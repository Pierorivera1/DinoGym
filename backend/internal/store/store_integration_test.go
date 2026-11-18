//go:build integration

package store

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"

	"github.com/Pierorivera1/DinoGym/backend/internal/auth"
	"github.com/Pierorivera1/DinoGym/backend/internal/awstest"
)

// newStore creates a fresh table on the emulator and deletes it after the test.
func newStore(t *testing.T) *Store {
	t.Helper()
	db := dynamodb.NewFromConfig(awstest.Config(t))
	s := New(db, awstest.Name("dinogym-test"))
	ctx := context.Background()
	if err := s.CreateTable(ctx); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if _, err := db.DeleteTable(context.Background(), &dynamodb.DeleteTableInput{TableName: &s.table}); err != nil {
			t.Errorf("delete table: %v", err)
		}
	})
	return s
}

func weighIn(id string, updatedAt int64, kg string) Row {
	return Row{Kind: "WEIGHIN", ID: id, UpdatedAt: updatedAt, Fields: map[string]types.AttributeValue{
		"kg": &types.AttributeValueMemberN{Value: kg},
	}}
}

func deleted(r Row, at int64) Row {
	r.DeletedAt = &at
	return r
}

func put(t *testing.T, s *Store, user string, r Row) Result {
	t.Helper()
	res, err := s.Put(context.Background(), user, r)
	if err != nil {
		t.Fatal(err)
	}
	return res
}

func TestSyncRules(t *testing.T) {
	s := newStore(t)
	steps := []struct {
		name string
		row  Row
		want Status
	}{
		{"a new row is kept", weighIn("a1", 1000, "72.4"), Accepted},
		{"an older edit loses", weighIn("a1", 900, "99.9"), Stale},
		{"a newer edit wins", weighIn("a1", 2000, "72.1"), Accepted},
		{"the same row again changes nothing", weighIn("a1", 2000, "72.1"), Stale},
		{"an edit at the same time loses", weighIn("a1", 2000, "50.0"), Stale},
		{"a deletion at the same time wins", deleted(weighIn("a1", 2000, "72.1"), 2000), Accepted},
		{"a second deletion at the same time changes nothing", deleted(weighIn("a1", 2000, "72.1"), 2000), Stale},
		{"an edit after the deletion wins", weighIn("a1", 3000, "73.5"), Accepted},
	}
	var last int64
	for _, st := range steps {
		res := put(t, s, "u1", st.row)
		if res.Status != st.want {
			t.Fatalf("%s: got %s, want %s", st.name, res.Status, st.want)
		}
		if res.Status == Accepted {
			if res.ChangeNumber <= last {
				t.Fatalf("%s: change number %d after %d", st.name, res.ChangeNumber, last)
			}
			last = res.ChangeNumber
		}
	}

	changes, cursor, more, err := s.Since(context.Background(), "u1", 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(changes) != 1 || more || cursor != last {
		t.Fatalf("got %d changes, cursor %d, more %v; want 1, %d, false", len(changes), cursor, more, last)
	}
	got := changes[0]
	if got.ID != "a1" || got.UpdatedAt != 3000 || got.DeletedAt != nil {
		t.Fatalf("stored row %+v, want the edit after the deletion", got.Row)
	}
	if kg := got.Fields["kg"].(*types.AttributeValueMemberN).Value; kg != "73.5" {
		t.Fatalf("kg = %s, want 73.5", kg)
	}
}

func TestDeletionIsStoredAndPulled(t *testing.T) {
	s := newStore(t)
	put(t, s, "u1", weighIn("a1", 1000, "70"))
	put(t, s, "u1", deleted(weighIn("a1", 1500, "70"), 1500))

	changes, _, _, err := s.Since(context.Background(), "u1", 0, 10)
	if err != nil {
		t.Fatal(err)
	}
	if len(changes) != 1 || changes[0].DeletedAt == nil || *changes[0].DeletedAt != 1500 {
		t.Fatalf("got %+v, want the row with deletedAt 1500", changes)
	}
}

func TestFutureClockIsRejectedWithoutWriting(t *testing.T) {
	s := newStore(t)
	future := time.Now().Add(MaxClockSkew + time.Minute).UnixMilli()
	if res := put(t, s, "u1", weighIn("a1", future, "70")); res.Status != Rejected || res.Reason == "" {
		t.Fatalf("got %+v, want rejected with a reason", res)
	}
	if changes, _, _, _ := s.Since(context.Background(), "u1", 0, 10); len(changes) != 0 {
		t.Fatalf("a rejected row was stored: %+v", changes)
	}
}

func TestPullPagesInChangeOrderAndKeepsUsersApart(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	ids := []string{"r1", "r2", "r3", "r4", "r5"}
	for i, id := range ids {
		put(t, s, "u1", weighIn(id, int64(1000+i), "70"))
	}
	put(t, s, "u2", weighIn("other", 1000, "80"))

	var got []string
	var cursor int64
	for pages := 0; ; pages++ {
		if pages > len(ids) {
			t.Fatal("pull never ended")
		}
		changes, next, more, err := s.Since(ctx, "u1", cursor, 2)
		if err != nil {
			t.Fatal(err)
		}
		for _, c := range changes {
			got = append(got, c.ID)
		}
		cursor = next
		if !more {
			break
		}
	}
	if len(got) != len(ids) {
		t.Fatalf("pulled %v, want %v", got, ids)
	}
	for i := range ids {
		if got[i] != ids[i] {
			t.Fatalf("pulled %v, want %v in change order", got, ids)
		}
	}

	// Nothing after the last cursor, and the cursor stays put.
	changes, next, more, err := s.Since(ctx, "u1", cursor, 2)
	if err != nil || len(changes) != 0 || more || next != cursor {
		t.Fatalf("after the end: %d changes, cursor %d, more %v, err %v", len(changes), next, more, err)
	}
}

// Accounts and sessions (decisions 79 and 80): the conditional writes that the
// auth package's unit tests only fake.

func TestGoogleAccountIsCreatedOnceUnderARace(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	ids := make([]string, 8)
	var wg sync.WaitGroup
	for i := range ids {
		wg.Add(1)
		go func() {
			defer wg.Done()
			id, err := s.GoogleUser(ctx, "sub-1", fmt.Sprintf("candidate-%d", i))
			if err != nil {
				t.Error(err)
			}
			ids[i] = id
		}()
	}
	wg.Wait()
	for _, id := range ids {
		if id == "" || id != ids[0] {
			t.Fatalf("concurrent sign-ins got %v, want one user id", ids)
		}
	}
	if id, err := s.GoogleUser(ctx, "sub-2", "u2"); err != nil || id != "u2" {
		t.Fatalf("another Google account: %q, %v", id, err)
	}
}

func TestRefreshTokenWorksOnce(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	exp := time.UnixMilli(time.Now().Add(auth.RefreshTokenTTL).UnixMilli())
	rec := auth.RefreshRecord{Hash: "h1", UserID: "u1", FamilyID: "f1", ExpiresAt: exp}
	if err := s.PutRefreshToken(ctx, rec); err != nil {
		t.Fatal(err)
	}

	if _, err := s.UseRefreshToken(ctx, "unknown", time.Now()); !errors.Is(err, auth.ErrNotFound) {
		t.Fatalf("unknown token: %v", err)
	}
	got, err := s.UseRefreshToken(ctx, "h1", time.Now())
	if err != nil || got.UserID != "u1" || got.FamilyID != "f1" || !got.ExpiresAt.Equal(exp) {
		t.Fatalf("first use: %+v, %v; want %+v", got, err, rec)
	}
	got, err = s.UseRefreshToken(ctx, "h1", time.Now())
	if !errors.Is(err, auth.ErrAlreadyUsed) || got.FamilyID != "f1" {
		t.Fatalf("second use: %+v, %v; want ErrAlreadyUsed with the family", got, err)
	}

	item, err := s.db.GetItem(ctx, &dynamodb.GetItemInput{TableName: &s.table, Key: refreshKey("h1")})
	if err != nil {
		t.Fatal(err)
	}
	ttl, err := readNumber(item.Item, attrTTL)
	if err != nil || ttl != exp.Unix() {
		t.Fatalf("ttl %d, %v; want %d (epoch seconds)", ttl, err, exp.Unix())
	}
}

func TestOnlyOneOfConcurrentRefreshesWins(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	if err := s.PutRefreshToken(ctx, auth.RefreshRecord{Hash: "h1", UserID: "u1", FamilyID: "f1", ExpiresAt: time.Now().Add(time.Hour)}); err != nil {
		t.Fatal(err)
	}
	errs := make([]error, 6)
	var wg sync.WaitGroup
	for i := range errs {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, errs[i] = s.UseRefreshToken(ctx, "h1", time.Now())
		}()
	}
	wg.Wait()
	won := 0
	for _, err := range errs {
		switch {
		case err == nil:
			won++
		case !errors.Is(err, auth.ErrAlreadyUsed):
			t.Fatal(err)
		}
	}
	if won != 1 {
		t.Fatalf("%d uses succeeded, want exactly 1", won)
	}
}

func TestFamilyRevocation(t *testing.T) {
	s := newStore(t)
	ctx := context.Background()
	later := time.Now().Add(time.Hour)

	if err := s.RenewFamily(ctx, "missing", later); !errors.Is(err, auth.ErrRevoked) {
		t.Fatalf("a missing family renewed: %v", err)
	}
	if err := s.PutFamily(ctx, "f1", later); err != nil {
		t.Fatal(err)
	}
	if err := s.RenewFamily(ctx, "f1", later.Add(time.Hour)); err != nil {
		t.Fatalf("renew: %v", err)
	}
	if err := s.RevokeFamily(ctx, "f1"); err != nil {
		t.Fatal(err)
	}
	if err := s.RenewFamily(ctx, "f1", later.Add(2*time.Hour)); !errors.Is(err, auth.ErrRevoked) {
		t.Fatalf("a revoked family renewed: %v", err)
	}
}
