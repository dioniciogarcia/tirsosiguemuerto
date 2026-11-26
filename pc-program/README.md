# PC program — WorkspaceHelper.exe

This runs on your **Windows work PC**. It sits in the system tray and carries
out whatever the phone app asks. No window, never steals focus.

> Do the [Firebase setup](../firebase/SETUP.md) **first** — you need the two
> Firebase values before this will connect.

---

## 1. Install Python (one time)

1. Go to <https://www.python.org/downloads/> and install Python (3.10 or newer).
2. On the first install screen, **tick "Add Python to PATH"**, then click
   Install.

## 2. Get these files onto your PC

Download this repository (green **Code** button → **Download ZIP** on GitHub),
unzip it, and open the `pc-program` folder.

## 3. Create your private settings file

1. In the `pc-program` folder, make a copy of `config.example.json`.
2. Rename the copy to exactly **`config.json`**.
3. Open `config.json` in Notepad and fill in:
   - `firebase_url` and `firebase_secret` — from the Firebase setup guide.
   - `desktime_api_key` — your personal DeskTime API key.
   - `desktime_exe_path` — where DeskTime is installed. To find it: right-click
     the DeskTime shortcut → **Open file location** → right-click
     `DeskTime.exe` → **Properties** → copy the "Location", add `\DeskTime.exe`
     to the end. In the file, write every `\` as `\\` (two backslashes).
   - `device_name` — any name you like, e.g. "Work PC".

   Save and close.

> `config.json` stays on your PC only. It is never uploaded to GitHub.

## 4. Build the program

Double-click **`build_exe.bat`**. It installs what's needed and creates the
program. When it finishes you'll have:

```
dist\WorkspaceHelper.exe
```

Move `WorkspaceHelper.exe` and your `config.json` into the **same folder**
(anywhere you like, e.g. `C:\WorkspaceHelper\`).

## 5. Run it

Double-click `WorkspaceHelper.exe`. A small icon appears in the tray (next to
the clock). That's it — the phone app can now control the PC.

- To stop it: right-click the tray icon → **Quit**.
- A `workspacehelper.log` file is written next to the program — handy if
  something doesn't work.

## 6. (Optional) Start it automatically with Windows

Press `Win + R`, type `shell:startup`, Enter. Drop a **shortcut** to
`WorkspaceHelper.exe` into that folder. Now it starts quietly every time you
log in.

---

### Prefer to run without building an .exe?

You can also just run the script directly (same folder as `config.json`):

```
pip install -r requirements.txt
python workspacehelper.py
```

---

### What each command does

| From the app | On the PC |
|--------------|-----------|
| Start / Stop DeskTime | Opens the DeskTime program, or closes its process |
| DeskTime timer | Opens now and closes after the timer (or the reverse) |
| Mouse helper (F9) | Presses F9 to toggle your mouse controller; optional 15/30/60-min timer |
| Confirm screen status | Takes one screenshot and sends it to the app |
| Shutdown at… | Shuts the PC down at the set time |
| Panic | Stops DeskTime **and** the mouse helper at once |

The app also shows your DeskTime hours, productivity, projects, and apps,
read live from the official DeskTime API.
