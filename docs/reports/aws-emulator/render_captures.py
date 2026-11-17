"""Turn each captures/*.txt (real command output) into a terminal-style PNG."""
import html, pathlib, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).parent
OUT = HERE / "screenshots"
OUT.mkdir(exist_ok=True)
TITLES = {
    "01-verify": "Verifying AWS's signature on the emulator package",
    "02-image": "The pinned emulator image",
    "03-start": "Starting the emulator: localhost only, 2 GB cap",
    "04-dynamodb-mistake": "DynamoDB: writing before the table is ACTIVE",
    "05-dynamodb-sync": "DynamoDB: newest edit wins, pull by change number",
    "06-sqs-s3": "SQS and S3",
    "07-scheduler-role": "EventBridge Scheduler: the role must exist",
    "08-scheduler-fire": "EventBridge Scheduler: firing, denied, then delivered",
    "09-lambda-go": "Lambda: the Go function is stored but cannot run",
    "10-lambda-python": "Lambda: a Python function runs",
    "11-terraform-6.73": "Terraform, AWS provider 6.73.0: the S3 bucket fails",
    "12-terraform-6.22": "Terraform, AWS provider 6.22.0: apply, no drift, destroy",
}

CSS = """
body{margin:0;padding:24px;background:#ffffff;font-family:'JetBrainsMono Nerd Font','JetBrains Mono',monospace}
.win{width:1100px;border-radius:10px;overflow:hidden;background:#16181d;box-shadow:0 6px 24px rgba(0,0,0,.25)}
.bar{display:flex;align-items:center;gap:8px;padding:10px 14px;background:#24272e;color:#aab0bb;font-size:13px}
.dot{width:12px;height:12px;border-radius:50%}
.t{margin-left:10px}
pre{margin:0;padding:16px 18px;color:#d7dae0;font:14px/1.5 inherit;font-family:inherit;white-space:pre-wrap;word-break:break-all}
.p{color:#7ec699}.c{color:#e8eaee}.e{color:#ff7b72}.w{color:#e3b341}.ok{color:#56d364;font-weight:bold}
"""

def line_html(l):
    s = html.escape(l)
    if l.startswith("$ "):
        return f'<span class="p">~/Projects/DinoGym</span> <span class="c">$ {s[2:]}</span>'
    if "[ERROR]" in l or l.startswith("detail:") or l.startswith("code:") or "InvalidRuntime" in l:
        return f'<span class="e">{s}</span>'
    if " WARN" in l or "WARNING" in l or l.startswith("an EventBridge"):
        return f'<span class="w">{s}</span>'
    if l.strip() in ("stored", "ready", "fired", "active", "ACTIVE\tACTIVE", "policy attached", "Active") or "Good signature" in l or l.startswith("hola dinocito"):
        return f'<span class="ok">{s}</span>'
    return s

for txt in sorted((HERE / "captures").glob("*.txt")):
    lines = txt.read_text().rstrip("\n").split("\n")
    body = "\n".join(line_html(l) for l in lines)
    title = TITLES.get(txt.stem, txt.stem)
    page = f"""<!doctype html><meta charset=utf-8><style>{CSS}</style>
<div class=win><div class=bar><span class=dot style="background:#ff5f57"></span><span class=dot style="background:#febc2e"></span><span class=dot style="background:#28c840"></span><span class=t>{html.escape(title)}</span></div><pre>{body}</pre></div>"""
    with tempfile.NamedTemporaryFile("w", suffix=".html", delete=False) as f:
        f.write(page)
    png = OUT / f"{txt.stem}.png"
    subprocess.run(["chromium", "--headless=new", "--disable-gpu", "--hide-scrollbars",
                    "--force-device-scale-factor=2", "--window-size=1160,2400",
                    f"--screenshot={png}", f"file://{f.name}"], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["magick", str(png), "-trim", "-bordercolor", "white", "-border", "20", str(png)], check=True)
    print(png.name)
