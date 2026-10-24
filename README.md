<div align="center">

# Soft Dial

**A modern, lightweight SIP softphone for Android — built with Kotlin and the Linphone SDK.**

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![SIP](https://img.shields.io/badge/SIP-Linphone%20SDK-0A84FF)](https://gitlab.linphone.org/BC/public/linphone-sdk)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

[Download](../../releases/latest) · [Features](#features) · [Build](#building-from-source) · [Release](#releasing) · [License](#license)

</div>

---

## Overview

Soft Dial turns your phone into a full-featured SIP client. Sign in with any SIP account, make and receive calls over Wi-Fi or mobile data, record conversations, manage contacts and call history, and keep everything backed up — all in a clean, fast interface with light and dark themes and full **English / বাংলা** support.

## Features

### Calling
- Dial pad with round keys, DTMF tones and haptic feedback
- Modern in-call screen: mute, keypad (DTMF), audio output, record, hold, add call / swap
- Call waiting, hold & answer, and swap between two calls
- Audio route chooser: earpiece, speaker, Bluetooth, wired headset
- Incoming calls in the background through a foreground service
- Optional FCM push for incoming calls (requires a push-capable SIP server)

### Accounts
- First-run SIP login (server, username, password, transport)
- Multiple SIP accounts with a selectable default for outgoing calls
- Live registration status: Connecting / Connected / Failed

### Contacts & history
- Local contacts with favorites, search and one-time phone import
- Recents grouped by Today / Yesterday / date, with missed-call badge and notifications
- Smart dialer suggestions (matches numbers and contact names while typing)
- Long-press actions: copy, add / edit contact, block, delete, details

### Control & privacy
- Call recording (manual or automatic) with a built-in Recordings screen and sharing
- Do Not Disturb (with optional contacts exception) and call forwarding (always / busy / no answer)
- Number blocking
- Encrypted backup & restore of settings, accounts and contacts (passphrase protected)
- In-app logs with number masking, copy and share

### Experience
- Light, dark and system themes
- English and Bangla (বাংলা) interface
- Custom ringtone, vibration and key-sound settings
- Splash screen and Material-style bottom navigation

## Requirements

| Item | Version |
|------|---------|
| Android | 8.0 (API 26) or newer |
| Target / compile SDK | 34 |
| JDK (build) | 17 |
| Gradle | 8.7 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 |

## Getting started

1. Download the latest APK from the [Releases](../../releases/latest) page and install it.
2. Open the app and sign in with your SIP server, username and password (or skip and add an account later in **Settings → Account**).
3. Allow the requested permissions (microphone, notifications, contacts, Bluetooth) for the best experience.
4. For reliable incoming calls, allow background running when asked and open the app once after a reboot.

## Building from source

```bash
git clone https://github.com/<your-username>/SoftDial.git
cd SoftDial
gradle assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/`.

### Build with GitHub Actions

Every push to `main` runs the **Build APK** workflow. Download the `softdial-debug` artifact from the workflow run.

### Optional: FCM push

1. Create a Firebase project and add an Android app with package `com.example.softdial`.
2. Download `google-services.json`.
3. Add the full file contents as the repository secret `GOOGLE_SERVICES_JSON`, then re-run the build.

> Push works only if your SIP server or proxy can send FCM pushes (for example a Flexisip push gateway). Ordinary SIP providers usually cannot. Without it the app works normally.

## Releasing

Signed release APKs are built and published by the **Release APK** workflow.

**1. Add these repository secrets** (*Settings → Secrets and variables → Actions*):

| Secret | Value |
|--------|-------|
| `ANDROID_KEYSTORE_BASE64` | Full contents of your keystore converted to Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Create the Base64 text with `base64 -w 0 release.keystore`.

**2. Update the version** in `app/build.gradle` (`versionCode`, `versionName`).

**3. Publish**

```bash
git tag v20.0
git push origin v20.0
```

You can also run **Actions → Release APK → Run workflow** and enter a tag. The APK is attached to a new GitHub Release automatically.

> Never commit your keystore or passwords to the repository.

## Project structure

```
app/src/main/java/com/example/softdial/
├── MainActivity.kt          # Dialer and in-call screen
├── SipManager.kt            # Linphone core, registration, calls
├── CallService.kt           # Foreground service for background calls
├── Push.kt, SoftFcmService.kt  # Optional FCM push
├── ContactsActivity.kt, HistoryActivity.kt, RecordingsActivity.kt
├── SettingsActivity.kt, SettingsDetailActivity.kt
├── AccountsActivity.kt, LoginActivity.kt, BlockedActivity.kt
├── Backup.kt, Prefs.kt, Store.kt, AppLog.kt
└── ...
```

## Privacy & legal notes

- Your SIP credentials and data stay on your device; Soft Dial does not run any servers of its own.
- Backups contain SIP account passwords — protect them with a passphrase.
- Recording a call may require the other party's consent under your local law. You are responsible for complying with it.

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first to discuss what you would like to change.

## Acknowledgements

- [Linphone SDK](https://gitlab.linphone.org/BC/public/linphone-sdk) by Belledonne Communications — SIP, audio and media engine
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging) — optional push notifications

## License

Copyright © 2026 Md. Masud Rana

Soft Dial is free software released under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for the full text.

The app is built on the Linphone SDK, which is itself licensed under the GPL, so the combined work is distributed under the GPL as well.
