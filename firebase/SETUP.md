# Firebase setup (one time, free)

Firebase is the free Google "middleman" that lets your phone and your PC talk
to each other from anywhere — no router changes, no IP address to chase.

You'll end up with **two values** to paste into the PC's `config.json`:
`firebase_url` and `firebase_secret`.

It takes about 5 minutes.

---

## 1. Create the project

1. Go to <https://console.firebase.google.com/> and sign in with a Google
   account.
2. Click **Add project**. Give it a name like `workspacehelper`. Click through
   (you can turn Google Analytics **off** — not needed). Click **Create**.

## 2. Create the Realtime Database

1. In the left menu, open **Build → Realtime Database**.
2. Click **Create Database**.
3. Pick the location closest to you, then choose **Start in locked mode** and
   click **Enable**.
4. At the top you'll see the database address, like:

   ```
   https://workspacehelper-xxxx-default-rtdb.firebaseio.com
   ```

   That is your **`firebase_url`**. Copy it.

## 3. Get the secret (so the PC can connect)

1. Click the **gear icon** (top-left, next to "Project Overview") → **Project
   settings**.
2. Open the **Service accounts** tab → **Database secrets** (a "Legacy" / older
   option, but the simplest for this).
3. Click **Show** and copy the secret. That is your **`firebase_secret`**.

> If you don't see "Database secrets", tell me and we'll use the newer
> service-account method instead — it's a downloaded file rather than a
> pasted value.

## 4. Lock the database to just you

So nobody else can read or write your data, set the rules.

1. Back in **Realtime Database**, open the **Rules** tab.
2. Replace what's there with:

   ```json
   {
     "rules": {
       ".read": "auth != null",
       ".write": "auth != null"
     }
   }
   ```

3. Click **Publish**.

Because both the PC program and the phone app sign in with the secret/key,
they can read and write — but random people cannot.

---

## 5. Paste the two values

Open the PC's `config.json` and fill in:

```json
"firebase_url": "https://workspacehelper-xxxx-default-rtdb.firebaseio.com",
"firebase_secret": "the-long-secret-you-copied"
```

That's the Firebase side done. Next: build and run the PC program
(see [`../pc-program/README.md`](../pc-program/README.md)).

---

### How the data is organised (for reference)

```
commands/   <- the phone drops a command here; the PC does it and deletes it
status/     <- the PC keeps this updated:
   online, ts (heartbeat), desktime_running, mouse_running, shutdown_at
   hours/      (DeskTime summary, projects, apps)
   screenshot/ (the latest on-demand screenshot)
```
