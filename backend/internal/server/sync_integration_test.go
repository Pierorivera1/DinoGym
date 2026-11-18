//go:build integration

package server

import (
	"context"
	"testing"

	"github.com/aws/aws-sdk-go-v2/service/dynamodb"

	"github.com/Pierorivera1/DinoGym/backend/internal/awstest"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// TestSyncOnTheEmulator runs the sync suite on the real store and DynamoDB on
// the AWS Emulator: the conditional writes, number normalization, empty
// strings and booleans as DynamoDB stores them. Each subtest signs in new
// users, so they share one table.
func TestSyncOnTheEmulator(t *testing.T) {
	db := dynamodb.NewFromConfig(awstest.Config(t))
	table := awstest.Name("dinogym-server-test")
	st := store.New(db, table)
	// The cursor's settle window is checked in the store's own tests; here
	// pages must cover rows the suite has just pushed.
	st.SetSettleWindow(0)
	if err := st.CreateTable(context.Background()); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if _, err := db.DeleteTable(context.Background(), &dynamodb.DeleteTableInput{TableName: &table}); err != nil {
			t.Errorf("delete table: %v", err)
		}
	})
	runSyncSuite(t, func(*testing.T) SyncStore { return st })
}
