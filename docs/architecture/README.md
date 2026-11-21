# Diagrams

The three diagrams in the main README, made with the [stack-diagram](https://github.com/caru-ini/stack-diagram) skill (v0.7.0, decision 114).

| Image | Shows |
|---|---|
| `architecture.png` | The runtime: app, dashboard, CloudFront, API Gateway, the two Lambdas, DynamoDB, SSM, Scheduler and FCM |
| `cicd.png` | The pull request checks, the staging → approval → prod deploy, and the APK release |
| `access.png` | OIDC deploy roles, Terraform state, secrets, telemetry, Google workload identity federation and the budget alert |

`src/gen.py` writes `src/<name>.html`: the diagram as HTML with `%%ICON_<name>%%` tokens in place of logos. The logos are not committed. AWS and Google allow their icons in diagrams, but redistributing the icon files is not confirmed, so they are fetched again on each build.

## Regenerate

The skill lives in `.claude/skills/stack-diagram/` (git-ignored). Install it with `npx skills add caru-ini/stack-diagram`, or copy `skills/stack-diagram` from the v0.7.0 tag. PNG output needs Python Playwright; a virtual environment outside the repository is enough, and it can drive the system Chromium.

```bash
SK=.claude/skills/stack-diagram
ICONS=$(mktemp -d)
python3 $SK/scripts/fetch_icons.py "$ICONS" android react go docker terraform github kotlin firebase grafana \
  "ghactions:githubactions" "gcp:google-cloud" "gcpiam:gcp/iam" \
  "awscloud:aws/AWS-Cloud-logo" "cloudfront:aws/cloudfront" "s3:aws/s3" "apigw:aws/api-gateway" \
  "lambda:aws/lambda" "dynamodb:aws/dynamodb" "scheduler:aws/Amazon-EventBridge" "ssm:aws/systems-manager" \
  "cloudwatch:aws/cloudwatch" "iam:aws/iam" "budgets:aws/budgets" \
  "phone:lucide/smartphone" "laptop:lucide/laptop" "approve:lucide/user-check" "mail:lucide/mail"
python3 docs/architecture/src/gen.py $SK/assets/template.html
for n in architecture cicd access; do
  python3 $SK/scripts/build.py docs/architecture/src/$n.html "$ICONS" /tmp/$n.html --fonts $SK/assets/fonts
  python3 $SK/scripts/render_png.py /tmp/$n.html docs/architecture/$n.png --selector .canvas --scale 2 --single
done
```

Look at each PNG before committing: no arrow through text, nothing outside its group.
