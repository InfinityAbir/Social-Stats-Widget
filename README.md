# Social Stats Widget — YouTube & Facebook Stats on Your Android Home Screen

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Jetpack_Glance-App_Widget-1A73E8?style=for-the-badge&logo=android&logoColor=white" alt="Glance" />
</p>

<p align="center"><b>A clean, lightweight home-screen widget that shows your YouTube and Facebook Page numbers at a glance — no dashboards, no logins, no ads.</b></p>

<p align="center">Subscribers • Followers • Latest post stats • Creator insights • Posting reminders • Light &amp; dark • Tap to open</p>

<p align="center">
  <img src="docs/screenshots/widget.png" alt="Social Stats home screen widget" width="640" />
</p>

---

## Table of Contents

- [About the Project](#about-the-project)
- [Why I Built It](#why-i-built-it)
- [Who Will Use It](#who-will-use-it)
- [Why They Will Use It](#why-they-will-use-it)
- [Key Features](#key-features)
- [Tests](#tests)
- [Screenshots](#screenshots)
- [Tech Stack](#tech-stack)
- [Architecture Overview](#architecture-overview)
- [Getting Started](#getting-started)
- [Getting Your API Keys](#getting-your-api-keys)
- [Configuration](#configuration)
- [Permissions & Privacy](#permissions--privacy)
- [Known Limitations](#known-limitations)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Author & Contact](#author--contact)

---

## About the Project

**Social Stats Widget** is a native Android app whose whole job is one home-screen widget. It pulls the numbers a creator checks all day and shows them in a single card:

- **YouTube:** subscribers, plus the views, comments and likes of your latest video
- **Facebook Page:** followers, plus the views, comments, reactions and shares of your latest reel

On top of the numbers it adds **creator insights** (how your latest post compares with your usual, how fast it is moving, your best post and best time to post), **alerts**, and **posting-time reminders**.

It uses the official **YouTube Data API v3** and the **Facebook Graph API**. You type your own keys into the app once, and the widget keeps itself up to date.

Built with **Kotlin + Jetpack Compose (Material 3)** for the app and **Jetpack Glance** for the widgets, with **WorkManager** handling background refresh and **AlarmManager** timing the posting reminders.

Package: `com.abir.socialwidget` • Min SDK 26 • Target SDK 36

---

## Why I Built It

I run a small content page, and I kept opening YouTube Studio and Meta Business Suite just to see three numbers. I wanted something I could glance at on my phone without opening anything.

1. **Too many taps for three numbers:** Studio and Business Suite are heavy apps. A widget answers the question instantly.
2. **No single view:** YouTube and Facebook each have their own dashboard. I wanted both side by side.
3. **Own your keys:** Many "analytics" apps want you to sign in with a third-party service. Here the keys stay on your device and the app talks directly to Google and Meta.
4. **Calm design:** A dark, glassy card that looks at home on any wallpaper, with clear icons instead of walls of text.
5. **Learning project:** A real-world exercise in Jetpack Glance, WorkManager, REST APIs with plain `HttpURLConnection`, and handling the awkward parts of the Meta Graph API (business-owned Pages, token lifetimes, permissions).

---

## Who Will Use It

- **Content creators** who post on both YouTube and Facebook and want a quick pulse check.
- **Page admins and social media managers** who check follower and view counts many times a day.
- **Small businesses** running a Facebook Page and a YouTube channel.
- **Developers** who want a compact, readable example of a Glance widget with background work and real APIs.

---

## Why They Will Use It

- **Everything in one glance:** subscribers, followers, latest-post views, comments and how long ago you posted, on the home screen.
- **More than numbers:** "is this post doing better than my usual?", "how fast is it moving?" and "when should I post?" are answered in the app.
- **No account on a third-party server:** the app calls Google and Meta directly. There is no backend, no analytics SDK and no ads.
- **Keys stay on the device:** encrypted with the Android Keystore, never shown again after saving, behind your fingerprint, face or screen lock (which you can switch off), with Android backup switched off so they are not copied to the cloud.
- **Tap to open, tap to refresh:** tapping the widget opens the app; the whole bottom strip refreshes it, so a tap near the button does not open the app by accident.
- **Built-in guides:** the app contains step-by-step help, with links, for getting a YouTube API key and renewing a Facebook token.
- **Honest failure messages:** if Facebook rejects the token, the widget shows a short warning line, and the app shows a banner with a renewal guide.

---

## Key Features

### 1. Home-Screen Widgets
- **Main widget (4x2, resizable):** two cards, **YouTube** (red) and **Facebook** (blue), with brand icons
- Large subscriber and follower counts, formatted as `4,835`, `12.4K`, `1.2M`, with today's change (`▲ 16 today`)
- Latest video or reel: views, comments, likes / reactions / shares, and one line such as `1h 33m ago · 0.7× usual · +807/h · title`
- You choose which of these show (see *Display* below)
- The whole bottom strip is the **Refresh** button. While it runs it says "Updating your numbers…" and ignores extra taps
- **Compact widget (2x1):** subscribers and followers with today's change
- Gradient glass background that works on light and dark wallpapers. The widgets keep their dark look in every app theme
- Tap the cards to open the app

### 2. Background Refresh
- Updates about every **15 minutes** using WorkManager (the shortest interval Android reliably allows)
- Runs only when the device is online
- A failure on one platform never blocks the other; the last good numbers stay on screen
- Being offline is shown as a calm "No internet connection" note, not as a problem with your keys

### 3. Picking the right "latest" post
YouTube and Facebook list posts by upload time, not by when they went public, and a page token also returns scheduled posts. The app therefore reads the last 25 videos (YouTube) or up to 3 pages of 20 reels (Facebook), keeps only the ones that are really public, and picks the one with the **newest go-live time**.
- Scheduled, private, unlisted, upcoming-premiere and failed items are ignored
- A reel Meta already calls published counts even while it is still processing
- If the reel lookup fails (network or Meta error), the last known reel stays on the card

### 4. YouTube and Facebook Data
- **YouTube:** subscribers, plus the latest public video's views, comments, likes, title and publish time. Accepts a channel **@handle**, a channel ID, or a pasted channel URL. Hidden subscriber counts and hidden like counts are handled
- **Facebook:** followers (`followers_count`, falling back to `fan_count`), plus the latest reel's views, comments, reactions, shares (when Meta allows it) and go-live time. Works with Pages owned through a **Business portfolio**

### 5. Creator Insights
Everything below is computed from the posts the app already fetched, so it costs no extra API calls.
- **Against your usual:** the latest post's views as a multiple of your **usual**, which is the *median* of your last 10 posts (one viral hit does not skew it). Shows `No views yet` for a new post, and `early` while a post is under 6 hours old
- **Speed:** views gained per hour, measured between readings at least 30 minutes apart
- **Engagement:** likes and comments (and shares) as a share of views
- **Best of your last N** and **Posts in the last 7 days**
- **Best time to post:** the 3-hour slot whose older posts earned the most views (needs 6+ posts over a day old, and at least 2 in the winning slot)
- **Last 7 days:** subscriber and follower change with a small trend line

### 6. Alerts (all optional, all off until you turn them on)
- **Milestones:** followers, subscribers and latest-post views passing 100, 200, 500, 1K, 2K, 5K, 10K and on
- **Video taking off:** the latest post gains at least 100, 500, 1,000 or 5,000 views per hour
- **New comments** on the latest video or reel
- **Connection problems:** YouTube or Facebook failing for 3 hours, for example an expired Page token. Fires once per episode and never for being offline
- **Posting reminder:** see below

### 7. Posting-Time Reminders
- Set **one or more posting times** (up to 6), the **days** they apply to, and how early to be reminded: **10 min, 30 min or 1 hour before**
- The settings show exactly when the next reminder will arrive
- The notification says how long is really left ("Posting time in 30 minutes"), and nothing is posted if the posting time has already passed
- Runs on its own alarm, independent of the 15-minute refresh and of being online. It is re-armed after a restart or an app update. Android may deliver it a few minutes late while the phone is saving battery

### 8. The App
- **Header** with a status chip: *Connected*, *Offline*, *Setup needed* or *Attention*
- **Live stat tiles** that mirror the widget. Tap a card to open that exact video or reel; the corner label (`Studio ›` / `Page ›`) opens your dashboard
- **First-run setup:** with no keys saved yet the app opens without a lock prompt, shows a short welcome, and puts the connection fields right under the cards, each with a "How do I get…?" link to the matching guide. You can use only YouTube or only Facebook
- **Collapsible sections:** *Display*, *Appearance*, *Alerts*, *Security* and *Connections*, each with a one-line description and a status (`2 on`, `Lock on`, `Saved`)
- **Display:** per platform, choose Views, Comments, Likes / Reactions / Shares, Title, Posted, Growth, vs usual and Speed
- **Appearance:** **System, Light or Dark** theme
- **Testing:**
- JUnit 4 unit tests for the selection, insight and scheduling logic

**Security:** an **App lock** switch (on by default). When on, the app asks for your fingerprint, face or screen lock when opened and again after 30 seconds in the background. The widget shows only counts and stays unlocked
- **Connections:** one card for YouTube (API key, channel) and one for the Facebook Page (Page ID, Page token). Saved secrets are **write-only**: they show as *Saved* and can only be replaced, never viewed or copied
- **Refresh** button and pull-down to refresh, a "Saved and refreshed" note after saving, and warning cards with a shortcut to the matching guide when YouTube or Facebook fails
- **Help & guides:** *Add the widget*, *Get or renew a Facebook token* and *Get a YouTube API key*, each with numbered steps and buttons that open the right page
- Content stays a comfortable width on tablets and folded phones

---

## Tests

The rules that are easy to get wrong live in plain Kotlin with no Android types, and are covered by JUnit tests:

- `Latest.kt` picks the latest post and decides what counts as public on each platform
- `Insights.kt` works out "usual", speed, engagement, best post, best time, comment growth and connection-problem timing
- `Schedule.kt` works out the next posting reminder (days, lead time, midnight and month boundaries) and its wording
- `Errors.kt` turns network failures into plain language

```bash
./gradlew testDebugUnitTest
```

On Windows use `gradlew.bat testDebugUnitTest`.

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/app-main.png" alt="Main screen with live stat tiles and connection settings" width="300" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/screenshots/app-help.png" alt="Collapsible Help and guides section" width="300" />
</p>

<p align="center"><i>Live stat tiles and connections • Collapsible Help &amp; guides</i></p>

<p align="center"><sub>Channel handle and Page ID are example placeholders in these screenshots. They show an earlier version of the app, before the insights, alerts, reminders and themes.</sub></p>

---

## Tech Stack

**Language & UI:**
- Kotlin 2.2.10, Jetpack Compose (BOM 2026.09.00), Material 3

**Widget:**
- Jetpack Glance (`glance-appwidget` and `glance-material3` 1.1.1), vector icons and shape drawables for the gradient cards

**Background work:**
- WorkManager 2.12.0 (periodic and one-time refresh)
- AlarmManager (`setAndAllowWhileIdle`) for the posting reminders, with a boot / app-update receiver to re-arm it

**Networking & data:**
- `HttpURLConnection` + `org.json` (no Retrofit/OkHttp needed), YouTube Data API v3, Facebook Graph API v21.0
- `SharedPreferences` in private app storage for keys and the cached numbers

**Security:**
- AndroidX Biometric 1.1.0 for the app lock (biometrics or device credential)
- Android Keystore (AES-256-GCM) to encrypt the API key and Page token at rest

**Build:**
- Gradle 9.3.1, Android Gradle Plugin 9.1.1, Java 17, compileSdk 37

See `gradle/libs.versions.toml` and `app/build.gradle.kts` for exact versions.

---

## Architecture Overview

Small single-module app. The rules live in plain Kotlin (unit tested); the screens and widgets only display what they produce.

**Data and background**
- `Repo.kt` — fetches YouTube and Facebook independently, caches the result and redraws the widgets
- `Latest.kt` — picks the latest public post by go-live time; public / scheduled rules for each platform
- `Store.kt` — settings and cached stats on top of `SharedPreferences`; secrets are saved through `Vault`
- `Vault.kt` — AES-256-GCM encryption with a key that lives in the Android Keystore
- `History.kt` — one record per day (30 days) for today's growth, the 7-day change and the trend line
- `Scheduler.kt` — `RefreshWorker` plus periodic (15 min) and one-time WorkManager scheduling
- `Errors.kt` — plain-language errors, and offline detection

**Insights, alerts and reminders**
- `Insights.kt` — usual (median), speed, engagement, best post, best time, comment growth, connection-problem timing
- `Notifier.kt` — milestone, taking-off, comment and connection-problem notifications
- `Schedule.kt` — next posting reminder and its wording
- `Reminders.kt` — the reminder alarm and its receiver (also re-arms after boot and app update)

**UI**
- `MainActivity.kt` — app lock, the Compose screen with collapsible sections, write-only key fields and Help guides
- `InsightsUi.kt` / `ReminderUi.kt` — the insights section and the posting-reminder settings
- `Theme.kt` — light and dark palettes and the System / Light / Dark choice
- `StatsWidget.kt` / `CompactWidget.kt` — the two Glance widgets and the refresh action
- `Display.kt` — what each card and widget shows, and the badge text

Data flow: **WorkManager / Refresh tap → `Repo.refreshAll()` → YouTube + Facebook APIs → `Store` → `StatsWidget.updateAll()`**.

---

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (current stable release recommended), or just JDK 17 and the Android SDK
- Android SDK Platform 37
- An Android device or emulator on Android 8.0 (API 26) or newer
- Your own API keys (free), see [Getting Your API Keys](#getting-your-api-keys)

### Run Locally

1. Clone the repo:
   ```bash
   git clone https://github.com/InfinityAbir/Social-Stats-Widget.git
   ```
2. Open the folder in Android Studio and let Gradle sync. Select **JDK 17** (the bundled JBR works) under **Settings > Build, Execution, Deployment > Build Tools > Gradle**.
3. Run the app on a device, or build from the terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows use `gradlew.bat assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
4. Open **Social Stats**, enter your keys and tap **Save & refresh**.
5. Long-press the home screen, choose **Widgets**, and drag **Social Stats** onto it.

---

## Getting Your API Keys

The same steps are available inside the app under **Help**.

### YouTube (free)
1. Open [Google Cloud Console](https://console.cloud.google.com/) and create or select a project.
2. Enable **YouTube Data API v3** for it.
3. Go to **Credentials > Create credentials > API key**. Restrict the key to YouTube Data API v3 and leave application restrictions on **None**.
4. Use your channel **@handle** (shown on your channel page and in YouTube Studio) as the channel.

One refresh uses about 3 quota units; the free daily quota is 10,000.

### Facebook Page (free)
1. Create an app at [Meta for Developers](https://developers.facebook.com/apps/) using the **Manage everything on your Page** use case.
2. Open the [Graph API Explorer](https://developers.facebook.com/tools/explorer/), pick your app, choose **User Token**, add `pages_show_list`, `pages_read_engagement` (and `business_management` if your Page belongs to a Business portfolio), then **Generate Access Token** and approve your Page.
3. Extend the token in the [Access Token Debugger](https://developers.facebook.com/tools/debug/accesstoken/) with **Extend Access Token**.
4. Paste the long-lived token into the Explorer and run:
   ```
   me/accounts?fields=id,name,access_token
   ```
5. In the result, the Page `id` is your **Page ID** and its `access_token` is your **Page token**. Page tokens made from a long-lived user token do not expire under normal use.

Never commit or share these values. They exist only in the app on your device.

---

## Configuration

- **Channel:** `@handle`, channel ID (`UC...`) or a channel URL.
- **Refresh interval:** 15 minutes, set in `Scheduler.kt`. Android does not allow background work more often than this.
- **Graph API version:** set by `FB_VERSION` in `Repo.kt`.
- **Colors and shapes:** the app's light and dark palettes are in `Theme.kt`. The widgets' gradient backgrounds live in `res/drawable/` (`widget_bg.xml`, `card_yt.xml`, `card_fb.xml`, `btn_refresh.xml`) and their text colors are at the top of `StatsWidget.kt`.
- **How many posts are compared:** `AVG_WINDOW` in `Insights.kt` (10). The "early" window is `EARLY_MS` (6 hours).

---

## Permissions & Privacy

| Permission | Why |
|---|---|
| `INTERNET` | Call the YouTube and Facebook APIs |
| `ACCESS_NETWORK_STATE` | Let WorkManager run refreshes only when online |
| `POST_NOTIFICATIONS` | Only for the optional alerts and reminders. Android asks the first time you turn one on |
| `RECEIVE_BOOT_COMPLETED` | Re-arm the posting reminder, and the scheduled refresh, after the phone restarts |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | App lock (fingerprint, face or screen lock) |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | Added by WorkManager so the scheduled refresh runs reliably |

- No ads, no analytics, no crash-reporting SDK, no backend.
- The API key and Page token are **encrypted** (AES-256-GCM, with a key held in the Android Keystore) before they are saved. Older plain-text values are migrated automatically on first launch. The channel, Page ID and cached numbers are stored in the app's private storage.
- Saved keys are **write-only**: the app never puts them back on screen, so they cannot be revealed or copied. To change one, tap **Replace**.
- The app is protected by the device lock (fingerprint, face, PIN, pattern or password) when **App lock** is on, which is the default. It opens without asking when you have not saved any keys yet, when the phone has no screen lock, or when you switch App lock off in **Security**.
- Android backup is disabled (`allowBackup="false"`), so keys are not copied to cloud backups.
- Requests go straight from your device to Google and Meta.

---

## Known Limitations

- **YouTube subscriber counts** are rounded and slightly delayed by YouTube itself, so small channels may lag by a few minutes.
- **Facebook last-video views and comments** depend on Meta permissions for your Page. If Meta blocks them, followers still work and the video numbers show `—`.
- **Facebook tokens** can be invalidated, for example after a password change or if the app is removed from your account. The app shows a warning and a renewal guide.
- **Refresh interval** cannot go below 15 minutes because of Android background limits. Use the Refresh button for an instant update.
- The cards show the **latest** post; the insights compare it with your last 10 (and look at up to 25 videos or about 20 reels), not your whole history.
- **Speed** needs two readings at least 30 minutes apart, so it shows "Measuring…" for the first half hour. **Against your usual** needs 3 older posts, and **Best time to post** needs 6 posts over a day old.
- **Posting reminders** use a battery-friendly alarm, so they can arrive a few minutes late while the phone is saving battery. The notification says how long is really left.
- **Alerts** other than the reminder are checked on each refresh, so they are about 15 minutes behind at worst.
- The **home-screen widgets** keep their dark look in every theme.
- Keys are encrypted at rest with a key held in the Android Keystore, and the app lock plus write-only fields protect against someone using your unlocked phone. A rooted or compromised phone, or a debuggable build, is outside what the app can protect against.

---

## Project Structure

```
Social-Stats-Widget/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/abir/socialwidget/
│       │   │   ├── MainActivity.kt   (app screen, lock, Help guides)
│       │   │   ├── InsightsUi.kt     (creator insights + trend)
│       │   │   ├── ReminderUi.kt     (posting-reminder settings)
│       │   │   ├── Theme.kt          (light / dark palettes)
│       │   │   ├── StatsWidget.kt    (4x2 Glance widget + receiver)
│       │   │   ├── CompactWidget.kt  (2x1 Glance widget)
│       │   │   ├── Repo.kt           (YouTube + Facebook API calls)
│       │   │   ├── Latest.kt         (which post is the latest)
│       │   │   ├── Insights.kt       (usual, speed, best time, ...)
│       │   │   ├── Schedule.kt       (next posting reminder)
│       │   │   ├── Reminders.kt      (reminder alarm + receiver)
│       │   │   ├── Notifier.kt       (alerts)
│       │   │   ├── History.kt        (daily history for growth + trend)
│       │   │   ├── Display.kt        (what the cards show)
│       │   │   ├── Errors.kt         (plain-language errors)
│       │   │   ├── Store.kt          (settings and cached stats)
│       │   │   ├── Vault.kt          (Keystore encryption for the keys)
│       │   │   └── Scheduler.kt      (WorkManager scheduling)
│       │   └── res/
│       │       ├── drawable/         (icons, gradient backgrounds)
│       │       ├── xml/              (widget info)
│       │       └── values/
│       └── test/java/com/abir/socialwidget/
│           ├── LatestTest.kt
│           ├── InsightsTest.kt
│           ├── ScheduleTest.kt
│           └── ErrorsTest.kt
├── docs/screenshots/                 (README images)
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Roadmap

- [x] Growth indicators: today's change in subscribers and followers
- [x] Trend line for the last 7 days
- [x] Compact 2x1 widget
- [ ] Large 4x4 widget with a chart
- [x] Optional milestone and "video taking off" notifications
- [x] New-comment and connection-problem alerts
- [x] Posting-time reminders you set yourself
- [x] "Against your usual", speed, engagement, best post and best time to post
- [x] Light, dark and system themes, and an App lock switch
- [x] Choose which metrics to show per card
- [ ] A light theme for the home-screen widgets
- [ ] More platforms (Instagram, TikTok)
- [ ] Guided in-app token renewal
- [ ] Play Store release with a signed bundle

Contributions and issue reports are welcome.

---

## Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit: `git commit -m "Add your feature"`
4. Push and open a Pull Request

Please never include real API keys or tokens in code, issues or screenshots, and include a screenshot for UI changes.

---

## License

This project is currently **All Rights Reserved** — source available for learning and review.

If you want to reuse code or contribute a licensed fork, please contact the author. A formal open-source license (for example MIT) can be added on request.

---

## Author & Contact

**Abir Hasan (InfinityAbir)** — ASP.NET Core / Android developer, building clean, secure, real-world apps.

- GitHub: https://github.com/InfinityAbir
- Portfolio: https://infinityabir.github.io/abir-hasan-portfolio/
- LinkedIn: https://www.linkedin.com/in/infinityabirhasan/

> If this widget saves you a few taps a day, please ⭐ star the repo and share feedback via Issues.
