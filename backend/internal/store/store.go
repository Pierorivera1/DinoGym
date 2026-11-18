// Package store keeps synced rows in DynamoDB and applies the sync rules of
// decision 51 on the table described in decision 52 (and 65):
//
//   - A pushed row replaces the stored one only if its updatedAt is newer.
//   - On equal updatedAt, a deletion wins; otherwise the stored row stays.
//   - A row dated too far in the future is refused, so a phone with a wrong
//     clock cannot win every conflict.
//   - Every accepted row gets the next number from the user's counter, and a
//     pull returns the rows after a given number.
//   - A pull's cursor never passes a row accepted less than SettleWindow ago,
//     so a row numbered earlier but written later is not skipped (decision 86).
package store

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

// MaxClockSkew is how far ahead of the server's clock a row's updatedAt may be.
const MaxClockSkew = 5 * time.Minute

// ReasonUpdatedAtInFuture is the reason given for a row refused because of
// MaxClockSkew. It is the contract's PushResult.reason.
const ReasonUpdatedAtInFuture = "updated_at_in_future"

// MaxWriteTime bounds how long a push may keep writing after it took its
// change numbers. Writes still running then are cancelled, so a number shows
// up in the sync index soon after it was taken, or never.
const MaxWriteTime = 10 * time.Second

// SettleWindow is how old a row's acceptedAt must be before a pull's cursor
// may move past it (decision 86). It covers MaxWriteTime plus the sync index
// catching up with the table, with room to spare.
const SettleWindow = 30 * time.Second

// writeWorkers is how many rows of one push are written at once. Writing 500
// rows one by one would take several seconds of the Lambda's 10; many more
// workers would push one user's partition towards DynamoDB's limit of 1,000
// writes a second.
const writeWorkers = 4

// Attribute and index names on the table. Terraform (task 17) creates the same
// table for staging and prod; CreateTable creates it on the emulator.
const (
	attrPK        = "PK"
	attrSK        = "SK"
	attrUpdatedAt = "updatedAt"
	attrDeletedAt = "deletedAt"
	attrChange    = "changeNumber"
	attrAccepted  = "acceptedAt"
	attrSeq       = "seq"
	syncIndex     = "GSI1"
	metaSK        = "META"
)

var reserved = map[string]bool{attrPK: true, attrSK: true, attrUpdatedAt: true, attrDeletedAt: true, attrChange: true, attrAccepted: true}

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
	// store keeps for itself (PK, SK, updatedAt, deletedAt, changeNumber,
	// acceptedAt).
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
	// AcceptedAt is the server's clock, epoch milliseconds, when the row's
	// change number was taken. 0 for rows stored before it existed.
	AcceptedAt int64
}

// Store reads and writes one DynamoDB table.
type Store struct {
	db     *dynamodb.Client
	table  string
	now    func() time.Time
	settle time.Duration
}

// New returns a Store on table.
func New(db *dynamodb.Client, table string) *Store {
	return &Store{db: db, table: table, now: time.Now, settle: SettleWindow}
}

// SetSettleWindow replaces SettleWindow. Tests outside this package set 0, so
// a pull's cursor covers rows they have just written.
func (s *Store) SetSettleWindow(d time.Duration) { s.settle = d }

func userKey(userID string) string { return "USER#" + userID }

// Put stores row for userID if it wins against the stored copy. It is
// PutRows for one row.
func (s *Store) Put(ctx context.Context, userID string, row Row) (Result, error) {
	res, err := s.PutRows(ctx, userID, []Row{row})
	if err != nil {
		return Result{}, err
	}
	return res[0], nil
}

// PutRows stores each row that wins against the stored copy for userID, and
// returns one result per row, in order.
//
// It takes a block of change numbers with one counter update, in the order of
// rows, and then makes the conditional writes. A stale row uses up a number
// that no row keeps; gaps are harmless, since a pull asks for "after N". The
// writes run a few at a time and must finish within MaxWriteTime. On an error
// the rows already written stay written, and retrying the whole push is safe:
// they come back stale.
func (s *Store) PutRows(ctx context.Context, userID string, rows []Row) ([]Result, error) {
	for _, row := range rows {
		if err := validate(row); err != nil {
			return nil, err
		}
	}
	results := make([]Result, len(rows))
	limit := s.now().Add(MaxClockSkew).UnixMilli()
	var todo []int
	for i, row := range rows {
		if row.UpdatedAt > limit {
			results[i] = Result{Status: Rejected, Reason: ReasonUpdatedAtInFuture}
			continue
		}
		todo = append(todo, i)
	}
	if len(todo) == 0 {
		return results, nil
	}

	ctx, cancel := context.WithTimeout(ctx, MaxWriteTime)
	defer cancel()
	last, err := s.takeChangeNumbers(ctx, userID, int64(len(todo)))
	if err != nil {
		return nil, err
	}
	// Read after the counter update: acceptedAt is never earlier than the
	// moment the numbers were taken, which the settle rule relies on.
	acceptedAt := s.now().UnixMilli()
	first := last - int64(len(todo)) + 1

	var (
		wg       sync.WaitGroup
		mu       sync.Mutex
		firstErr error
		slots    = make(chan struct{}, writeWorkers)
	)
	for j, i := range todo {
		slots <- struct{}{}
		if ctx.Err() != nil {
			<-slots
			break
		}
		wg.Add(1)
		go func() {
			defer func() { <-slots; wg.Done() }()
			res, err := s.write(ctx, userID, rows[i], first+int64(j), acceptedAt)
			if err != nil {
				mu.Lock()
				if firstErr == nil {
					firstErr = err
				}
				mu.Unlock()
				cancel()
				return
			}
			results[i] = res
		}()
	}
	wg.Wait()
	if firstErr == nil && ctx.Err() != nil {
		firstErr = fmt.Errorf("push: %w", ctx.Err())
	}
	if firstErr != nil {
		return nil, firstErr
	}
	return results, nil
}

// write is the conditional write of one row under change number n.
func (s *Store) write(ctx context.Context, userID string, row Row, n, acceptedAt int64) (Result, error) {
	item := make(map[string]types.AttributeValue, len(row.Fields)+6)
	for k, v := range row.Fields {
		item[k] = v
	}
	item[attrPK] = &types.AttributeValueMemberS{Value: userKey(userID)}
	item[attrSK] = &types.AttributeValueMemberS{Value: row.Kind + "#" + row.ID}
	item[attrUpdatedAt] = number(row.UpdatedAt)
	item[attrChange] = number(n)
	item[attrAccepted] = number(acceptedAt)
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
	_, err := s.db.PutItem(ctx, &dynamodb.PutItemInput{
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

// takeChangeNumbers adds n to the user's counter and returns its new value,
// the last of the n numbers taken.
func (s *Store) takeChangeNumbers(ctx context.Context, userID string, n int64) (int64, error) {
	out, err := s.db.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: &s.table,
		Key: map[string]types.AttributeValue{
			attrPK: &types.AttributeValueMemberS{Value: userKey(userID)},
			attrSK: &types.AttributeValueMemberS{Value: metaSK},
		},
		UpdateExpression:          aws.String("ADD #seq :n"),
		ExpressionAttributeNames:  map[string]string{"#seq": attrSeq},
		ExpressionAttributeValues: map[string]types.AttributeValue{":n": number(n)},
		ReturnValues:              types.ReturnValueUpdatedNew,
	})
	if err != nil {
		return 0, fmt.Errorf("take change numbers: %w", err)
	}
	return readNumber(out.Attributes, attrSeq)
}

// Since returns up to limit of userID's rows accepted after change number
// after, oldest first. cursor is where the next pull starts, and more says
// whether rows remain past this page.
//
// Two pushes can take numbers 5 and 6 and commit 6 first, and the sync index
// lags the table, so row 5 may still be missing when row 6 is returned. The
// cursor therefore stops before the first row accepted less than the settle
// window ago (settledCursor, decision 86). Every row found is still returned,
// but the recent ones come again on the next pull, by when any row numbered
// before them is visible.
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
	for _, item := range items {
		c, err := toChange(item)
		if err != nil {
			return nil, 0, false, err
		}
		changes = append(changes, c)
	}
	cursor, more = settledCursor(changes, after, more, s.now().Add(-s.settle).UnixMilli())
	return changes, cursor, more, nil
}

// settledCursor returns the cursor and hasMore for a page of changes pulled
// after after. The cursor moves past each row accepted at or before settledBy
// and stops at the first row accepted later. Numbers are taken in time order,
// so every row after a recent one is recent too: when the cursor stops early,
// more is false, and the device gets the rest on its next sync instead of
// pulling the same page in a loop.
func settledCursor(changes []Change, after int64, more bool, settledBy int64) (int64, bool) {
	cursor := after
	for _, c := range changes {
		if c.AcceptedAt > settledBy {
			return cursor, false
		}
		cursor = c.ChangeNumber
	}
	return cursor, more
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
	if _, ok := item[attrAccepted]; ok {
		if c.AcceptedAt, err = readNumber(item, attrAccepted); err != nil {
			return Change{}, err
		}
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
