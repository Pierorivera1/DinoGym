#!/usr/bin/env python3
"""Writes the three diagram drafts (with %%ICON_x%% tokens) from the stack-diagram template.

  python3 docs/architecture/src/gen.py <template.html>

Then build and render each draft with the stack-diagram skill (docs/architecture/README.md).
"""
import pathlib, re, sys

HERE = pathlib.Path(__file__).parent


def node(cx, top, icon, name, role, tile="bare", wide=False, dark_icon=None):
    left, width = (cx - 76, 152) if wide else (cx - 66, 132)
    img = f'<img src="%%ICON_{icon}%%" alt="">'
    if dark_icon:
        img = f'<img class="lt" src="%%ICON_{icon}%%" alt=""><img class="dk" src="%%ICON_{dark_icon}%%" alt="">'
    cls = "tile" + (f" {tile}" if tile else "")
    style = f"left:{left}px;top:{top}px" + (f";width:{width}px" if wide else "")
    return (f'    <div class="node" style="{style}">\n'
            f'      <div class="{cls}">{img}</div>\n'
            f'      <div class="name"><span>{name}</span></div><div class="role"><span>{role}</span></div>\n'
            f'    </div>')


def group(cls, left, top, right, bottom, label):
    return (f'    <div class="group {cls}" style="left:{left}px; top:{top}px; '
            f'width:{right - left}px; height:{bottom - top}px;">\n'
            f'      <div class="g-label">{label}</div>\n    </div>')


def edge(cls, d, marker="ar"):
    return f'      <path class="{cls}" d="{d}" marker-end="url(#{marker})"/>'


def label(x, y, text, accent=False, anchor="middle"):
    return f'      <text class="el{" a" if accent else ""}" x="{x}" y="{y}" text-anchor="{anchor}">{text}</text>'


AWS_LABEL = ('<img class="lt" src="%%ICON_awscloud%%" alt="">'
             '<img class="dk" src="%%ICON_awscloud_dark%%" alt="">AWS Cloud')
MARKERS = """      <defs>
        <marker id="ar" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6.5" markerHeight="6.5" orient="auto-start-reverse">
          <path d="M0 0 L10 5 L0 10 z" fill="context-stroke"/>
        </marker>
        <marker id="open" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6.5" markerHeight="6.5" orient="auto-start-reverse">
          <path d="M1 1 L9 5 L1 9" fill="none" stroke="context-stroke" stroke-width="1.5"/>
        </marker>
      </defs>"""


def page(template, *, title, kicker, lede, w, h, groups, edges, nodes, legend, credits):
    html = template
    html = html.replace("<title><!-- TITLE --></title>", f"<title>{title}</title>")
    html = html.replace("<h1><!-- TITLE --></h1>", f"<h1>{title}</h1>")
    html = re.sub(r'<div class="kicker">.*?</div>', f'<div class="kicker">{kicker}</div>', html, count=1)
    html = re.sub(r'<p class="lede">.*?</p>', f'<p class="lede">{lede}</p>', html, count=1)
    html = html.replace("width: 1000px; height: 540px;", f"width: {w}px; height: {h}px;")
    html = html.replace("width:1000px; height:540px;", f"width:{w}px; height:{h}px;")
    body = ("    <!-- GROUPS -->\n" + "\n".join(groups) + "\n\n"
            f'    <!-- EDGES -->\n    <svg class="edges" viewBox="0 0 {w} {h}" aria-hidden="true">\n'
            + MARKERS + "\n" + "\n".join(edges) + "\n    </svg>\n\n"
            "    <!-- NODES -->\n" + "\n".join(nodes) + "\n")
    html = re.sub(r'(<div class="canvas">\n).*?(\n  </div>\n  </div>\n\n  <div class="legend">)',
                  lambda m: m.group(1) + "\n" + body + m.group(2), html, count=1, flags=re.S)
    html = re.sub(r'(<div class="legend">\n).*?(\n  </div>)',
                  lambda m: m.group(1) + "\n".join(f"    {x}" for x in legend) + m.group(2),
                  html, count=1, flags=re.S)
    html = re.sub(r'<span><!-- FOOT-LEFT.*?</span>', '<span>DinoGym · Pierorivera1/DinoGym</span>', html, flags=re.S)
    html = re.sub(r'<!-- FOOT-RIGHT.*?-->', f"icons: {credits}", html, flags=re.S)
    html = html.replace("</style>", ".group.aws.region > .g-label { top:8px; left:12px; }\n</style>", 1)
    return html


LEG_REQ = '<span><span class="sw"></span>request path</span>'
LEG_INT = '<span><span class="sw n"></span>internal</span>'
LEG_DASH = '<span><span class="sw n dash"></span>dev / deploy</span>'


def architecture(t):
    R1, R2, R3 = 200, 360, 520
    groups = [
        group("aws", 186, 92, 934, 694, AWS_LABEL),
        group("aws region", 210, 140, 910, 670, "us-east-1 · one stack each for staging and prod"),
        group("ga", 970, R2 - 60, 1150, R2 + 150,
              '<img src="%%ICON_firebase%%" alt="">Firebase'),
    ]
    edges = [
        edge("e", f"M96 {R1+30} H262"), label(140, R1 + 22, "HTTPS · JWT", True),
        edge("e", f"M96 {R2+30} H262"), label(140, R2 + 22, "HTTPS", True),
        edge("aws", f"M336 {R1+30} H424", "open"), label(380, R1 + 22, "proxy"),
        edge("aws", f"M496 {R1+30} H604", "open"), label(550, R1 + 22, "sync"),
        edge("aws", f"M300 {R2-6} V{R1+106}", "open"), label(310, R2 - 24, "/auth /sync /shares", anchor="start"),
        edge("aws", f"M300 {R2+106} V{R3-6}", "open"), label(310, R3 - 24, "OAC", anchor="start"),
        edge("aws", f"M460 {R1+106} V{R2-6}", "open"), label(470, R2 - 24, "secrets", anchor="start"),
        edge("aws", f"M460 {R1-6} V165 H820 V{R1-6}", "open"), label(640, 157, "one schedule per user"),
        edge("aws", f"M820 {R1+128} V{R2-6}", "open"), label(830, R2 - 14, "invoke", anchor="start"),
        edge("aws", f"M784 {R2+30} H640 V{R1+106}", "open"), label(712, R2 + 22, "devices"),
        edge("n", f"M856 {R2+30} H1024"), label(952, R2 + 22, "send"),
        edge("n", f"M1096 {R2+30} H1176 V50 H60 V{R1-6}"), label(640, 42, "push notification"),
    ]
    nodes = [
        node(60, R1, "android", "DinoGym app", "Room · WorkManager", tile=""),
        node(60, R2, "react", "Web dashboard", "React · Vite", tile=""),
        node(300, R1, "apigw", "Amazon API Gateway", "HTTP API · throttled", wide=True),
        node(460, R1, "lambda", "AWS Lambda", "Go API"),
        node(640, R1, "dynamodb", "Amazon DynamoDB", "single table"),
        node(820, R1, "scheduler", "Amazon EventBridge Scheduler", "per-user reminders", wide=True),
        node(300, R2, "cloudfront", "Amazon CloudFront", "CloudFront Function"),
        node(460, R2, "ssm", "AWS Systems Manager", "Parameter Store", wide=True),
        node(820, R2, "lambda", "AWS Lambda", "Go reminders"),
        node(300, R3, "s3", "Amazon S3", "dashboard · share page", wide=True),
        node(1060, R2, "firebase", "Cloud Messaging", "FCM v1", tile=""),
    ]
    return page(t, title="DinoGym architecture", kicker="DinoGym / runtime",
                lede="The phone works offline and syncs through a serverless Go API. "
                     "Terraform deploys the same module to staging and prod.",
                w=1200, h=730, groups=groups, edges=edges, nodes=nodes,
                legend=[LEG_REQ, LEG_INT], credits="AWS Architecture Icons / svgl")


def cicd(t):
    R1, R2, R3 = 110, 340, 570
    X = [460, 620, 780, 940, 1100]
    groups = [
        group("gb", 370, R1 - 60, 1190, R1 + 150,
              '<img src="%%ICON_ghactions%%" alt="">Pull request checks (parallel workflows)'),
        group("gb", 370, R2 - 60, 1190, R2 + 150,
              '<img src="%%ICON_ghactions%%" alt="">deploy.yml · push to main'),
        group("gb", 370, R3 - 60, 710, R3 + 150,
              '<img src="%%ICON_ghactions%%" alt="">release.yml · tag v*'),
    ]
    edges = [
        edge("n", f"M106 {R2+30} H182"), label(144, R2 + 22, "push"),
        edge("n", f"M220 {R2-6} V{R1+30} H{X[0]-36}"), label(320, R1 + 22, "pull request"),
        edge("n", f"M256 {R2+30} H{X[0]-36}"), label(320, R2 + 22, "merge"),
        edge("n", f"M220 {R2+106} V{R3+30} H{X[0]-36}"), label(320, R3 + 22, "tag"),
        edge("e", f"M{X[0]+36} {R2+30} H{X[1]-36}"), label(540, R2 + 22, "pass", True),
        edge("e", f"M{X[1]+36} {R2+30} H{X[2]-36}"), label(700, R2 + 22, "zips", True),
        edge("e", f"M{X[2]+36} {R2+30} H{X[3]-36}"), label(860, R2 + 22, "healthy", True),
        edge("e", f"M{X[3]+36} {R2+30} H{X[4]-36}"), label(1020, R2 + 22, "approved", True),
        edge("e", f"M{X[0]+36} {R3+30} H{X[1]-36}"), label(540, R3 + 22, "APK", True),
    ]
    nodes = [
        node(70, R2, "laptop", "Developer", "git push", tile=""),
        node(220, R2, "github", "GitHub", "Pierorivera1/DinoGym", tile="", dark_icon=None),
        node(X[0], R1, "android", "Android", "build · test · lint", tile=""),
        node(X[1], R1, "phone", "Android emulator", "instrumented tests", tile=""),
        node(X[2], R1, "go", "Go API", "OpenAPI · go test", tile=""),
        node(X[3], R1, "react", "Web dashboard", "Vitest · build", tile=""),
        node(X[4], R1, "docker", "AWS Emulator", "Terraform · Go tests", tile=""),
        node(X[0], R2, "docker", "Emulator checks", "same as the PR", tile=""),
        node(X[1], R2, "go", "Build once", "two Lambda zips", tile=""),
        node(X[2], R2, "terraform", "Staging", "OIDC · plan · apply", tile=""),
        node(X[3], R2, "approve", "Owner approval", "prod environment", tile=""),
        node(X[4], R2, "terraform", "Prod", "same zips · /health", tile=""),
        node(X[0], R3, "android", "Signed build", "release keystore", tile=""),
        node(X[1], R3, "github", "GitHub Releases", "DinoGym-x.y.z.apk", tile=""),
    ]
    return page(t, title="DinoGym CI/CD", kicker="DinoGym / delivery",
                lede="Every pull request is checked against the AWS Emulator. A merge deploys "
                     "staging on real AWS; prod waits for the owner.",
                w=1230, h=750, groups=groups, edges=edges, nodes=nodes,
                legend=['<span><span class="sw"></span>deploy path</span>',
                        '<span><span class="sw n"></span>trigger</span>'],
                credits="svgl / devicon / Lucide")


def access(t):
    R1, R2, R3 = 120, 280, 480
    groups = [
        group("aws", 210, R1 - 60, 870, R2 + 150, AWS_LABEL),
        group("ga", 910, R3 - 60, 1250, R3 + 150,
              '<img src="%%ICON_gcp%%" alt="">Google Cloud · Firebase'),
    ]
    edges = [
        edge("e", f"M126 {R1+30} H262"), label(195, R1 + 22, "OIDC token", True),
        edge("aws", f"M336 {R1+30} H424", "open"), label(380, R1 + 22, "state"),
        edge("aws dash", f"M300 {R1+106} V{R2-14} H445 V{R2-6}", "open"), label(308, R2 - 24, "terraform apply", anchor="start"),
        edge("aws", f"M424 {R2+30} H336", "open"), label(380, R2 + 22, "secrets"),
        edge("aws", f"M496 {R2+30} H584", "open"), label(540, R2 + 22, "logs"),
        edge("n", f"M480 {R2-6} V{R2-36} H1080 V{R2-6}"), label(1000, R2 - 44, "OTLP traces · metrics · logs"),
        edge("e", f"M460 {R2+106} V{R3+30} H964"), label(700, R3 + 22, "AWS identity → Google token", True),
        edge("e", f"M1036 {R3+30} H1124"), label(1080, R3 + 22, "send", True),
        edge("n", f"M816 {R1+30} H1044"), label(930, R1 + 22, "50% · 100%"),
    ]
    nodes = [
        node(90, R1, "ghactions", "GitHub Actions", "staging · prod jobs", tile=""),
        node(300, R1, "iam", "AWS IAM", "OIDC deploy role"),
        node(460, R1, "s3", "Amazon S3", "Terraform state"),
        node(780, R1, "budgets", "AWS Budgets", "$10 a month"),
        node(1080, R1, "mail", "Email alert", "owner", tile=""),
        node(300, R2, "ssm", "AWS Systems Manager", "SecureString", wide=True),
        node(460, R2, "lambda", "AWS Lambda", "API · reminders"),
        node(620, R2, "cloudwatch", "Amazon CloudWatch", "Logs · 7 days", wide=True),
        node(1080, R2, "grafana", "Grafana Cloud", "dashboards · alerts", tile=""),
        node(1000, R3, "gcpiam", "Workload Identity", "no key file", tile=""),
        node(1160, R3, "firebase", "Cloud Messaging", "send-only account", tile=""),
    ]
    return page(t, title="DinoGym keyless access and observability", kicker="DinoGym / operations",
                lede="No long-lived keys: CI and the reminder Lambda trade their identity for "
                     "short-lived credentials. Telemetry goes to Grafana Cloud.",
                w=1290, h=660, groups=groups, edges=edges, nodes=nodes,
                legend=[LEG_REQ.replace("request path", "credential exchange"), LEG_INT, LEG_DASH],
                credits="AWS Architecture Icons / Google Cloud product icons / svgl / devicon / Lucide")


if __name__ == "__main__":
    tpl = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
    for name, fn in (("architecture", architecture), ("cicd", cicd), ("access", access)):
        (HERE / f"{name}.html").write_text(fn(tpl), encoding="utf-8")
        print("wrote", HERE / f"{name}.html")
