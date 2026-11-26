# Android app

The phone app (English + Spanish). It shows your DeskTime data and controls
your PC through the shared Firebase database.

It's a lightweight app: a native Android shell hosting the UI in
`app/src/main/assets/www/index.html`. GitHub builds it into an APK
automatically.

## Install

1. Download the latest APK:
   **https://github.com/dioniciogarcia/tirsosiguemuerto/releases/download/app-latest/WorkspaceHelper.apk**
2. Open it on your phone. If asked, allow "install unknown apps" for your
   browser/files app.
3. Open **WorkspaceHelper** and, on the Setup screen, enter your
   **Firebase URL** and **Firebase secret** (the same two values from the PC
   `config.json`). Tap **Connect**.
4. Pick your device in **Settings → Device** if more than one is shown.

## Sharing with someone else

Everyone installs the same APK. Each person enters the same Firebase URL +
secret (shared database), runs their own PC program with their own DeskTime
key, and picks their own PC under **Settings → Device**.

## What it does

- Live DeskTime status, hours, productivity, projects, and apps
- Start/stop DeskTime (with a confirm step), mouse helper (F9) with a timer
- On-demand screenshot, scheduled shutdown, panic stop
- English / Spanish, dark theme
