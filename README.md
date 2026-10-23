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


## GitHub Actions: signed release APK

The workflow always creates a debug APK. It creates a **signed** release APK only when all four GitHub Actions repository secrets below are configured; otherwise it uploads an explicitly named unsigned release artifact.

### 1. Create a release keystore (keep it private)

On a trusted computer with Java installed, run:

```bash
keytool -genkeypair -v -keystore softdial-release.jks -alias softdial -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 softdial-release.jks > softdial-release.jks.base64
```

Keep both the `.jks` and its passwords private and make an offline backup. **Never commit the keystore, its Base64 text, or passwords to Git.** On macOS use `base64 < softdial-release.jks | tr -d '\n' > softdial-release.jks.base64`; on Windows use a trusted Base64 encoder.

### 2. Add repository secrets

In GitHub: repository → **Settings → Secrets and variables → Actions → New repository secret**. Add these four secrets:

- `ANDROID_KEYSTORE_BASE64`: the complete Base64 contents of `softdial-release.jks.base64`
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: `softdial` (or the alias you chose)
- `ANDROID_KEY_PASSWORD`: key password

Do not paste secrets into workflow YAML or chat. If you lose the signing key, future APKs generally cannot update installations signed with the old key. Keep the key and passwords backed up securely.

### 3. Run the workflow

Open **Actions → SoftDial Android CI → Run workflow**. With secrets configured, download `softdial-signed-release-apk` from the run's Artifacts section. Verify that the artifact is the signed release, not `softdial-debug-apk`. The workflow verifies the APK signature before upload.

The keystore is generated and kept in the GitHub runner's temporary directory and is not uploaded as an artifact.
