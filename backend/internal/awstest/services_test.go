//go:build integration

package awstest

// The task 14 checks for the services later tasks use (decision 59): SQS for
// background jobs, S3 for shared routines (task 23), and EventBridge Scheduler
// delivering to SQS for reminders (task 24). The DynamoDB sync rules are tested
// on the real store code in internal/store.

import (
	"context"
	"fmt"
	"io"
	"strings"
	"testing"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/iam"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/aws/aws-sdk-go-v2/service/scheduler"
	schedtypes "github.com/aws/aws-sdk-go-v2/service/scheduler/types"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	sqstypes "github.com/aws/aws-sdk-go-v2/service/sqs/types"
)

func newQueue(t *testing.T, client *sqs.Client) (queueURL, queueARN string) {
	t.Helper()
	ctx := context.Background()
	q, err := client.CreateQueue(ctx, &sqs.CreateQueueInput{QueueName: aws.String(Name("dinogym-jobs"))})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _, _ = client.DeleteQueue(context.Background(), &sqs.DeleteQueueInput{QueueUrl: q.QueueUrl}) })
	attrs, err := client.GetQueueAttributes(ctx, &sqs.GetQueueAttributesInput{
		QueueUrl:       q.QueueUrl,
		AttributeNames: []sqstypes.QueueAttributeName{sqstypes.QueueAttributeNameQueueArn},
	})
	if err != nil {
		t.Fatal(err)
	}
	return *q.QueueUrl, attrs.Attributes[string(sqstypes.QueueAttributeNameQueueArn)]
}

// receive waits up to wait for one message and returns its body, or "".
func receive(t *testing.T, client *sqs.Client, queueURL string, wait time.Duration) string {
	t.Helper()
	deadline := time.Now().Add(wait)
	for time.Now().Before(deadline) {
		out, err := client.ReceiveMessage(context.Background(), &sqs.ReceiveMessageInput{
			QueueUrl:        &queueURL,
			WaitTimeSeconds: 5,
		})
		if err != nil {
			t.Fatal(err)
		}
		if len(out.Messages) > 0 {
			return *out.Messages[0].Body
		}
	}
	return ""
}

func TestSQSSendAndReceive(t *testing.T) {
	client := sqs.NewFromConfig(Config(t))
	url, _ := newQueue(t, client)
	body := `{"type":"weekly-summary","userId":"u1"}`
	if _, err := client.SendMessage(context.Background(), &sqs.SendMessageInput{QueueUrl: &url, MessageBody: &body}); err != nil {
		t.Fatal(err)
	}
	if got := receive(t, client, url, 10*time.Second); got != body {
		t.Fatalf("received %q, want %q", got, body)
	}
}

func TestS3PutAndGet(t *testing.T) {
	// Path-style URLs: a bucket name in front of 127.0.0.1 does not resolve.
	client := s3.NewFromConfig(Config(t), func(o *s3.Options) { o.UsePathStyle = true })
	ctx := context.Background()
	bucket, key := Name("dinogym-test"), "routines/hello.json"
	if _, err := client.CreateBucket(ctx, &s3.CreateBucketInput{Bucket: &bucket}); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx := context.Background()
		_, _ = client.DeleteObject(ctx, &s3.DeleteObjectInput{Bucket: &bucket, Key: &key})
		if _, err := client.DeleteBucket(ctx, &s3.DeleteBucketInput{Bucket: &bucket}); err != nil {
			t.Errorf("delete bucket: %v", err)
		}
	})

	want := `{"name":"hola dinocito"}`
	if _, err := client.PutObject(ctx, &s3.PutObjectInput{
		Bucket: &bucket, Key: &key, Body: strings.NewReader(want), ContentType: aws.String("application/json"),
	}); err != nil {
		t.Fatal(err)
	}
	out, err := client.GetObject(ctx, &s3.GetObjectInput{Bucket: &bucket, Key: &key})
	if err != nil {
		t.Fatal(err)
	}
	defer out.Body.Close()
	got, _ := io.ReadAll(out.Body)
	if string(got) != want || aws.ToString(out.ContentType) != "application/json" {
		t.Fatalf("got %q (%s), want %q", got, aws.ToString(out.ContentType), want)
	}
}

// TestSchedulerDeliversToSQS checks what task 14 found by hand: a schedule
// needs a role Scheduler may assume, and that role needs sqs:SendMessage on the
// queue, or the message is dropped. It takes about 10 seconds.
func TestSchedulerDeliversToSQS(t *testing.T) {
	cfg := Config(t)
	ctx := context.Background()
	queues := sqs.NewFromConfig(cfg)
	queueURL, queueARN := newQueue(t, queues)

	roles := iam.NewFromConfig(cfg)
	roleName := Name("dinogym-scheduler")
	role, err := roles.CreateRole(ctx, &iam.CreateRoleInput{
		RoleName: &roleName,
		AssumeRolePolicyDocument: aws.String(`{"Version":"2012-10-17","Statement":[{"Effect":"Allow",` +
			`"Principal":{"Service":"scheduler.amazonaws.com"},"Action":"sts:AssumeRole"}]}`),
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		ctx := context.Background()
		_, _ = roles.DeleteRolePolicy(ctx, &iam.DeleteRolePolicyInput{RoleName: &roleName, PolicyName: aws.String("send")})
		_, _ = roles.DeleteRole(ctx, &iam.DeleteRoleInput{RoleName: &roleName})
	})
	if _, err := roles.PutRolePolicy(ctx, &iam.PutRolePolicyInput{
		RoleName:   &roleName,
		PolicyName: aws.String("send"),
		PolicyDocument: aws.String(fmt.Sprintf(`{"Version":"2012-10-17","Statement":[{"Effect":"Allow",`+
			`"Action":"sqs:SendMessage","Resource":%q}]}`, queueARN)),
	}); err != nil {
		t.Fatal(err)
	}

	schedules := scheduler.NewFromConfig(cfg)
	name := Name("fire-test")
	at := time.Now().UTC().Add(5 * time.Second).Format("2006-01-02T15:04:05")
	if _, err := schedules.CreateSchedule(ctx, &scheduler.CreateScheduleInput{
		Name:               &name,
		ScheduleExpression: aws.String("at(" + at + ")"),
		FlexibleTimeWindow: &schedtypes.FlexibleTimeWindow{Mode: schedtypes.FlexibleTimeWindowModeOff},
		Target: &schedtypes.Target{
			Arn:     &queueARN,
			RoleArn: role.Role.Arn,
			Input:   aws.String("fired"),
		},
	}); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = schedules.DeleteSchedule(context.Background(), &scheduler.DeleteScheduleInput{Name: &name})
	})

	if got := receive(t, queues, queueURL, 30*time.Second); got != "fired" {
		t.Fatalf("received %q within 30 s, want the schedule's \"fired\"", got)
	}
}
