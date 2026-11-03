# Router Credits (Android)

How much data is left on the Telemach SIM in the TP-Link TL-MR110-Outdoor (http://192.168.1.1), checked from
the phone that is on the router's Wi-Fi. The Android twin of [router-sms](https://github.com/markoboskoauroville/router-sms)
(the Mac's `router-sms balance`): the same steps, selectors and parsing.

**Check now** → the app's own browser (Android WebView, built in, nothing extra to install) opens the router's
page, logs in with the password from Settings (answers "force the other device to log off" with yes), Advanced → SMS → New Message, sends
**STANJE** to **13880**, reloads the Inbox every 3 s for up to 60 s, opens Telemach's reply and parses
`… iskoristili ste USED od ukupno TOTAL MB …`. Left = total − used. Only a reply whose own time is not earlier
than the STANJE counts.

- The last status stays on the screen; every check is a line under **Checks**, every step a line under **Log**.
- The log files: `Android/data/com.mantra.routercredits/files/router-credits.log` and `checks.jsonl`.
- **Settings** (the gear, top right): the router password, the phone number the result goes to, SMS on/off, show the
  browser. They stay on the phone only; nothing personal is in this repository. Until a password is saved, Check now
  opens Settings.
- After a good check the **router's own SIM** texts "Router: N GB left …" to that number (through the router's SMS
  page, never from the phone).
- **Read the router's inbox into the log**: every SMS on the router, full text.
- **Show the browser while it works**: the router's page is shown and typed into at a hand's pace.
- The router allows one logged-in device: a check here logs the Mac's router tab off, and the other way round.

Built by GitHub Actions on every push to main; the APK is attached to the release `v<mantraVersion>`.
The signing key is in the repository's secrets, never in the tree.
