// Package store keeps synced rows in DynamoDB and applies the sync rules of
// decision 51 on the table described in decision 52 (and 65):
//
//   - A pushed row replaces the stored one only if its updatedAt is newer.
//   - On equal updatedAt, a deletion wins; otherwise the stored row stays.
//   - A row dated too far in the future is refused, so a phone with a wrong
//     clock cannot win every conflict.
//   - Every accepted row gets the next number from the user's counter, and a
//     pull returns the rows after a given number.
package store

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

// MaxClockSkew is how far ahead of the server's clock a row's updatedAt may be.
const MaxClockSkew = 5 * time.Minute

// Attribute and index names on the table. Terraform (task 17) creates the same
// table for staging and prod; CreateTable creates it on the emulator.
const (
	attrPK        = "PK"
	attrSK        = "SK"
	attrUpdatedAt = "updatedAt"
	attrDeletedAt = "deletedAt"
	attrChange    = "changeNumber"
	attrSeq       = "seq"
	syncIndex     = "GSI1"
	metaSK        = "META"
)

var reserved = map[string]bool{attrPK: true, attrSK: true, attrUpdatedAt: true, attrDeletedAt: true, attrChange: true}

// Row is one synced row: a Room row on the phone, an item here.
type Row struct {
	// Kind is the sort key prefix, for example "SESSION" or "WEIGHIN".
	Kind string
	// ID is the row's UUID, or the key for preferences.
	ID string
	// UpdatedAt and DeletedAt are epoch milliseconds, written by the device.
	UpdatedAt int64
	DeletedAt *int64
	// Fields are the row's other columns. They must not use the names the
	// store keeps for itself (PK, SK, updatedAt, deletedAt, changeNumber).
	Fields map[string]types.AttributeValue
}

// Status is what happened to a pushed row. The values match the contract's
// PushResult.status.
type Status string

const (
	Accepted Status = "accepted"
	Stale    Status = "stale"
	Rejected Status = "rejected"
)

// Result is the outcome of one Put.
type Result struct {
	Status Status
	// ChangeNumber is set when the row was accepted.
	ChangeNumber int64
	// Reason is set when the row was rejected.
	Reason string
}

// Change is a stored row with the number it was accepted under.
type Change struct {
	Row
	ChangeNumber int64
}

// Store reads and writes one DynamoDB table.
type Store struct {
	db    *dynamodb.Client
	table string
	now   func() time.Time
}

// New returns a Store on table.
func New(db *dynamodb.Client, table string) *Store {
	return &Store{db: db, table: table, now: time.Now}
}

func userKey(userID string) string { return "USER#" + userID }

// Put stores row for userID if it wins against the stored copy.
//
// The counter is bumped before the conditional write, so a stale row uses up
// a number that no row keeps. Gaps are harmless: a pull asks for "after N".
func (s *Store) Put(ctx context.Context, userID string, row Row) (Result, error) {
	if err := validate(row); err != nil {
		return Result{}, err
	}
	if limit := s.now().Add(MaxClockSkew).UnixMilli(); row.UpdatedAt > limit {
		return Result{Status: Rejected, Reason: "updatedAt is in the future; check the device clock"}, nil
	}

	n, err := s.nextChangeNumber(ctx, userID)
	if err != nil {
		return Result{}, err
	}

	item := make(map[string]types.AttributeValue, len(row.Fields)+5)
	for k, v := range row.Fields {
		item[k] = v
	}
	item[attrPK] = &types.AttributeValueMemberS{Value: userKey(userID)}
	item[attrSK] = &types.AttributeValueMemberS{Value: row.Kind + "#" + row.ID}
	item[attrUpdatedAt] = number(row.UpdatedAt)
	item[attrChange] = number(n)
	if row.DeletedAt != nil {
		item[attrDeletedAt] = number(*row.DeletedAt)
	}

	// DynamoDB refuses names the expression does not use, so #d is added only
	// with the clause that needs it.
	cond := "attribute_not_exists(#pk) OR #u < :u"
	names := map[string]string{"#pk": attrPK, "#u": attrUpdatedAt}
	if row.DeletedAt != nil {
		cond += " OR (#u = :u AND attribute_not_exists(#d))"
		names["#d"] = attrDeletedAt
	}
	_, err = s.db.PutItem(ctx, &dynamodb.PutItemInput{
		TableName:                 &s.table,
		Item:                      item,
		ConditionExpression:       &cond,
		ExpressionAttributeNames:  names,
		ExpressionAttributeValues: map[string]types.AttributeValue{":u": number(row.UpdatedAt)},
	})
	var lost *types.ConditionalCheckFailedException
	switch {
	case errors.As(err, &lost):
		return Result{Status: Stale}, nil
	case err != nil:
		return Result{}, fmt.Errorf("put %s#%s: %w", row.Kind, row.ID, err)
	}
	return Result{Status: Accepted, ChangeNumber: n}, nil
}

func validate(row Row) error {
	if row.Kind == "" || row.ID == "" || strings.Contains(row.Kind, "#") {
		return fmt.Errorf("row needs a Kind without '#' and an ID, got %q %q", row.Kind, row.ID)
	}
	for k := range row.Fields {
		if reserved[k] {
			return fmt.Errorf("field %q is reserved by the store", k)
		}
	}
	return nil
}

func (s *Store) nextChangeNumber(ctx context.Context, userID string) (int64, error) {
	out, err := s.db.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: &s.table,
		Key: map[string]types.AttributeValue{
			attrPK: &types.AttributeValueMemberS{Value: userKey(userID)},
			attrSK: &types.AttributeValueMemberS{Value: metaSK},
		},
		UpdateExpression:          aws.String("ADD #seq :one"),
		ExpressionAttributeNames:  map[string]string{"#seq": attrSeq},
		ExpressionAttributeValues: map[string]types.AttributeValue{":one": number(1)},
		ReturnValues:              types.ReturnValueUpdatedNew,
	})
	if err != nil {
		return 0, fmt.Errorf("next change number: %w", err)
	}
	return readNumber(out.Attributes, attrSeq)
}

// Since returns up to limit of userID's rows accepted after change number
// after, oldest first. cursor is the last number returned (after itself when
// nothing is), and more says whether rows remain past it.
//
// The sync index is eventually consistent, so a row accepted a moment ago may
// not be returned yet; task 21 handles that together with decision 51's risk.
func (s *Store) Since(ctx context.Context, userID string, after int64, limit int32) (changes []Change, cursor int64, more bool, err error) {
	if limit < 1 {
		return nil, 0, false, fmt.Errorf("limit %d: want at least 1", limit)
	}
	// One extra row tells whether there is more without a second query.
	out, err := s.db.Query(ctx, &dynamodb.QueryInput{
		TableName:                 &s.table,
		IndexName:                 aws.String(syncIndex),
		KeyConditionExpression:    aws.String("#pk = :pk AND #c > :after"),
		ExpressionAttributeNames:  map[string]string{"#pk": attrPK, "#c": attrChange},
		ExpressionAttributeValues: map[string]types.AttributeValue{":pk": &types.AttributeValueMemberS{Value: userKey(userID)}, ":after": number(after)},
		Limit:                     aws.Int32(limit + 1),
	})
	if err != nil {
		return nil, 0, false, fmt.Errorf("query changes: %w", err)
	}

	items := out.Items
	if len(items) > int(limit) {
		items, more = items[:limit], true
	}
	cursor = after
	for _, item := range items {
		c, err := toChange(item)
		if err != nil {
			return nil, 0, false, err
		}
		changes = append(changes, c)
		cursor = c.ChangeNumber
	}
	return changes, cursor, more, nil
}

func toChange(item map[string]types.AttributeValue) (Change, error) {
	sk, _ := item[attrSK].(*types.AttributeValueMemberS)
	if sk == nil {
		return Change{}, errors.New("item without a sort key")
	}
	kind, id, ok := strings.Cut(sk.Value, "#")
	if !ok {
		return Change{}, fmt.Errorf("sort key %q is not KIND#id", sk.Value)
	}
	c := Change{Row: Row{Kind: kind, ID: id, Fields: map[string]types.AttributeValue{}}}
	var err error
	if c.UpdatedAt, err = readNumber(item, attrUpdatedAt); err != nil {
		return Change{}, err
	}
	if c.ChangeNumber, err = readNumber(item, attrChange); err != nil {
		return Change{}, err
	}
	if _, ok := item[attrDeletedAt]; ok {
		d, err := readNumber(item, attrDeletedAt)
		if err != nil {
			return Change{}, err
		}
		c.DeletedAt = &d
	}
	for k, v := range item {
		if !reserved[k] {
			c.Fields[k] = v
		}
	}
	return c, nil
}

func number(n int64) *types.AttributeValueMemberN {
	return &types.AttributeValueMemberN{Value: strconv.FormatInt(n, 10)}
}

func readNumber(item map[string]types.AttributeValue, name string) (int64, error) {
	v, ok := item[name].(*types.AttributeValueMemberN)
	if !ok {
		return 0, fmt.Errorf("attribute %q missing or not a number", name)
	}
	return strconv.ParseInt(v.Value, 10, 64)
}

// CreateTable creates the table with its sync index, on demand billing, and
// waits until it is usable. It is for the emulator and tests: staging and prod
// get the table from Terraform (task 17). An existing table is left as is.
// Time to Live on `ttl` (decision 79) is left to Terraform: nothing here
// depends on expired items being deleted.
func (s *Store) CreateTable(ctx context.Context) error {
	_, err := s.db.CreateTable(ctx, &dynamodb.CreateTableInput{
		TableName:   &s.table,
		BillingMode: types.BillingModePayPerRequest,
		AttributeDefinitions: []types.AttributeDefinition{
			{AttributeName: aws.String(attrPK), AttributeType: types.ScalarAttributeTypeS},
			{AttributeName: aws.String(attrSK), AttributeType: types.ScalarAttributeTypeS},
			{AttributeName: aws.String(attrChange), AttributeType: types.ScalarAttributeTypeN},
		},
		KeySchema: []types.KeySchemaElement{
			{AttributeName: aws.String(attrPK), KeyType: types.KeyTypeHash},
			{AttributeName: aws.String(attrSK), KeyType: types.KeyTypeRange},
		},
		// Only synced rows carry changeNumber, so the counter and future
		// non-synced items stay out of the index.
		GlobalSecondaryIndexes: []types.GlobalSecondaryIndex{{
			IndexName: aws.String(syncIndex),
			KeySchema: []types.KeySchemaElement{
				{AttributeName: aws.String(attrPK), KeyType: types.KeyTypeHash},
				{AttributeName: aws.String(attrChange), KeyType: types.KeyTypeRange},
			},
			Projection: &types.Projection{ProjectionType: types.ProjectionTypeAll},
		}},
	})
	// ResourceInUse: the table exists, or is still being created; wait either way.
	var inUse *types.ResourceInUseException
	if err != nil && !errors.As(err, &inUse) {
		return fmt.Errorf("create table %s: %w", s.table, err)
	}
	return dynamodb.NewTableExistsWaiter(s.db).Wait(ctx, &dynamodb.DescribeTableInput{TableName: &s.table}, time.Minute)
}
