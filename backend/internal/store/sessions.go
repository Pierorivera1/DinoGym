package store

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"

	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
)

// Accounts and sessions on the same table (decisions 52, 79 and 80):
//
//	GOOGLE#<sub>      / USER   userId                      (no email, no name)
//	REFRESH#<sha256>  / TOKEN  userId, familyId, expiresAt, ttl, usedAt
//	FAMILY#<familyId> / META   ttl, revoked
//
// None of them has changeNumber, so they stay out of the sync index. ttl is
// epoch seconds for DynamoDB's Time to Live; expiresAt is epoch milliseconds.
const (
	attrUserID    = "userId"
	attrFamilyID  = "familyId"
	attrExpiresAt = "expiresAt"
	attrTTL       = "ttl"
	attrUsedAt    = "usedAt"
	attrRevoked   = "revoked"
)

var _ auth.Store = (*Store)(nil)

func key(pk, sk string) map[string]types.AttributeValue {
	return map[string]types.AttributeValue{
		attrPK: &types.AttributeValueMemberS{Value: pk},
		attrSK: &types.AttributeValueMemberS{Value: sk},
	}
}

func str(s string) *types.AttributeValueMemberS { return &types.AttributeValueMemberS{Value: s} }

// GoogleUser returns the user id for a Google sub, creating the account with
// newUserID when there is none. The create is conditional, so when two first
// sign-ins race, the loser reads the winner's id instead of overwriting it.
func (s *Store) GoogleUser(ctx context.Context, sub, newUserID string) (string, error) {
	item := key("GOOGLE#"+sub, "USER")
	item[attrUserID] = str(newUserID)
	_, err := s.db.PutItem(ctx, &dynamodb.PutItemInput{
		TableName:                &s.table,
		Item:                     item,
		ConditionExpression:      aws.String("attribute_not_exists(#pk)"),
		ExpressionAttributeNames: map[string]string{"#pk": attrPK},
	})
	var exists *types.ConditionalCheckFailedException
	switch {
	case err == nil:
		return newUserID, nil
	case !errors.As(err, &exists):
		return "", fmt.Errorf("create account: %w", err)
	}
	out, err := s.db.GetItem(ctx, &dynamodb.GetItemInput{TableName: &s.table, Key: key("GOOGLE#"+sub, "USER"), ConsistentRead: aws.Bool(true)})
	if err != nil {
		return "", fmt.Errorf("read account: %w", err)
	}
	id, ok := out.Item[attrUserID].(*types.AttributeValueMemberS)
	if !ok || id.Value == "" {
		return "", errors.New("account item without userId")
	}
	return id.Value, nil
}

func familyKey(id string) map[string]types.AttributeValue { return key("FAMILY#"+id, "META") }

// PutFamily creates a session family that the TTL removes after expiresAt.
func (s *Store) PutFamily(ctx context.Context, familyID string, expiresAt time.Time) error {
	item := familyKey(familyID)
	item[attrTTL] = number(expiresAt.Unix())
	_, err := s.db.PutItem(ctx, &dynamodb.PutItemInput{
		TableName:                &s.table,
		Item:                     item,
		ConditionExpression:      aws.String("attribute_not_exists(#pk)"),
		ExpressionAttributeNames: map[string]string{"#pk": attrPK},
	})
	if err != nil {
		return fmt.Errorf("create session family: %w", err)
	}
	return nil
}

// RenewFamily moves a family's TTL to expiresAt. It fails with
// auth.ErrRevoked when the family is revoked or gone, in the same call.
func (s *Store) RenewFamily(ctx context.Context, familyID string, expiresAt time.Time) error {
	_, err := s.db.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName:                 &s.table,
		Key:                       familyKey(familyID),
		UpdateExpression:          aws.String("SET #ttl = :ttl"),
		ConditionExpression:       aws.String("attribute_exists(#pk) AND attribute_not_exists(#revoked)"),
		ExpressionAttributeNames:  map[string]string{"#pk": attrPK, "#ttl": attrTTL, "#revoked": attrRevoked},
		ExpressionAttributeValues: map[string]types.AttributeValue{":ttl": number(expiresAt.Unix())},
	})
	var failed *types.ConditionalCheckFailedException
	switch {
	case errors.As(err, &failed):
		return auth.ErrRevoked
	case err != nil:
		return fmt.Errorf("renew session family: %w", err)
	}
	return nil
}

// RevokeFamily marks a family revoked. Its item keeps its TTL, so it is
// removed with its last token.
func (s *Store) RevokeFamily(ctx context.Context, familyID string) error {
	_, err := s.db.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName:                 &s.table,
		Key:                       familyKey(familyID),
		UpdateExpression:          aws.String("SET #revoked = :true"),
		ExpressionAttributeNames:  map[string]string{"#revoked": attrRevoked},
		ExpressionAttributeValues: map[string]types.AttributeValue{":true": &types.AttributeValueMemberBOOL{Value: true}},
	})
	if err != nil {
		return fmt.Errorf("revoke session family: %w", err)
	}
	return nil
}

func refreshKey(hash string) map[string]types.AttributeValue { return key("REFRESH#"+hash, "TOKEN") }

// PutRefreshToken stores a new refresh token by its hash.
func (s *Store) PutRefreshToken(ctx context.Context, r auth.RefreshRecord) error {
	item := refreshKey(r.Hash)
	item[attrUserID] = str(r.UserID)
	item[attrFamilyID] = str(r.FamilyID)
	item[attrExpiresAt] = number(r.ExpiresAt.UnixMilli())
	item[attrTTL] = number(r.ExpiresAt.Unix())
	_, err := s.db.PutItem(ctx, &dynamodb.PutItemInput{
		TableName:                &s.table,
		Item:                     item,
		ConditionExpression:      aws.String("attribute_not_exists(#pk)"),
		ExpressionAttributeNames: map[string]string{"#pk": attrPK},
	})
	if err != nil {
		return fmt.Errorf("store refresh token: %w", err)
	}
	return nil
}

// UseRefreshToken sets usedAt on the token only if it exists and has no
// usedAt yet, so of two requests with the same token exactly one gets it.
// When the condition fails, DynamoDB returns the old item, which tells an
// unknown token (auth.ErrNotFound) from a used one (auth.ErrAlreadyUsed, with
// its record so the caller can revoke the family).
func (s *Store) UseRefreshToken(ctx context.Context, hash string, at time.Time) (auth.RefreshRecord, error) {
	out, err := s.db.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName:                           &s.table,
		Key:                                 refreshKey(hash),
		UpdateExpression:                    aws.String("SET #used = :at"),
		ConditionExpression:                 aws.String("attribute_exists(#pk) AND attribute_not_exists(#used)"),
		ExpressionAttributeNames:            map[string]string{"#pk": attrPK, "#used": attrUsedAt},
		ExpressionAttributeValues:           map[string]types.AttributeValue{":at": number(at.UnixMilli())},
		ReturnValues:                        types.ReturnValueAllNew,
		ReturnValuesOnConditionCheckFailure: types.ReturnValuesOnConditionCheckFailureAllOld,
	})
	var failed *types.ConditionalCheckFailedException
	switch {
	case errors.As(err, &failed):
		if len(failed.Item) == 0 {
			return auth.RefreshRecord{}, auth.ErrNotFound
		}
		rec, err := toRefreshRecord(hash, failed.Item)
		if err != nil {
			return auth.RefreshRecord{}, err
		}
		return rec, auth.ErrAlreadyUsed
	case err != nil:
		return auth.RefreshRecord{}, fmt.Errorf("use refresh token: %w", err)
	}
	return toRefreshRecord(hash, out.Attributes)
}

func toRefreshRecord(hash string, item map[string]types.AttributeValue) (auth.RefreshRecord, error) {
	user, _ := item[attrUserID].(*types.AttributeValueMemberS)
	family, _ := item[attrFamilyID].(*types.AttributeValueMemberS)
	if user == nil || family == nil {
		return auth.RefreshRecord{}, errors.New("refresh token item without userId or familyId")
	}
	exp, err := readNumber(item, attrExpiresAt)
	if err != nil {
		return auth.RefreshRecord{}, err
	}
	return auth.RefreshRecord{Hash: hash, UserID: user.Value, FamilyID: family.Value, ExpiresAt: time.UnixMilli(exp)}, nil
}
