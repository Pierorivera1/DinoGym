# The bucket that holds Terraform state for every root (decision 66). The name
# includes the account ID because S3 bucket names are global.

resource "aws_s3_bucket" "state" {
  bucket = local.state_bucket
  # force_destroy stays false: a bucket that still holds state cannot be
  # deleted by a stray `terraform destroy`.
}

# Every write keeps the previous version, so a broken state can be rolled back.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

# Old versions are kept 90 days, then deleted, so the bucket does not grow forever.
resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"
    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 90
    }
  }

  depends_on = [aws_s3_bucket_versioning.state]
}

# SSE-S3: free, and no KMS key to manage or pay for.
resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    # S3 now blocks customer-provided keys on new buckets by default. Saying so
    # here keeps the next plan empty instead of trying to undo that default.
    blocked_encryption_types = ["SSE-C"]
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# State can hold secrets, so it is never sent over plain HTTP.
resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  # Both change the bucket's policy settings; one at a time avoids a conflict.
  depends_on = [aws_s3_bucket_public_access_block.state]
}
