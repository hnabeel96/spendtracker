# Ledger — spending tracker (Android + Google Sheet)

Bank SMS in → parsed on the phone → row in your Google Sheet.

```
Bank SMS ──► SmsReceiver ──► SmsParser ──► Store (phone, instant)
                                              │
                                   SyncWorker (when online, retries)
                                              ▼
                                Apps Script web app ──► Sheet "Transactions"
```

* **Phone is the inbox, Sheet is the ledger.** Everything is saved locally first, so nothing is lost offline.
* The same SMS always gets the same id, so live capture, inbox re-scans and retries never double-log.
* `category` column is left blank on purpose — to be filled later.

## Sheet setup
1. New Google Sheet → **Extensions → Apps Script** → paste [`backend/Code.gs`](backend/Code.gs).
2. Change `TOKEN` to a long random string.
3. **Deploy → New deployment → Web app**, Execute as **Me**, Access **Anyone**. Copy the `/exec` URL.
4. In the app: **Settings** → paste URL + token → **Test connection**.

After editing `Code.gs`: Deploy → Manage deployments → Edit → *New version*.

## Install
Download the latest APK from [Releases](../../releases/latest). Each push to `main` builds a new signed APK that installs as an update.

## Parser
`app/src/main/java/com/eko/ledger/SmsParser.kt`, tested in `app/src/test`. To teach it a new bank format, add the SMS as a test case.
