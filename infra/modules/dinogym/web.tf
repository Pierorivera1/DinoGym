# The public web side (task 23, decisions 98–99): one CloudFront distribution
# with two origins.
#
#   /r/<id>                     the shared routine page (S3, rewritten by a function)
#   /web/*                      its CSS, JavaScript, icon and font (S3)
#   /.well-known/assetlinks.json  Android App Links (S3)
#   /shares/*                   GET only, to the HTTP API, so the page reads the
#                               share from its own origin (no CORS)
#
# The bucket stays private: CloudFront reads it with origin access control.
# No custom domain (decision 23), so links use the *.cloudfront.net name.

locals {
  web_dir = "${path.module}/../../../web/share"

  # Files under /web/ and their content types.
  web_assets = {
    "r.css"                 = "text/css; charset=utf-8"
    "r.js"                  = "text/javascript; charset=utf-8"
    "icon.svg"              = "image/svg+xml"
    "pixelify_sans_OFL.txt" = "text/plain; charset=utf-8"
  }

  # The app's own font file, so the page and the app use the same one.
  font_file = "${path.module}/../../../android/app/src/main/res/font/pixelify_sans.ttf"

  # Digital Asset Links: which app may open /r/* links of this domain
  # without asking (decision 99).
  assetlinks = jsonencode([
    for app in var.app_links : {
      relation = ["delegate_permission/common.handle_all_urls"]
      target = {
        namespace                = "android_app"
        package_name             = app.package_name
        sha256_cert_fingerprints = app.sha256_cert_fingerprints
      }
    }
  ])

  api_origin_domain = replace(aws_apigatewayv2_api.api.api_endpoint, "https://", "")
}

resource "aws_s3_bucket" "web" {
  # Bucket names are global, so the account id keeps this one unique.
  bucket = "${local.name}-web-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket                  = aws_s3_bucket.web.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Only this distribution may read the bucket, and only objects.
resource "aws_s3_bucket_policy" "web" {
  bucket = aws_s3_bucket.web.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "CloudFrontReadsObjects"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.web.arn}/*"
      Condition = { StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.web.arn } }
    }]
  })
  depends_on = [aws_s3_bucket_public_access_block.web]
}

# The page itself. Terraform fills in this environment's app package, which
# the "Open in DinoGym" button names in its intent link.
resource "aws_s3_object" "page" {
  bucket = aws_s3_bucket.web.id
  key    = "web/r.html"
  content = templatefile("${local.web_dir}/r.html", {
    package      = var.app_links[0].package_name
    releases_url = var.releases_url
  })
  content_type = "text/html; charset=utf-8"
  # Short, so a new page reaches people within minutes without invalidations.
  cache_control = "public, max-age=300"
}

resource "aws_s3_object" "asset" {
  for_each      = local.web_assets
  bucket        = aws_s3_bucket.web.id
  key           = "web/${each.key}"
  source        = "${local.web_dir}/${each.key}"
  source_hash   = filemd5("${local.web_dir}/${each.key}")
  content_type  = each.value
  cache_control = "public, max-age=300"
}

resource "aws_s3_object" "font" {
  bucket        = aws_s3_bucket.web.id
  key           = "web/pixelify_sans.ttf"
  source        = local.font_file
  source_hash   = filemd5(local.font_file)
  content_type  = "font/ttf"
  cache_control = "public, max-age=86400"
}

resource "aws_s3_object" "assetlinks" {
  bucket        = aws_s3_bucket.web.id
  key           = ".well-known/assetlinks.json"
  content       = local.assetlinks
  content_type  = "application/json"
  cache_control = "public, max-age=300"
}

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${local.name}-web"
  description                       = "CloudFront reads ${aws_s3_bucket.web.id}."
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# /r/<id> → /web/r.html. A CloudFront Function, because S3 cannot serve one
# object for many keys, and a custom error page would also rewrite the API's
# own 404s.
resource "aws_cloudfront_function" "share_page" {
  name    = "${local.name}-share-page"
  runtime = "cloudfront-js-2.0"
  comment = "Serves /web/r.html for every /r/<id> link."
  publish = true
  code    = file("${local.web_dir}/rewrite.js")
}

# The API sends Cache-Control itself (an hour for a share); nothing else is in
# the cache key, so every viewer of a link shares one cached copy.
resource "aws_cloudfront_cache_policy" "api" {
  name        = "${local.name}-api"
  comment     = "Follows the API's Cache-Control; no headers, cookies or query strings."
  min_ttl     = 0
  default_ttl = 0
  max_ttl     = 86400

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config {
      cookie_behavior = "none"
    }
    headers_config {
      header_behavior = "none"
    }
    query_strings_config {
      query_string_behavior = "none"
    }
    enable_accept_encoding_gzip   = true
    enable_accept_encoding_brotli = true
  }
}

# The page loads only its own files and talks only to its own origin. Names in
# a share are user text; the page never puts them in as HTML, and this policy
# blocks any script that is not ours if that is ever got wrong.
resource "aws_cloudfront_response_headers_policy" "web" {
  name    = "${local.name}-web"
  comment = "Security headers for the shared routine page."

  security_headers_config {
    content_security_policy {
      content_security_policy = "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
      override                = true
    }
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "no-referrer"
      override        = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = false
      override                   = true
    }
  }
}

resource "aws_cloudfront_distribution" "web" {
  enabled         = true
  comment         = "${local.name}: shared routine links"
  is_ipv6_enabled = true
  http_version    = "http2and3"
  # North America and Europe edges only: the cheapest class. Viewers elsewhere
  # (South America included) are served from the nearest of those.
  price_class = "PriceClass_100"

  origin {
    origin_id                = "web"
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  origin {
    origin_id   = "api"
    domain_name = local.api_origin_domain
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # Everything else: the page and its files from S3, cached with
  # CloudFront's managed CachingOptimized policy, which follows each object's
  # Cache-Control.
  default_cache_behavior {
    target_origin_id           = "web"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6" # Managed-CachingOptimized
    response_headers_policy_id = aws_cloudfront_response_headers_policy.web.id
    compress                   = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.share_page.arn
    }
  }

  # Reading a share. GET and HEAD only, so publishing (POST /shares) cannot go
  # through CloudFront: the app calls the API directly for that.
  ordered_cache_behavior {
    path_pattern           = "/shares/*"
    target_origin_id       = "api"
    viewer_protocol_policy = "https-only"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = aws_cloudfront_cache_policy.api.id
    compress               = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
