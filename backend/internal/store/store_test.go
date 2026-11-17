package store

import (
	"context"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

// These checks run before any AWS call, so they need no emulator. The rules
// that need DynamoDB are in store_integration_test.go.

func TestPutRefusesBadRowsBeforeCallingAWS(t *testing.T) {
	s := &Store{now: time.Now} // no client: any AWS call would panic
	bad := []Row{
		{Kind: "", ID: "a", UpdatedAt: 1},
		{Kind: "WEIGHIN", ID: "", UpdatedAt: 1},
		{Kind: "WEIGH#IN", ID: "a", UpdatedAt: 1},
		{Kind: "WEIGHIN", ID: "a", UpdatedAt: 1, Fields: map[string]types.AttributeValue{"changeNumber": number(9)}},
	}
	for _, r := range bad {
		if _, err := s.Put(context.Background(), "u1", r); err == nil {
			t.Errorf("Put(%+v): want an error", r)
		}
	}
}

func TestPutRejectsAFutureClock(t *testing.T) {
	now := time.Date(2026, 11, 17, 12, 0, 0, 0, time.UTC)
	s := &Store{now: func() time.Time { return now }}
	r := Row{Kind: "WEIGHIN", ID: "a", UpdatedAt: now.Add(MaxClockSkew + time.Millisecond).UnixMilli()}
	res, err := s.Put(context.Background(), "u1", r)
	if err != nil || res.Status != Rejected {
		t.Fatalf("got %+v, %v; want rejected", res, err)
	}
}

func TestItemsReadBackAsRows(t *testing.T) {
	item := map[string]types.AttributeValue{
		"PK":           &types.AttributeValueMemberS{Value: "USER#u1"},
		"SK":           &types.AttributeValueMemberS{Value: "PREF#weight#unit"},
		"updatedAt":    number(10),
		"deletedAt":    number(11),
		"changeNumber": number(7),
		"value":        &types.AttributeValueMemberS{Value: "kg"},
	}
	c, err := toChange(item)
	if err != nil {
		t.Fatal(err)
	}
	if c.Kind != "PREF" || c.ID != "weight#unit" || c.UpdatedAt != 10 || *c.DeletedAt != 11 || c.ChangeNumber != 7 {
		t.Fatalf("got %+v", c)
	}
	if len(c.Fields) != 1 || c.Fields["value"] == nil {
		t.Fatalf("fields %v, want only value", c.Fields)
	}
}
