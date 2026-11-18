package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

// Shared routines and programs on the same table (decisions 52 and 97):
//
//	SHARE#<id>    / SHARE                     ownerId, createdAt, snapshot (JSON string)
//	USER#<userId> / SHARECOUNT#<yyyy-mm-dd>   n, ttl                       (per-day cap)
//
// Neither has changeNumber, so both stay out of the sync index and never
// reach a pull. The snapshot is the contract's ShareSnapshot as JSON: it is
// written once, read whole and never queried by field, so one string is
// simpler than an attribute per field (unlike synced rows, decision 87).
const (
	attrOwnerID   = "ownerId"
	attrCreatedAt = "createdAt"
	attrSnapshot  = "snapshot"
	attrCount     = "n"
	shareSK       = "SHARE"
)

// ErrShareNotFound is returned by GetShare for an id with no share.
var ErrShareNotFound = errors.New("share not found")

// ErrShareLimit is returned by PutShare when the owner has published the
// day's maximum already.
var ErrShareLimit = errors.New("daily share limit reached")

// Share is one published snapshot.
type Share struct {
	ID      string
	OwnerID string
	// CreatedAt is epoch milliseconds, the server's clock.
	CreatedAt int64
	// Snapshot is the contract's ShareSnapshot, as JSON.
	Snapshot []byte
}

func shareKey(id string) map[string]types.AttributeValue { return key("SHARE#"+id, shareSK) }

// PutShare stores sh after counting it against its owner's shares for the
// UTC day of sh.CreatedAt, at most perDay. The counter is taken first; if
// the put then fails, the day's count is one too high, which only costs the
// owner one share that day. The counter item deletes itself through TTL two
// days later.
func (s *Store) PutShare(ctx context.Context, sh Share, perDay int) error {
	created := time.UnixMilli(sh.CreatedAt).UTC()
	_, err := s.db.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName:                &s.table,
		Key:                      key(userKey(sh.OwnerID), "SHARECOUNT#"+created.Format(time.DateOnly)),
		UpdateExpression:         aws.String("ADD #n :one SET #ttl = :ttl"),
		ConditionExpression:      aws.String("attribute_not_exists(#n) OR #n < :max"),
		ExpressionAttributeNames: map[string]string{"#n": attrCount, "#ttl": attrTTL},
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":one": number(1),
			":max": number(int64(perDay)),
			":ttl": number(created.Add(48 * time.Hour).Unix()),
		},
	})
	var failed *types.ConditionalCheckFailedException
	switch {
	case errors.As(err, &failed):
		return ErrShareLimit
	case err != nil:
		return fmt.Errorf("count share: %w", err)
	}

	item := shareKey(sh.ID)
	item[attrOwnerID] = str(sh.OwnerID)
	item[attrCreatedAt] = number(sh.CreatedAt)
	item[attrSnapshot] = str(string(sh.Snapshot))
	// 128 random bits never collide in practice; the condition makes sure a
	// share can never be overwritten even if one did.
	_, err = s.db.PutItem(ctx, &dynamodb.PutItemInput{
		TableName:                &s.table,
		Item:                     item,
		ConditionExpression:      aws.String("attribute_not_exists(#pk)"),
		ExpressionAttributeNames: map[string]string{"#pk": attrPK},
	})
	if err != nil {
		return fmt.Errorf("store share: %w", err)
	}
	return nil
}

// GetShare returns the share with id, or ErrShareNotFound.
func (s *Store) GetShare(ctx context.Context, id string) (Share, error) {
	// Consistent: a link opened right after it was published must not get a
	// 404, which CloudFront would then keep for a few seconds.
	out, err := s.db.GetItem(ctx, &dynamodb.GetItemInput{TableName: &s.table, Key: shareKey(id), ConsistentRead: aws.Bool(true)})
	if err != nil {
		return Share{}, fmt.Errorf("read share: %w", err)
	}
	if len(out.Item) == 0 {
		return Share{}, ErrShareNotFound
	}
	owner, _ := out.Item[attrOwnerID].(*types.AttributeValueMemberS)
	snap, _ := out.Item[attrSnapshot].(*types.AttributeValueMemberS)
	if owner == nil || snap == nil {
		return Share{}, errors.New("share item without ownerId or snapshot")
	}
	created, err := readNumber(out.Item, attrCreatedAt)
	if err != nil {
		return Share{}, err
	}
	return Share{ID: id, OwnerID: owner.Value, CreatedAt: created, Snapshot: []byte(snap.Value)}, nil
}
