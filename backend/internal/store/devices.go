package store

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

// Phones that receive reminders, and the reminder settings (task 24,
// decisions 105–106):
//
//	USER#<userId> / DEVICE#<installationId>   token, updatedAt, ttl
//	USER#<userId> / PREF#reminder_<name>      value, ... (a synced preference row)
//
// Device items have no changeNumber, so they stay out of the sync index and
// never reach a pull. Each registration pushes its TTL DeviceTTL further, so
// a phone that stops opening the app drops out by itself.
const (
	attrToken   = "token"
	attrValue   = "value"
	devicePref  = "DEVICE#"
	reminderSK  = "PREF#reminder_"
	DeviceTTL   = 60 * 24 * time.Hour
	queryPageSz = 100
)

// Device is one registered phone.
type Device struct {
	InstallationID string
	Token          string
}

// PutDevice stores the FCM token of one installation of the app, replacing
// the one stored before.
func (s *Store) PutDevice(ctx context.Context, userID, installationID, token string, now time.Time) error {
	item := key(userKey(userID), devicePref+installationID)
	item[attrToken] = str(token)
	item[attrUpdatedAt] = number(now.UnixMilli())
	item[attrTTL] = number(now.Add(DeviceTTL).Unix())
	if _, err := s.db.PutItem(ctx, &dynamodb.PutItemInput{TableName: &s.table, Item: item}); err != nil {
		return fmt.Errorf("store device: %w", err)
	}
	return nil
}

// DeleteDevice removes one installation. Removing one that is not there is
// not an error.
func (s *Store) DeleteDevice(ctx context.Context, userID, installationID string) error {
	if _, err := s.db.DeleteItem(ctx, &dynamodb.DeleteItemInput{
		TableName: &s.table,
		Key:       key(userKey(userID), devicePref+installationID),
	}); err != nil {
		return fmt.Errorf("delete device: %w", err)
	}
	return nil
}

// Devices returns the user's registered phones. Items past their TTL that
// DynamoDB has not deleted yet are left out.
func (s *Store) Devices(ctx context.Context, userID string, now time.Time) ([]Device, error) {
	items, err := s.queryPrefix(ctx, userID, devicePref)
	if err != nil {
		return nil, fmt.Errorf("read devices: %w", err)
	}
	out := make([]Device, 0, len(items))
	for _, it := range items {
		if ttl, err := readNumber(it, attrTTL); err == nil && ttl < now.Unix() {
			continue
		}
		sk, _ := it[attrSK].(*types.AttributeValueMemberS)
		tok, _ := it[attrToken].(*types.AttributeValueMemberS)
		if sk == nil || tok == nil {
			continue
		}
		out = append(out, Device{InstallationID: strings.TrimPrefix(sk.Value, devicePref), Token: tok.Value})
	}
	return out, nil
}

// ReminderSettings returns the user's synced preferences whose key starts
// with "reminder_", as key → value. Deleted rows are left out. The read is
// consistent, so a reconcile right after a push sees the row it just wrote.
func (s *Store) ReminderSettings(ctx context.Context, userID string) (map[string]string, error) {
	items, err := s.queryPrefix(ctx, userID, reminderSK)
	if err != nil {
		return nil, fmt.Errorf("read reminder settings: %w", err)
	}
	out := make(map[string]string, len(items))
	for _, it := range items {
		if _, deleted := it[attrDeletedAt].(*types.AttributeValueMemberN); deleted {
			continue
		}
		sk, _ := it[attrSK].(*types.AttributeValueMemberS)
		v, _ := it[attrValue].(*types.AttributeValueMemberS)
		if sk == nil || v == nil {
			continue
		}
		out[strings.TrimPrefix(sk.Value, "PREF#")] = v.Value
	}
	return out, nil
}

// queryPrefix returns every item of the user whose sort key starts with prefix.
func (s *Store) queryPrefix(ctx context.Context, userID, prefix string) ([]map[string]types.AttributeValue, error) {
	in := &dynamodb.QueryInput{
		TableName:              &s.table,
		KeyConditionExpression: aws.String("#pk = :pk AND begins_with(#sk, :p)"),
		ExpressionAttributeNames: map[string]string{
			"#pk": attrPK, "#sk": attrSK,
		},
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":pk": str(userKey(userID)),
			":p":  str(prefix),
		},
		ConsistentRead: aws.Bool(true),
		Limit:          aws.Int32(queryPageSz),
	}
	var out []map[string]types.AttributeValue
	for {
		page, err := s.db.Query(ctx, in)
		if err != nil {
			return nil, err
		}
		out = append(out, page.Items...)
		if len(page.LastEvaluatedKey) == 0 {
			return out, nil
		}
		in.ExclusiveStartKey = page.LastEvaluatedKey
	}
}
