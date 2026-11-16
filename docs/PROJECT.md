# Project context

## Goal

Build one project that is worth showing on a resume for two kinds of role:

- **Android / mobile developer** (the reviewer looks at the app)
- **Cloud / DevOps and software engineering** (the reviewer looks at the infrastructure, pipelines and backend)

It must also be a real product that the owner and other people can use, not a demo.

The owner has separate learning projects for Terraform, GitHub Actions, Docker and AWS, done by hand. In this project the AI writes the code, and the owner reviews it and must be able to explain every decision.

## What DinoGym is

A native Android workout tracker for bodyweight training. Version 1 already exists (now `Pierorivera1/Old-DinoGym` on GitHub): Kotlin, Jetpack Compose, Material 3, Navigation 3, with all data in a local JSON file and no backend.

This project is a full rebuild. The owner does not like version 1's UI or its overall structure.

## What version 1 does (the baseline to keep)

- Training plans: named workouts, rotating splits, reorderable exercises, targets per workout
- Set logging: repetitions or a custom metric, optional weight in kilograms
- Rest timers per exercise, running in the background
- Exercise library with archive and restore
- History by date, and progress charts of weekly best sets
- Resuming a workout draft after restarting the app
- Light and dark themes

## What the rebuild adds

- **Accounts:** sign in with Google
- **Sync:** workouts and history sync across devices
- **Shareable routines:** publish a workout or split as a public link that others can open on the web and import into their app
- **Push reminders:** server-sent notifications such as "today is pull day" or a weekly summary
- **Web dashboard:** log in on the web to see history and progress charts
- **A redesigned UI**, with mock screens approved by the owner before each screen is built
- **English and Spanish**, following the phone's language

## Principles

- **Offline first:** the app works fully without a connection, as version 1 does, and syncs when it can.
- **Fresh start:** the new app does not import version 1 data (decision 19).
- **Cheap to run:** under $10 a month on AWS, always on.
- **Emulator first:** changes are proven against the AWS Emulator before they reach real AWS.

## Out of scope for now

- Friends and an activity feed (offered, not chosen)
- A Play Store listing (GitHub Releases only for now)
- Email and password sign-in
- iOS
