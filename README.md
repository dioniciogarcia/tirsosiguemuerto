# WorkspaceHelper

Control your work PC from your phone (and watch), and see your DeskTime
information remotely.

This project has three parts that all talk to each other through a free
Google Firebase "middleman", so nothing depends on your home IP address and
no router setup is needed.

| Part | Folder | What it is |
|------|--------|------------|
| **PC program** | [`pc-program/`](pc-program/) | A small Windows tray app (`WorkspaceHelper.exe`). It does the actual work on the computer: open/close DeskTime, press F9, take a screenshot, shut down, and read your DeskTime hours. |
| **Phone app** | [`android-app/`](android-app/) | The Android app (English + Spanish) you use to send commands and see your stats. *(Built after the PC program works.)* |
| **Watch app** | *(later)* | A round glance-and-tap app for the Galaxy Watch 7 / Watch 5. |
| **Firebase setup** | [`firebase/`](firebase/) | One-time, free setup that links the PC and the phone. |

## What it can do

- Start and stop the DeskTime process from your phone
- Start/stop the mouse helper (presses **F9**) with a 15 / 30 / 60-minute timer
- See your DeskTime hours, productivity, projects, and apps used
- Take an on-demand screenshot to confirm the screen status (manual only)
- Schedule the PC to shut down at a set time
- Push notification + alert when the PC goes offline
- Fingerprint confirmation before starting/stopping DeskTime

## Build order

1. **Firebase** — do the one-time setup in [`firebase/SETUP.md`](firebase/SETUP.md).
2. **PC program** — follow [`pc-program/README.md`](pc-program/README.md).
3. **Phone app** — then the watch.

> **A note on secrets:** your DeskTime API key and Firebase details are
> personal. They live in a `config.json` file that is **deliberately kept out
> of this repository** (see `.gitignore`), so they are never uploaded. You
> create that file once on your own PC — the guides tell you exactly how.
