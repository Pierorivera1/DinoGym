# DinoGym

Rebuild of DinoGym: a native Android workout tracker with a cloud backend. It is a resume project for both Android and cloud/DevOps roles, and an app the owner uses daily.

Read these before making non-trivial changes:

- [docs/PROJECT.md](docs/PROJECT.md): what the project is, who it is for, features and scope
- [docs/STACK.md](docs/STACK.md): the technology chosen for each area
- [docs/TASKS.md](docs/TASKS.md): every task in order, with checkboxes. Tick a task when it is done
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): app structure, data model and details for tasks 1–10
- [docs/DESIGN.md](docs/DESIGN.md): the approved style guide and which screens have approved mocks
- [docs/WORKFLOW.md](docs/WORKFLOW.md): the emulator-first pipeline and what the AWS Emulator can and cannot do
- [docs/DECISIONS.md](docs/DECISIONS.md): every decision made so far, with the reason
- [docs/OPEN-ITEMS.md](docs/OPEN-ITEMS.md): what is not decided or not set up yet

## Hard rules

### Do not overheat the laptop

A previous agent ran builds or tests that held the CPU at 100% and pushed it to 90°C. This machine has 14 threads and 15 GB of RAM.

- Keep these limits in `android/gradle.properties` (they also apply inside Android Studio), and do not raise them:
  ```properties
  org.gradle.workers.max=3
  org.gradle.parallel=false
  org.gradle.jvmargs=-Xmx2g
  kotlin.daemon.jvmargs=-Xmx1536m
  ```
- Run only the tests for the code that just changed (one module or one test class), never the whole suite locally.
- Do not start an Android emulator or run instrumented (`androidTest`) tests locally. Those run in GitHub Actions.
- For Go, limit test parallelism (`go test -p 2`) and test only the changed packages.
- Tell the owner before starting anything long or heavy.

### The owner must be able to defend the project

The owner will be asked about this project in interviews.

- Work in small pull requests the owner can review.
- Record every real choice in [docs/DECISIONS.md](docs/DECISIONS.md) with its reason and the alternative rejected.
- Do not add a service, library or tool that is not in [docs/STACK.md](docs/STACK.md) without recording why.

### Cost

- AWS spend must stay under $10 a month, always on. See [docs/DECISIONS.md](docs/DECISIONS.md).
- Nothing is created on real AWS until it has passed the emulator stage in [docs/WORKFLOW.md](docs/WORKFLOW.md).
- No long-lived AWS keys anywhere. GitHub Actions uses OIDC.

### Honesty about the AWS Emulator

Do not claim the emulator proves reliability, security or performance. It proves that our AWS API usage is correct. See the limits in [docs/WORKFLOW.md](docs/WORKFLOW.md).

## Environment

- OS: Arch Linux (Omarchy). Packages install with `pacman`.
- Installed: Docker (starts on demand through `docker.socket`; the user is in the `docker` group, so no sudo), AWS CLI v2, GitHub CLI (authenticated), Android Studio, Go and Terraform (both mise, decisions 18 and 37).
- Not installed yet: the AWS Emulator (Docker route in [docs/WORKFLOW.md](docs/WORKFLOW.md)).
- AWS CLI has no credentials yet. They are not needed until the staging step.
- The previous app is version 1, renamed on GitHub to `Pierorivera1/Old-DinoGym` (local clone: `~/Projects/OLD-DinoGym`). Do not change it. This project takes over the name `Pierorivera1/DinoGym`.

## Product rules

- Offline first: every feature works without a connection; sync happens when it can.
- Every user-facing string goes in resources, in English and Spanish.
- Keep docs current: tick or remove items in [docs/OPEN-ITEMS.md](docs/OPEN-ITEMS.md), and add dated entries to the end of [docs/DECISIONS.md](docs/DECISIONS.md).

## UI and design

- No screen is built until the owner approves a mock of it (decision 16).
- Use the design skill for mock screens and visual direction, and follow [docs/DESIGN.md](docs/DESIGN.md). The design canvas is https://claude.ai/artifact/3XzHLFUzvpe5iuf5SrAhXj; keep its source in `docs/design/mocks/` in step with it. Use the owner's mobile UI skills when writing Compose screens.

## Sub-agents and threads

- Before development work, assess whether a separate worktree would prevent interference with other active work. Use one when concurrent tasks may edit the same checkout; read-only work and isolated changes do not automatically need one.
- Review worktree changes and check for conflicts before integrating them into `main`. Resolve conflicts without discarding existing work, run relevant checks, and merge the completed work into `main`.
- Do not spawn sub-agents or multi-agent panels for work a single agent can finish in one pass.
- Delegate when a task has independent parts that can clearly finish faster in parallel. Give each agent a bounded task and the context it needs, then review and integrate its results.
- The main orchestrator is Fable 5.2. Sub-agents use Opus 5.5 with medium reasoning effort, unless Piero specifies another model. If the tool cannot select it, state the limitation.
- Sub-agents follow the same CPU limits as everyone else: no parallel heavy builds or test runs across agents.
- Skill-provided agent presets do not override these delegation, model, or reasoning defaults.
- Use sub-agents to conserve the main thread's context when useful. When a separate T3 Code thread would help, suggest a handoff with the current decisions, relevant files, and remaining work. You can create a new thread whenever you need to delegate work.
