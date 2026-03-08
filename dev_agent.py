import os
import json
import smtplib
import subprocess
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
import requests
import anthropic

BASE_URL = "http://localhost:8080"
PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
_mvn_script = "mvn.cmd" if os.name == "nt" else "mvn"
MVN = os.getenv(
    "MVN_PATH",
    os.path.expanduser(
        f"~/.m2/wrapper/dists/apache-maven-3.8.7-bin"
        f"/1ktonn2lleg549uah6ngl1r74r/apache-maven-3.8.7/bin/{_mvn_script}"
    ),
)

# SMTP config — set via environment variables
SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")          # sender email
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")  # app password
EMAIL_OWNER = os.getenv("EMAIL_OWNER", "")       # PR owner/reviewer email

MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-4-6")
client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])


# ---------------------------------------------------------------------------
# Tool implementations
# ---------------------------------------------------------------------------

def api_create_customer(externalSystem: str, externalCustomerId: str, fullName: str, email: str | None = None):
    payload = {
        "externalSystem": externalSystem,
        "externalCustomerId": externalCustomerId,
        "fullName": fullName,
        "email": email,
    }
    r = requests.post(f"{BASE_URL}/api/customers", json=payload, timeout=15)
    try:
        body = r.json()
    except Exception:
        body = {"raw": r.text}
    return {"http_status": r.status_code, "body": body}


def api_get_customer(externalSystem: str, externalCustomerId: str):
    r = requests.get(f"{BASE_URL}/api/customers/{externalSystem}/{externalCustomerId}", timeout=15)
    try:
        body = r.json()
    except Exception:
        body = {"raw": r.text}
    return {"http_status": r.status_code, "body": body}


def run_tests_with_coverage():
    """Run mvn verify (tests + JaCoCo coverage check)."""
    result = subprocess.run(
        [MVN, "verify"],
        cwd=PROJECT_DIR,
        capture_output=True,
        text=True,
        timeout=300,
    )
    output = result.stdout + result.stderr
    passed = result.returncode == 0
    # Extract summary lines
    summary_lines = [
        line for line in output.splitlines()
        if any(kw in line for kw in [
            "Tests run:", "BUILD", "coverage checks", "ERROR", "FAILURE"
        ])
    ]
    return {
        "success": passed,
        "summary": "\n".join(summary_lines[-30:]),
        "return_code": result.returncode,
    }


def write_file(file_path: str, content: str):
    """Write content to a file, creating directories if needed."""
    full_path = os.path.join(PROJECT_DIR, file_path.lstrip("/\\"))
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, "w", encoding="utf-8") as f:
        f.write(content)
    return {"success": True, "file": full_path, "bytes_written": len(content)}


def git_commit_and_push(branch: str, commit_message: str):
    """Init git (if needed), commit all changes, push to origin."""
    def run(cmd, **kwargs):
        return subprocess.run(cmd, cwd=PROJECT_DIR, capture_output=True, text=True, **kwargs)

    # Init repo if not already a git repo
    if not os.path.exists(os.path.join(PROJECT_DIR, ".git")):
        run(["git", "init"])
        run(["git", "checkout", "-b", branch])
    else:
        # Create and switch to the branch
        r = run(["git", "checkout", "-b", branch])
        if r.returncode != 0:
            run(["git", "checkout", branch])

    # Stage all relevant source files (exclude build artifacts)
    run(["git", "add", "pom.xml", "src/"])

    # Commit
    commit = run(["git", "commit", "-m", commit_message])
    if commit.returncode != 0 and "nothing to commit" in commit.stdout + commit.stderr:
        return {"success": True, "message": "Nothing new to commit. Branch is already up to date. Proceed with create_pull_request.", "branch": branch}
    if commit.returncode != 0:
        return {"success": False, "message": commit.stderr or commit.stdout}

    # Push feature branch
    push = run(["git", "push", "-u", "origin", branch])
    if push.returncode != 0:
        return {"success": False, "message": f"Commit succeeded but push failed: {push.stderr}"}

    return {"success": True, "message": f"Committed and pushed branch '{branch}'."}


def create_pull_request(branch: str, title: str, body: str, base: str = "main"):
    """Create a GitHub PR using the GitHub API."""
    import urllib.request, urllib.error, re
    token = os.getenv("GH_TOKEN", "")
    if not token:
        return {"success": False, "message": "GH_TOKEN env var not set."}
    remote = subprocess.run(
        ["git", "remote", "get-url", "origin"],
        cwd=PROJECT_DIR, capture_output=True, text=True
    ).stdout.strip()
    m = re.search(r"github\.com[:/](.+?)(?:\.git)?$", remote)
    if not m:
        return {"success": False, "message": f"Cannot parse repo from remote: {remote}"}
    repo = m.group(1)
    data = json.dumps({"title": title, "body": body, "head": branch, "base": base}).encode()
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/pulls",
        data=data,
        headers={
            "Authorization": f"token {token}",
            "Accept": "application/vnd.github.v3+json",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req) as resp:
            result = json.loads(resp.read())
            return {"success": True, "pr_url": result["html_url"]}
    except urllib.error.HTTPError as e:
        return {"success": False, "message": f"HTTP {e.code}: {e.read().decode()}"}


def send_pr_review_email(pr_url: str, pr_title: str, pr_body: str, to_email: str | None = None):
    """Send an HTML email to the PR owner requesting review and merge."""
    recipient = to_email or EMAIL_OWNER
    if not recipient:
        return {"success": False, "message": "No recipient email. Set EMAIL_OWNER env var or pass to_email."}
    if not SMTP_USER or not SMTP_PASSWORD:
        return {"success": False, "message": "SMTP_USER and SMTP_PASSWORD env vars are required."}

    html_body = f"""
    <html><body style="font-family:Arial,sans-serif;line-height:1.6">
      <h2>&#128204; PR Review Requested</h2>
      <p>A new Pull Request has been created and is ready for your review and merge.</p>
      <table style="border-collapse:collapse;width:100%">
        <tr><td style="padding:8px;font-weight:bold;width:120px">Title</td>
            <td style="padding:8px">{pr_title}</td></tr>
        <tr style="background:#f9f9f9">
            <td style="padding:8px;font-weight:bold">PR Link</td>
            <td style="padding:8px"><a href="{pr_url}">{pr_url}</a></td></tr>
        <tr><td style="padding:8px;font-weight:bold;vertical-align:top">Description</td>
            <td style="padding:8px"><pre style="white-space:pre-wrap">{pr_body}</pre></td></tr>
      </table>
      <p style="margin-top:24px">
        Please review, approve, and merge at your earliest convenience.<br/>
        <a href="{pr_url}" style="background:#2ea44f;color:#fff;padding:10px 20px;
           text-decoration:none;border-radius:6px;display:inline-block;margin-top:8px">
          &#128279; Open PR
        </a>
      </p>
      <hr/><p style="color:#888;font-size:12px">Sent by Claude DevOps Agent</p>
    </body></html>
    """

    msg = MIMEMultipart("alternative")
    msg["Subject"] = f"[PR Review Requested] {pr_title}"
    msg["From"] = SMTP_USER
    msg["To"] = recipient
    msg.attach(MIMEText(html_body, "html"))

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as server:
            server.ehlo()
            server.starttls()
            server.login(SMTP_USER, SMTP_PASSWORD)
            server.sendmail(SMTP_USER, recipient, msg.as_string())
        return {"success": True, "message": f"Review email sent to {recipient}."}
    except Exception as e:
        return {"success": False, "message": str(e)}


# ---------------------------------------------------------------------------
# Tool definitions for Claude
# ---------------------------------------------------------------------------

TOOLS = [
    {
        "name": "create_customer",
        "description": "Create a new customer in the Customer Service. Enforces unique(externalSystem, externalCustomerId).",
        "input_schema": {
            "type": "object",
            "properties": {
                "externalSystem": {"type": "string", "description": "External system name, e.g., NAVIGATOR"},
                "externalCustomerId": {"type": "string", "description": "External customer id from that system"},
                "fullName": {"type": "string", "description": "Customer full name"},
                "email": {"type": ["string", "null"], "description": "Customer email (optional)"},
            },
            "required": ["externalSystem", "externalCustomerId", "fullName"],
        },
    },
    {
        "name": "get_customer",
        "description": "Fetch a customer by externalSystem + externalCustomerId.",
        "input_schema": {
            "type": "object",
            "properties": {
                "externalSystem": {"type": "string"},
                "externalCustomerId": {"type": "string"},
            },
            "required": ["externalSystem", "externalCustomerId"],
        },
    },
    {
        "name": "write_file",
        "description": (
            "Write content to a source file in the project. Use this to create or overwrite "
            "Java source files, DTOs, services, controllers, and test files. "
            "file_path should be relative to the project root, "
            "e.g. 'src/main/java/com/example/customerservice/dto/UpdateCustomerRequest.java'."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "file_path": {"type": "string", "description": "Relative path from project root"},
                "content":   {"type": "string", "description": "Full file content to write"},
            },
            "required": ["file_path", "content"],
        },
    },
    {
        "name": "run_tests_with_coverage",
        "description": (
            "Run all tests and JaCoCo code coverage checks (mvn verify). "
            "Returns pass/fail status and a summary of test results and coverage. "
            "Use this before committing to ensure quality gates pass."
        ),
        "input_schema": {
            "type": "object",
            "properties": {},
            "required": [],
        },
    },
    {
        "name": "git_commit_and_push",
        "description": (
            "Stage all source changes, commit them with the given message, and push to the remote branch. "
            "Only call this after run_tests_with_coverage succeeds."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "branch": {"type": "string", "description": "Git branch name to create/push, e.g. 'feature/add-test-coverage'"},
                "commit_message": {"type": "string", "description": "Git commit message"},
            },
            "required": ["branch", "commit_message"],
        },
    },
    {
        "name": "create_pull_request",
        "description": (
            "Open a GitHub Pull Request for the pushed branch. "
            "Requires gh CLI authenticated. Call only after git_commit_and_push succeeds."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "branch": {"type": "string", "description": "The source branch of the PR"},
                "title": {"type": "string", "description": "PR title"},
                "body": {"type": "string", "description": "PR description (markdown)"},
                "base": {"type": "string", "description": "Target branch, defaults to 'main'", "default": "main"},
            },
            "required": ["branch", "title", "body"],
        },
    },
    {
        "name": "send_pr_review_email",
        "description": (
            "Send an HTML email to the PR owner requesting review and merge. "
            "Call this immediately after create_pull_request succeeds, passing the PR URL and details."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "pr_url": {"type": "string", "description": "The GitHub PR URL returned by create_pull_request"},
                "pr_title": {"type": "string", "description": "PR title"},
                "pr_body": {"type": "string", "description": "PR description shown in the email body"},
                "to_email": {
                    "type": ["string", "null"],
                    "description": "Recipient email. If omitted, uses EMAIL_OWNER env var.",
                },
            },
            "required": ["pr_url", "pr_title", "pr_body"],
        },
    },
]

# ---------------------------------------------------------------------------
# System prompt
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = """
You are an enterprise DevOps + integration agent for a Spring Boot customer-service project.

You can:
1. Create / fetch customers via the REST API.
2. Run tests and code coverage checks (JaCoCo, 80% threshold).
3. Commit code changes and push to a git branch.
4. Open a GitHub Pull Request.

Workflow rules:
- When asked to implement a new feature (e.g. a new API endpoint):
    a. Use write_file ONE FILE AT A TIME — never call write_file for multiple files in the same response.
       Write each file in a separate turn and wait for the result before writing the next file.
       Order: DTO → Service → Controller → ServiceTest → ControllerTest → IntegrationTest.
    b. After ALL files are written → call run_tests_with_coverage.
    c. If tests PASS → call git_commit_and_push with a descriptive branch name and commit message.
    d. If git push succeeds → call create_pull_request with a meaningful title and markdown body.
    e. After PR is created → ALWAYS call send_pr_review_email with the PR URL, title, and body.
    f. Report the final PR URL and confirm the review email was sent.
- When asked to run tests, commit, and raise a PR (no new files needed):
    a. Call run_tests_with_coverage first.
    b. If tests PASS -> call git_commit_and_push.
    c. Whether git_commit_and_push says "committed" OR "nothing to commit", ALWAYS proceed to call create_pull_request next.
    d. After PR is created -> call send_pr_review_email.
- If tests FAIL → report the failure details and do NOT commit, push, or send email.
- If create_customer returns 409 (conflict) → call get_customer and explain the existing record.
- Be concise. Return the final result clearly.
"""

# ---------------------------------------------------------------------------
# Agent loop
# ---------------------------------------------------------------------------

TOOL_DISPATCH = {
    "create_customer": lambda i: api_create_customer(**i),
    "get_customer": lambda i: api_get_customer(**i),
    "write_file": lambda i: write_file(i["file_path"], i["content"]) if "content" in i else {"error": "content field missing — write one file at a time"},
    "run_tests_with_coverage": lambda i: run_tests_with_coverage(),
    "git_commit_and_push": lambda i: git_commit_and_push(**i),
    "create_pull_request": lambda i: create_pull_request(**i),
    "send_pr_review_email": lambda i: send_pr_review_email(**i),
}


def _trim_messages(messages: list) -> list:
    """
    Replace the content of previous write_file tool_use inputs with a short
    summary so the accumulated history does not blow up the input token budget.
    Only the most recent assistant turn is kept verbatim.
    """
    trimmed = []
    for i, msg in enumerate(messages):
        is_last = (i == len(messages) - 1)
        if msg["role"] == "assistant" and not is_last:
            slim_content = []
            for block in msg["content"]:
                if hasattr(block, "type") and block.type == "tool_use" and block.name == "write_file":
                    # Replace the large content field with a placeholder
                    from anthropic.types import ToolUseBlock
                    slim_input = {"file_path": block.input.get("file_path", ""), "content": "<truncated>"}
                    slim_content.append(ToolUseBlock(id=block.id, name=block.name,
                                                     input=slim_input, type="tool_use"))
                else:
                    slim_content.append(block)
            trimmed.append({"role": "assistant", "content": slim_content})
        else:
            trimmed.append(msg)
    return trimmed


def run_agent(user_message: str):
    messages = [{"role": "user", "content": user_message}]

    while True:
        # Retry loop for rate-limit errors
        for attempt in range(5):
            try:
                resp = client.messages.create(
                    model=MODEL,
                    max_tokens=8192,
                    system=SYSTEM_PROMPT,
                    messages=_trim_messages(messages),
                    tools=TOOLS,
                )
                break
            except Exception as e:
                if "rate_limit" in str(e).lower() and attempt < 4:
                    wait = 30 * (attempt + 1)
                    print(f"  [rate limit] waiting {wait}s before retry {attempt + 1}/4...")
                    import time; time.sleep(wait)
                else:
                    raise

        tool_uses = [b for b in resp.content if b.type == "tool_use"]
        text_blocks = [b.text for b in resp.content if b.type == "text"]

        if not tool_uses:
            return "\n".join(text_blocks).strip()

        messages.append({"role": "assistant", "content": resp.content})

        tool_results = []
        for tu in tool_uses:
            print(f"  [tool] {tu.name}({json.dumps(tu.input, indent=None)[:120]})")
            fn = TOOL_DISPATCH.get(tu.name)
            result = fn(tu.input) if fn else {"error": f"Unknown tool: {tu.name}"}
            print(f"  [result] {json.dumps(result)[:200]}")
            tool_results.append({
                "type": "tool_result",
                "tool_use_id": tu.id,
                "content": json.dumps(result),
            })

        messages.append({"role": "user", "content": tool_results})


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    print("Claude Tool-Use Agent (Customer Service)")
    print("API :", BASE_URL)
    print("Model:", MODEL)
    print("Project:", PROJECT_DIR)
    print()
    print("Example prompts:")
    print('  Create customer in NAVIGATOR with id EXT-123 name "Harika Maddala" email harika@test.com')
    print("  Run tests, check coverage, commit and raise a PR")
    print("------------------------------------------------------------")

    while True:
        user = input("\nYou> ").strip()
        if user.lower() in {"exit", "quit"}:
            break
        if not user:
            continue
        out = run_agent(user)
        print("\nAgent>\n" + out)
