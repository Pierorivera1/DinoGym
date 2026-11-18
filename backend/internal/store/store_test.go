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

func TestPutRowsRejectsFutureRowsWithoutTakingNumbers(t *testing.T) {
	now := time.Date(2026, 11, 17, 12, 0, 0, 0, time.UTC)
	s := &Store{now: func() time.Time { return now }} // no client: a counter update would panic
	future := now.Add(MaxClockSkew + time.Second).UnixMilli()
	res, err := s.PutRows(context.Background(), "u1", []Row{
		{Kind: "WEIGHIN", ID: "a", UpdatedAt: future},
		{Kind: "SET", ID: "b", UpdatedAt: future},
	})
	if err != nil || len(res) != 2 {
		t.Fatalf("got %+v, %v", res, err)
	}
	for _, r := range res {
		if r.Status != Rejected || r.Reason != ReasonUpdatedAtInFuture {
			t.Fatalf("got %+v, want rejected %s", r, ReasonUpdatedAtInFuture)
		}
	}
}

func TestSettledCursorStopsBeforeRecentRows(t *testing.T) {
	page := func(accepted ...int64) []Change {
		var out []Change
		for i, a := range accepted {
			out = append(out, Change{ChangeNumber: int64(10 + i), AcceptedAt: a})
		}
		return out
	}
	cases := []struct {
		name       string
		changes    []Change
		more       bool
		wantCursor int64
		wantMore   bool
	}{
		{"an empty page keeps the cursor", nil, false, 5, false},
		{"all settled: the last row", page(100, 100, 200), true, 12, true},
		{"stops at the first recent row", page(100, 300, 200), true, 10, false},
		{"first row recent: no move and no more, so no loop", page(300, 100), true, 5, false},
		{"rows stored before acceptedAt existed count as settled", page(0, 0), false, 11, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			cursor, more := settledCursor(c.changes, 5, c.more, 200)
			if cursor != c.wantCursor || more != c.wantMore {
				t.Fatalf("got %d %v, want %d %v", cursor, more, c.wantCursor, c.wantMore)
			}
		})
	}
}
