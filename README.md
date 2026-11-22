# MindTrack 

**MindTrack** is an Android app that helps university students notice how their **phone habits** and **mood** are connected. It combines a short questionnaire scored by an **on-device AI model**, a daily mood journal, the phone's **real** screen-time data, and gentle tools that help students act on what they see.

> Notice that this is **Not a medical device.** MindTrack is a student research prototype. It shows patterns, not causes, and its results are not a diagnosis.

This repository is the **mobile app** (Mobile Applications unit). The machine-learning model was trained separately (Artificial Intelligence unit) and is shipped inside the app as `app/src/main/assets/mindtrack_model.json`.

---

## Contents
1. [Features](#features)
2. [Tech stack](#tech-stack)
3. [Getting started (run it on your machine)](#getting-started)
4. [Firebase setup](#firebase-setup)
5. [Setting up the emulator for a demo](#setting-up-the-emulator-for-a-demo)
6. [Permissions](#permissions)
7. [Architecture and project structure](#architecture-and-project-structure)
8. [Running the tests](#running-the-tests)
9. [Privacy and data](#privacy-and-data)
10. [Troubleshooting](#troubleshooting)

---

## Features

### 🧪 AI check-in (core feature)
- A **41-question check-in** in three parts: about you (6), phone dependence (22, the TMD scale) and feelings (13 anxiety items from the STAI). It takes about 4 minutes.
- **On-device AI model:** a random forest of 300 decision trees, trained on survey data from **1,220 university students**, estimates whether the answers look more like students with a Beck Depression Inventory score of **11 or more**.
- The **result screen** has an animated gauge showing a **Low** or **Elevated** indicator, the phone-dependence band and support lines.
- **History and trend:** every check-in is saved, with a trend chart over time.
- The model runs **fully offline**. Questionnaire answers never leave the phone.

### Daily mood journal
- **One-tap mood logging** with five faces (Very low to Great), plus an optional note.
- Stored in an on-device **SQLite database**, one entry per day (logging again updates that day).
- 14-day mood calendar on Insights.
- ** Mood streak** on Home.
- **Playful feedback:** the face bounces, sparkles burst on good days, and a short, kind message appears, with a breather offered on harder days.

###  Real screen-time insights
- Uses Android's **UsageStatsManager** to show **actual** screen time, phone unlocks and most-used apps (the student grants "Usage access").
- 7-day bar chart; daily totals are saved to the database so history builds up over time.
- **Mood vs screen time** chart (14 days), with a plain-language summary of the student's own pattern. With 7+ days it adds a **correlation (Pearson r)**, and always notes that a pattern is not proof of cause.

###  Screen-time goals and nudges
- **Daily screen-time goal** with an animated **progress ring** on Home (teal, then amber from 80%, then red when passed).
- **Suggested goal:** 15% under the student's own 7-day average.
- **Goal nudges** at 80% and 100%, at most once each per day.
- **Bedtime wind-down:** a nudge if the phone is in real use after a chosen bedtime.
- **Weekly wins**, for example "🎉 Goal met on 5 of the last 7 days".

###  Reminders (WorkManager)
- **Daily mood reminder**, skipped automatically if the mood is already logged.
- **Check-in reminder**, weekly or every 2 weeks, skipped if a check-in was done recently.
- **Log your mood from the notification**, with 🙁 😐 🙂 buttons and no need to open the app.
- Tapping a notification opens the right screen (deep links).
- A "Send a test reminder now" button for demos.

###  Home-screen widget
- Shows the streak and today's mood, with **one-tap mood buttons**.
- Stays in sync with the app both ways.
- Hides which mood was logged while App lock is on.

###  App lock and privacy
- An optional **App lock** using the phone's PIN, pattern, password, fingerprint or face (BiometricPrompt).
- Re-locks after 30 seconds in the background, and there is a **Lock now** button in the top bar.
- While locked, the app is **hidden in Recents and screenshots are blocked** (FLAG_SECURE).

###  Wellbeing and safety
- **Safety net:** after **three low moods in a row** or a recent **Elevated** result, Home shows a caring card with one-tap calls to Kenya Red Cross (1199) and Befrienders Kenya, and the breather. "Not now" hides it for 3 days.
- **1-minute breather:** a guided breathing exercise (4 s in, 2 s hold, 6 s out) with an animated circle and gentle vibrations.
- **Get support now** is always available in the ⋮ menu.

###  Shareable summary for a counsellor (PDF)
- Creates a **PDF of the last 30 days** on the phone, containing:
  - mood chart and screen-time chart;
  - check-in results;
  - the mood vs screen-time pattern;
  - support lines.
- **In-app preview** of the real pages before anything is shared.
- **Share** (email, WhatsApp, Drive…) through a secure FileProvider, or **save to Downloads**.
- Mood notes are only included if the student ticks the box. Questionnaire answers are never included.

###  Cloud backup (Firebase)
- Optional **email and password account** (Firebase Authentication).
- Check-in results are backed up to **Cloud Firestore** and can be **restored on a new phone** ("I already have a backup").
- **Data minimisation:** only results are uploaded, never the questionnaire answers.

### Design and usability
- **Material 3** design with Jetpack Compose, plus full **dark mode** support.
- **Animations:**
  - cards rise in one after another;
  - tabs slide in the direction you tap;
  - tapped cards press in slightly;
  - floating bubbles on the Home banner;
  - a waving 👋;
  - a pop and sparkles when a check-in is finished.
- **Navigation:** 4 bottom tabs (Home, Insights, History, Profile) plus a **⋮ menu** with quick actions.
- **First-run tutorial:** 4 swipeable cards, which can be replayed from ⋮ → How to use MindTrack.
- **Accessibility:** screen-reader labels on interactive elements, and animations follow Android's "Remove animations" setting.

---

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.4.20 |
| UI | Jetpack Compose, Material 3 (Compose BOM 2026.02.01) |
| Build | Android Gradle Plugin 9.3.3 (built-in Kotlin), Gradle 9.5.0 |
| SDK | minSdk 24 (Android 7.0), target/compileSdk 37 |
| Architecture | Single activity + `AndroidViewModel`, Compose state, state-based navigation |
| Local storage | SQLite (`SQLiteOpenHelper`, schema v3 with migrations) and SharedPreferences |
| Background work | WorkManager 2.11.2 |
| Cloud | Firebase Authentication and Cloud Firestore (Firebase BoM 35.2.0) |
| Security | androidx.biometric 1.1.0 (BiometricPrompt), FLAG_SECURE |
| Phone data | UsageStatsManager (usage access) |
| Files | PdfDocument, PdfRenderer, FileProvider, MediaStore |
| AI model | Random forest exported to JSON, evaluated in Kotlin on the device |

---

## Getting started

### 1. What you need
- The **latest stable Android Studio**. This project uses **AGP 9.3.3**; if Android Studio says the Android Gradle Plugin is too new, update Android Studio (Help → Check for Updates).
- Android Studio's **bundled JDK** (nothing extra to install).
- An **emulator** (API 34 recommended, for example a Pixel 4 with Android 14) or an Android phone with USB debugging.
- Internet access the first time, so Gradle can download libraries.

### 2. Get the code
Either:
- **In Android Studio:** File → New → Project from Version Control, then paste the repository URL:
  ```
  https://github.com/AustinNganga/MindTrack_mobile_app.git
  ```
- **Or in a terminal:**
  ```bash
  git clone https://github.com/AustinNganga/MindTrack_mobile_app.git
  ```
  then open the folder in Android Studio (File → Open).

> The repository is private, so the owner must add you as a collaborator first (GitHub → Settings → Collaborators).

### 3. Check Firebase
- Make sure **`app/google-services.json`** exists. See [Firebase setup](#firebase-setup); with the default option there is nothing to do.

### 4. Sync and run
1. Wait for the **Gradle sync** to finish. If it doesn't start by itself, click the 🐘 elephant icon (*Sync Project with Gradle Files*). The first sync can take a few minutes.
2. Pick your emulator or phone in the device menu at the top.
3. Press **Run ▶** (Shift + F10).

The app opens on the welcome screen. Enter a name, accept the consent screen, and the tutorial will show you around.

---

## Firebase setup

Cloud backup is **optional**: the whole app works offline without an account. You only need Firebase for "Turn on backup" and "Restore from backup".

### Option A: use the existing project (default, nothing to do)
The repository already contains `app/google-services.json` for the team's Firebase project, which has:
- **Authentication** with **Email/Password** enabled;
- **Cloud Firestore** (production mode, location `africa-south1`) with owner-only security rules.

Clone, sync and run. Sign-up and backup work straight away, and the accounts you create appear in the team's Firebase console.

> The API key inside `google-services.json` is **not a secret**; it only identifies the project. Data is protected by the Firestore security rules below. Still, keep the repository private.

### Option B: use your own Firebase project
Do this if you want your own separate database.

1. **Create a project:** go to <https://console.firebase.google.com>, choose **Add project**, give it a name, and skip Google Analytics if you like.
2. **Register the Android app:**
   - Click the **Android** icon.
   - **Package name:** `com.example.mindtrack_app` (it must match exactly).
   - A nickname is optional, and the **SHA-1 is not needed** (email/password sign-in doesn't use it).
3. **Download `google-services.json`** and put it in the **`app/`** folder, replacing the existing one.
   - You can **skip** the "Add Firebase SDK" step; the Gradle files are already set up.
4. **Turn on Authentication:** Build → Authentication → **Get started** → Sign-in method → **Email/Password** → Enable → Save.
5. **Create Firestore:**
   - Build → Firestore Database → **Create database**.
   - Choose a location close to you (we used `africa-south1`, Johannesburg). The location can't be changed later.
   - Start in **production mode**.
6. **Publish these security rules** (Firestore → **Rules** tab → paste → **Publish**), so each user can only read and write their own data:
   ```
   rules_version = '2';
   service cloud.firestore {
     match /databases/{database}/documents {
       match /users/{userId} {
         allow read, write: if request.auth != null && request.auth.uid == userId;
         match /checkIns/{checkInId} {
           allow read, write: if request.auth != null && request.auth.uid == userId;
         }
       }
     }
   }
   ```
7. **Sync Gradle and run the app.** Go to **Profile → Cloud backup → Turn on backup**, create an account, then check **Authentication → Users** and **Firestore → Data** in the console.

**Data layout in Firestore:**
```
users/{uid}                        ← profile (display name)
users/{uid}/checkIns/{checkInId}   ← one document per check-in result (no questionnaire answers)
```

---

## Setting up the emulator for a demo

| To show… | Do this once |
|---|---|
| **App lock** | Emulator Settings → Security → Screen lock → **PIN** (for example 1234). Then in MindTrack: Profile → **App lock** on. |
| **Screen time, goals, mood vs screen time** | MindTrack → Insights → **Allow usage access** → find MindTrack → switch on **Permit usage access**. Or, from a terminal: `adb shell appops set com.example.mindtrack_app GET_USAGE_STATS allow` |
| **Reminders and nudges** | Turn a reminder on in Profile and tap **Allow** when asked about notifications. Use **Send a test reminder now** or **Send a test nudge now** for an instant demo. |
| **Home-screen widget** | Long-press the home screen → **Widgets** → MindTrack → drag **MindTrack mood** onto the screen. |
| **Dark mode** | Swipe down twice → **Dark theme** tile. |
| **Tutorial again** | ⋮ → **How to use MindTrack**. |
| **Lots of data for charts** | Log a mood every day and use the emulator for a while (Chrome, YouTube). Charts and patterns appear after about 4 days. |

**Tips:**
- While App lock is on, **screenshots come out black** (on purpose). Turn the lock off before taking report screenshots.
- Background checks (goals, bedtime) run about **every 15 minutes**, Android's minimum interval for background work.

---

## Permissions

| Permission | Why | When it's asked |
|---|---|---|
| `INTERNET` | Firebase cloud backup | Automatic (no prompt) |
| `PACKAGE_USAGE_STATS` | Read real screen time (special "Usage access" permission) | When the student taps **Allow usage access** (opens system Settings) |
| `POST_NOTIFICATIONS` (Android 13+) | Reminders, goal and bedtime nudges | When a reminder or nudge is first switched on |
| `<queries>` launcher intent | Show app names and keep only user-facing apps in screen time | Automatic |
| Biometric / device credential | App lock (handled by Android's BiometricPrompt; the app never sees the PIN) | When App lock is turned on |

---

## Architecture and project structure

```
UI (Jetpack Compose screens)
        │  observes state / calls functions
        ▼
MindTrackViewModel  (AndroidViewModel: app state + navigation)
        │
        ├── MindTrackModel          → on-device random forest (assets/mindtrack_model.json)
        ├── LocalRepository         → check-ins & profile (SharedPreferences)
        ├── MoodRepository          → mood_entries table (SQLite)
        ├── ScreenTimeRepository    → screen_time_daily table (SQLite)
        ├── ScreenTimeReader        → UsageStatsManager
        ├── CloudBackup             → Firebase Auth + Firestore
        └── AppSettings             → preferences (reminders, lock, goals)

Background: WorkManager (ReminderWorker, GoalWorker) · BroadcastReceiver (MoodActionReceiver)
            · AppWidgetProvider (MoodWidget)
```

```
app/src/main/java/com/example/mindtrack_app/
├── MainActivity.kt          # single activity: navigation, top bar, tabs, app lock, FLAG_SECURE
├── MindTrackViewModel.kt    # all app state and actions
├── data/                    # questions, repositories, SQLite database, screen time, settings, cloud backup
├── model/                   # MindTrackModel: random-forest inference in Kotlin
├── reminders/               # WorkManager reminders + notification mood buttons (BroadcastReceiver)
├── goals/                   # screen-time goal and bedtime nudges (WorkManager)
├── widget/                  # home-screen widget (AppWidgetProvider + RemoteViews)
├── report/                  # PDF summary (PdfDocument / PdfRenderer / FileProvider)
└── ui/                      # Compose screens, components, animations, theme
app/src/main/assets/mindtrack_model.json   # the trained model (300 trees)
app/src/test/                              # unit tests (model parity check)
```

**SQLite database (`mindtrack.db`):**
| Table | Columns |
|---|---|
| `mood_entries` | `date` (PK, yyyy-MM-dd), `mood` (1–5, CHECK constraint), `note`, `updated_at` |
| `screen_time_daily` | `date` (PK), `total_ms`, `unlocks`, `updated_at` |

**Migrations:** v1 → v2 added `screen_time_daily`; v2 → v3 cleared over-counted screen-time history after the counting fix.

---

## Running the tests
- **In Android Studio:** right-click `app/src/test/java` → **Run 'Tests in…'**.
- **Or in a terminal:**
  ```bash
  ./gradlew test        # macOS / Linux
  gradlew.bat test      # Windows
  ```

`ModelParityTest` checks that the Kotlin model gives the **same predictions as the original Python model** (to within 1e-9) on the test vectors in `app/src/test/resources/test_vectors.json`.

---

## Privacy and data
- **On the phone only (by default):** questionnaire answers, check-in results, moods, notes and screen time.
- **Uploaded only if the student turns on backup:** the display name and check-in **results** (no answers, moods or screen time).
- **Screen time** is read and processed on the device and never uploaded.
- **PDF summaries** are created on the phone; the student chooses whether and with whom to share them.
- **Delete all data** (Profile) wipes local data, settings, reminders and the widget state.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `Module was compiled with an incompatible version of Kotlin` | Make sure `kotlin = "2.4.20"` in `gradle/libs.versions.toml`, then sync. |
| "The project is using an incompatible version of the Android Gradle plugin" | Update Android Studio to the latest stable version. |
| `File google-services.json is missing` | Put `google-services.json` in the `app/` folder (see [Firebase setup](#firebase-setup)). |
| Sign-up fails with a configuration error | In the Firebase console, check that **Email/Password** sign-in is enabled. |
| Backup fails with `PERMISSION_DENIED` | Publish the Firestore rules above. |
| Run ▶ is greyed out | Sync first (🐘 icon), and pick a device in the device menu. |
| Insights shows no screen time | Allow **Usage access** (see the demo setup table). |
| No notifications | Allow notifications: Settings → Apps → MindTrack → Notifications. |
| The widget doesn't appear in the Widgets list | Uninstall the app from the emulator and run it again (launchers cache widget lists). |
| Report screenshots are black | Turn **App lock** off (it blocks screenshots on purpose). |

---

## Support lines (Kenya)
- **Kenya Red Cross:** 1199 (toll-free, 24 hours)
- **Befrienders Kenya:** +254 722 178 177 (call, SMS or WhatsApp)
- Your university counselling centre

---

*MindTrack · student research prototype · Mobile Applications & Artificial Intelligence units, 2026*
