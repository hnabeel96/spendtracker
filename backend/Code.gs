/**
 * Spend Tracker — Google Sheet backend (Apps Script Web App)
 *
 * Setup (one time):
 *   1. Create a Google Sheet. Extensions → Apps Script. Paste this whole file.
 *   2. Change TOKEN below to a long random string (the app sends it with every request).
 *   3. Deploy → New deployment → type "Web app"
 *        Execute as: Me      Who has access: Anyone
 *      Copy the Web app URL (ends in /exec).
 *   4. In the app: Settings → paste URL + token → Test connection.
 *
 * After editing this file you must Deploy → Manage deployments → Edit → Version: New version,
 * otherwise the /exec URL keeps serving the old code.
 */

const TOKEN = 'CHANGE-ME-to-a-long-random-string';
const SHEET_NAME = 'Transactions';

// Column order. Add new columns at the END so older rows stay aligned.
// "category" is intentionally left blank by the app — fill it in later.
const HEADERS = [
  'id', 'ts', 'amount', 'type', 'category', 'merchant', 'account',
  'ref', 'note', 'mode', 'paid_by', 'source', 'sms', 'synced_at'
];

function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);
    if (body.token !== TOKEN) return json_({ ok: false, error: 'unauthorized' });

    const txs = Array.isArray(body.txs) ? body.txs : [];
    const deletes = Array.isArray(body.deletes) ? body.deletes : [];
    const lock = LockService.getScriptLock();
    lock.waitLock(20000);
    try {
      const sheet = sheet_();
      const now = new Date();
      const accepted = [];
      let added = 0, updated = 0, deleted = 0;

      // 1. Edits to rows that already exist (op: "update"); everything else is an insert.
      let index = rowIndex_(sheet);
      const inserts = [];
      txs.forEach(function (t) {
        if (!t || !t.id) return;
        accepted.push(t.id);   // duplicates count as accepted so the phone marks them synced
        const row = index.get(String(t.id));
        if (row) {
          if (t.op === 'update') {
            sheet.getRange(row, 1, 1, HEADERS.length).setValues([toRow_(t, now)]);
            updated++;
          }
          return;
        }
        index.set(String(t.id), -1);
        inserts.push(toRow_(t, now));
      });
      if (inserts.length) {
        sheet.getRange(sheet.getLastRow() + 1, 1, inserts.length, HEADERS.length).setValues(inserts);
        added = inserts.length;
      }

      // 2. Deletes, bottom-up so row numbers stay valid.
      if (deletes.length) {
        index = rowIndex_(sheet);
        deletes.map(function (id) { return index.get(String(id)); })
          .filter(function (r) { return r > 1; })
          .sort(function (a, b) { return b - a; })
          .forEach(function (r) { sheet.deleteRow(r); deleted++; });
      }
      return json_({ ok: true, accepted: accepted, deleted_ids: deletes, added: added, updated: updated, deleted: deleted });
    } finally {
      lock.releaseLock();
    }
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function toRow_(t, now) {
  return HEADERS.map(function (h) {
    if (h === 'ts') return t.ts ? new Date(t.ts) : now;
    if (h === 'synced_at') return now;
    const v = t[h];
    return v === undefined || v === null ? '' : v;
  });
}

function doGet(e) {
  const p = (e && e.parameter) || {};
  if (p.token !== TOKEN) return json_({ ok: false, error: 'unauthorized' });

  const sheet = sheet_();
  if (p.action === 'list') {
    const limit = Math.min(Number(p.limit) || 200, 1000);
    const last = sheet.getLastRow();
    if (last < 2) return json_({ ok: true, txs: [] });
    const start = Math.max(2, last - limit + 1);
    const values = sheet.getRange(start, 1, last - start + 1, HEADERS.length).getValues();
    const txs = values.map(function (r) {
      const o = {};
      HEADERS.forEach(function (h, i) { o[h] = r[i] instanceof Date ? r[i].getTime() : r[i]; });
      return o;
    });
    return json_({ ok: true, txs: txs });
  }
  // default: ping
  return json_({ ok: true, sheet: SpreadsheetApp.getActive().getName(), rows: Math.max(0, sheet.getLastRow() - 1) });
}

function sheet_() {
  const ss = SpreadsheetApp.getActive();
  let sh = ss.getSheetByName(SHEET_NAME);
  if (!sh) sh = ss.insertSheet(SHEET_NAME);
  if (sh.getLastRow() === 0) {
    sh.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]).setFontWeight('bold');
    sh.setFrozenRows(1);
    sh.getRange('B:B').setNumberFormat('yyyy-mm-dd hh:mm');
    sh.getRange('C:C').setNumberFormat('#,##0.00');
  }
  return sh;
}

function rowIndex_(sheet) {
  const map = new Map();
  const last = sheet.getLastRow();
  if (last < 2) return map;
  sheet.getRange(2, 1, last - 1, 1).getValues().forEach(function (r, i) { map.set(String(r[0]), i + 2); });
  return map;
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** Run once from the editor to create the tab and check permissions. */
function setup() {
  sheet_();
}
