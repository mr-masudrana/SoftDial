# Soft Dial (starter v11)

SIP dialer using Linphone SDK.

Features: dialpad, in-call screen (mute / speaker / record), background incoming
calls (foreground service), call recording + Recordings screen, call history
(Recents) with missed-call notifications, local contacts, light/dark/system
theme, optional FCM push, phonebook import, in-call keypad (DTMF),
recording sharing.

## Build
Push to GitHub (`main`) -> Actions -> "Build APK" -> download `softdial-debug`.

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
- Backup & restore (Settings): settings + SIP accounts + contacts to a JSON file,
  optionally encrypted with a passphrase. Restore merges contacts.

## v7
- Bottom navigation (Dialer / Recents / Contacts / Settings) with custom vector icons
  in res/drawable/ic_nav_*.xml. Colors: nav_active / nav_inactive in res/values/colors.xml
  (and values-night). The bar is hidden during calls.

## v8
- Selected bottom tab uses a filled icon (ic_nav_*_filled.xml).
- Red badge on Recents shows unseen missed calls (cleared when Recents is opened).
- Contacts: search box (name or number).

## v9
- Dialer: keypad at the bottom; middle area shows recent calls, and matching
  contacts/numbers (also by name letters, e.g. 566 -> Jon) while typing. Tap to fill the number.
- Top bar, status bar and navigation bar use the same color as the bottom nav
  (`bar_bg` in res/values/colors.xml and values-night).

## v10
- No three-dot menus. Recordings and Import contacts are in Settings.
- Recents: "Clear all" in the bar, with a confirmation dialog.
- Contacts permission is requested at app start (like notifications); contacts are
  copied once automatically. Settings -> "Import contacts from phone" repeats it.

## v11
- Round keypad keys with letters (ABC...). Round green call button, custom backspace icon.
- Call button on the right of every row in Recents, Contacts and dialer suggestions.
  (Tapping the row itself still opens options / fills the number.)
