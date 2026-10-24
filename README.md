# Soft Dial (starter v20)

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

## v12
- Full Bangla translation of all screens (Settings -> Language). Strings live in
  res/values/strings.xml (English) and res/values-bn/strings.xml (Bangla).
- Contacts and Recents are cards with an avatar, a call button and ripple.
- Tap a row = Details. Long press = Copy number / Add or Edit contact / Block (Recents) / Delete / Details.

## v13
- Contacts: star = favorite (tap the star or long press). Favorites are listed first under "Favorites".
- Recents: grouped by Today / Yesterday / date.
- Top bar (right side): default SIP account name (or user id) and Online / Offline on two lines.
- Accounts screen and Settings -> Account show registration: Connected / Failed / Connecting.

## v14
- Modern call screen: big avatar with pulsing ring while ringing, name + number, live call timer,
  round control buttons (Mute, Keypad, Speaker, Record, Hold, Add call / Swap) that light up when on,
  round red End button, green Answer button for incoming calls, call-waiting banner on top.

## v15
- After a call ends the call screen stays for ~2 seconds with "Call ended - 02:34"
  (or "Call failed"), then returns to the dialer. Unanswered incoming calls skip it.
- The Speaker button became an audio-output button: it shows the current output and,
  when Bluetooth or a headset is connected, opens a chooser (earpiece / speaker / bluetooth / headset).
  Bluetooth needs the Bluetooth permission on Android 12+ (asked at start, also in Settings -> Permissions).

## v16
- Call screen hides the top bar (it comes back in the dialer).
- In-call Keypad opens in its own compact mode (round controls and avatar make room), with "Hide keypad".
- Dialer: no name under the typed number; matching numbers are cards.

## v17
- Top bar account name + Online/Offline only on the home (dialer) page.
- Long press on dialer suggestion cards (contacts / recent numbers): Copy, Add/Edit contact, Delete, Details.
  (Recents and Contacts pages already had this.)

## v18
- Call times use AM/PM (e.g. 3:05 PM).
- Settings is now a grid of tiles (Account, Theme, Language, Call recording, Call forwarding,
  Number block, Permissions, Backup & restore, About), each with a live status line.
  Tap a tile to open that section (SettingsDetailActivity, layouts res/layout/settings_*.xml).

## v19
- Settings tiles: Ringtone (pick any ringtone / silent, vibration, test), App logs (view, copy, share, clear;
  numbers partly hidden), Help (FAQ in English / Bangla).
- Splash screen (core-splashscreen): app icon on the app color. Colors: ic_launcher_background.

## v20
- First run: SIP login page (server, username, password, advanced: label + transport). Shows
  Connecting / Connected / the server's error. "Skip for now" hides it (Settings -> Account to add later).
- Dial pad keys play a DTMF tone and a short vibration (switches in Settings -> Ringtone & sound).
