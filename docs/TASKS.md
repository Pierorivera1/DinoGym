# Tasks

The whole build, in order. Tick a task when it is done. Details for tasks 1–10 are in [ARCHITECTURE.md](ARCHITECTURE.md); the reasons behind each choice are in [DECISIONS.md](DECISIONS.md). Every new screen needs an approved mock first ([DESIGN.md](DESIGN.md), decision 16).

Order agreed 2026-11-16. Tasks 1–10 are planned in detail; from task 11 on the order is set by decision 31 and the details are still open.

## Part 1: first daily-usable app (offline, no cloud)

- [x] **1. Project setup.** Gradle wrapper, version catalog, `:app` with Compose, Navigation 3, Hilt and Room; package `io.github.pierorivera1.dinogym`, minSdk 26, targetSdk 36. An empty app that launches.
- [x] **2. CI.** GitHub Actions workflow: build, unit tests, lint on every push.
- [x] **3. Theme and components.** Night and Day colours, bundled fonts, stepped corners, raised buttons, text box, stat bar, stepper, Dinocito's cursor, bottom bar, first icons. English and Spanish string files.
- [x] **4. Database and training rules.** Room tables and repositories, the owner's program loaded on first run. Rules with unit tests: cycle advance, pre-fill from last session, set-1 bests. DAO tests in CI.
- [x] **5. Workout flow** (approved mocks). Home, active exercise, rest picker, rest timer in the background, summary. A workout survives an app restart.
- [x] **6. Second mock batch** (alongside tasks 1–5). Programs, Exercises, Stats, Settings, for the owner's approval.
- [x] **7. Programs.** Program list, cycle editor, routine editor, exercise picker.
- [x] **8. Exercises.** Library, add and edit (icon, fixed-pattern template, weight switch), archive and restore.
- [x] **9. Stats.** History by date, edit a past session, progress view per exercise.
- [x] **10. Settings and first release.** Theme setting, Spanish review of every string, release signing (keystore kept out of git), signed APK on GitHub Releases. **The owner switches from version 1 here.**

## Part 2: more app features

- [x] **11. Body weight.** Mock, then weigh-in entry and the chart with the 7-day average and goal line. Offline.

## Part 3: cloud foundations (before sign-in)

- [x] **12. Decide the open cloud items.** Sync conflict design, DynamoDB data model, AWS region, where secrets live, keyless auth to Firebase Cloud Messaging. Decisions 51–55.
- [x] **13. Docker.** Docker starts on demand (`docker.socket`) and the user is in the `docker` group, so no sudo is needed (decision 56).
- [x] **14. AWS Emulator.** Pinned 1.0.491 in Docker; DynamoDB, SQS, S3, Scheduler and Terraform checked, the Go Lambda cannot run ([WORKFLOW.md](WORKFLOW.md), decisions 57–59, [report](reports/aws-emulator/aws-emulator-report.pdf)).
- [x] **15. OpenAPI contract** for the API; it generates the Kotlin client and the Go server interfaces ([api/openapi.yaml](../api/openapi.yaml), decisions 60–62).
- [x] **16. Go backend skeleton.** Runs locally against the emulator, with OpenTelemetry.
- [x] **17. Terraform**, proven on the emulator first ([infra/](../infra/README.md), decisions 66–67).
- [x] **18. Pipeline.** Pull request → emulator tests; merge → staging on real AWS; approval → prod. OIDC, no stored keys, AWS budget alarm (decisions 68–73). The deploy jobs stay off until task 19.
- [x] **19. First real AWS setup.** `aws login`, apply `infra/bootstrap`, set up the GitHub environments and variables ([infra/README.md](../infra/README.md)), first staging and prod deploy, under $10 a month (decisions 74–76).

## Part 4: cloud features (order from decision 31)

- [x] **20. Sign-in.** Google sign-in on the phone; the backend issues its own sessions (decisions 79–84).
- [ ] **21. Sync** between devices (WorkManager on the phone; API and DynamoDB on the backend).
- [ ] **22. Starter programs and program builder.** Gym UL/PPL, Calisthenics UL/PPL, 4-day Upper/Lower.
- [ ] **23. Shareable routines.** A public web link that imports into the app.
- [ ] **24. Reminders.** EventBridge Scheduler → Lambda → Firebase Cloud Messaging: "today is pull day", weekly summary.
- [ ] **25. Web dashboard.** React on S3 and CloudFront; history and progress charts after logging in.
- [ ] **26. Observability.** Grafana Cloud dashboards and alerts in Terraform; Crashlytics in the app.
