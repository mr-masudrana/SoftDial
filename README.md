# Soft Dial (starter v6)

SIP dialer using Linphone SDK.

Features: dialpad, in-call screen (mute / speaker / record), background incoming
calls (foreground service), call recording + Recordings screen, call history
(Recents) with missed-call notifications, local contacts, light/dark/system
theme, optional FCM push, phonebook import, in-call keypad (DTMF),
recording sharing.

## Build
Push to GitHub (`main`) -> Actions -> "SoftDial Android CI" -> download `softdial-apks`.
The workflow builds a debug APK and an unsigned release APK. The release APK is not
production-distribution-ready until it is signed with your own release key. Do not
commit signing keys or passwords to the repository.

## FCM push (optional)
1. Create a Firebase project, add an Android app with package `com.example.softdial`.
2. Download `google-services.json`.
3. GitHub repo -> Settings -> Secrets -> Actions -> new secret `GOOGLE_SERVICES_JSON`
   with the full file contents. Re-run the build.
4. IMPORTANT: push only works if your SIP server/proxy can send FCM pushes
   (e.g. Flexisip push gateway, or a provider that supports push). Ordinary
   SIP providers will not. Without that, the app works as before.

## Notes
- Open the app once after reboot to restart the background service.
- Recordings: Android/data/com.example.softdial/files/recordings
- Recording calls may need the other person's consent under local law.

## v5 additions
- Call hold/resume, call waiting (Hold & answer / Decline), swap between two calls.
- Multiple SIP accounts: Settings -> Manage SIP accounts. Pick the account for
  outgoing calls from the dialer ("Call with") or the accounts screen.
- App icon: res/drawable/ic_launcher_foreground.xml (shape) and
  res/values/colors.xml (background color).
- App name: res/values/strings.xml (`app_name`); Bangla name in res/values-bn/strings.xml.

## v6 additions
- Add call: puts the running call on hold and dials a second number.
- Do Not Disturb (menu or Settings; optional exception for contacts) and call
  forwarding (always / busy / no answer). Done inside the app, so only while it runs.
- Backup & restore (Settings): settings + SIP accounts + contacts to an encrypted JSON
  file. A passphrase of at least 10 characters is required; unencrypted backup imports
  are rejected. Restore merges accounts and contacts; it does not delete existing data.

## Security and release notes (v6.0)
- SIP account JSON is encrypted at rest with AES-GCM and an Android Keystore key. Existing
  plaintext account JSON is migrated on app startup; legacy single-account password fields
  are cleared after migration.
- SIP SDK debug logging follows `BuildConfig.DEBUG` and is disabled in release builds.
- Android cleartext traffic is not explicitly enabled.
- Backup imports are size-limited and validate account/contact fields before applying data.
- History entries have unique IDs to avoid deleting multiple calls with the same timestamp.
- The Linphone dependency is pinned to a specific version for reproducible dependency resolution.
- A background SIP service cannot guarantee incoming calls after force-stop, on every OEM,
  or without a push-capable SIP provider. Test reboot, Doze, notification permission and
  foreground-service behavior on the target Android versions.
- Run a full device/SIP integration test before production release. This repository update
  has not been compiled or tested against a live SIP server in this environment.
