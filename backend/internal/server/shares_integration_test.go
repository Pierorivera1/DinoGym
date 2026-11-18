//go:build integration

package server

import (
	"context"
	"testing"

	"github.com/aws/aws-sdk-go-v2/service/dynamodb"

	"github.com/Pierorivera1/DinoGym/backend/internal/awstest"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// TestSharesOnTheEmulator runs the shares suite on the real store and
// DynamoDB on the AWS Emulator: the conditional daily counter, the share item
// and its consistent read. Each subtest signs in new users, so they share one
// table.
func TestSharesOnTheEmulator(t *testing.T) {
	db := dynamodb.NewFromConfig(awstest.Config(t))
	table := awstest.Name("dinogym-shares-test")
	st := store.New(db, table)
	if err := st.CreateTable(context.Background()); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		if _, err := db.DeleteTable(context.Background(), &dynamodb.DeleteTableInput{TableName: &table}); err != nil {
			t.Errorf("delete table: %v", err)
		}
	})
	runSharesSuite(t, func(*testing.T) Store { return st })
}
