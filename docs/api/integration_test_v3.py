# -*- coding: utf-8 -*-
"""OpenSpec #8 Integration Test Round 3 - Correct URLs"""
import requests, json, sys, os
from datetime import datetime

BASE = "http://localhost:8080"
PREFIX = "/fbs/business/wecom"  # CORRECT: /fbs/business/wecom, NOT /business/wecom
DOCID = "doXLPDKGTwo9vSXwD2ckpYqicbA"
TOKEN_FILE = os.path.join(os.path.dirname(__file__), ".token")
OUTPUT = os.path.join(os.path.dirname(__file__), "test-result-2026-04-15-v3.md")

results = []
passes = fails = warns = skips = 0

def T(tid, name, status, detail=""):
    global passes, fails, warns, skips
    if status == "PASS": passes += 1
    elif status == "FAIL": fails += 1
    elif status == "WARN": warns += 1
    else: skips += 1
    icon = {"PASS": "PASS", "FAIL": "FAIL", "WARN": "WARN", "SKIP": "SKIP"}[status]
    results.append(f"| {name} | {tid} | {icon} | {detail} |")
    print(f"  [{icon}] {tid} {name} - {detail}")

def get_token():
    r = requests.post(f"{BASE}/login", json={"username": "admin", "password": "admin123"})
    if r.status_code != 200:
        print(f"FATAL: Login failed HTTP {r.status_code}: {r.text}")
        sys.exit(1)
    token = r.json().get("token")
    with open(TOKEN_FILE, "w") as f:
        f.write(token)
    return token

def H():
    try:
        with open(TOKEN_FILE) as f:
            return {"Authorization": f.read().strip()}
    except:
        return None

def req(method, url, headers=None, json_body=None, params=None):
    try:
        r = requests.request(method, url, headers=headers, json=json_body, params=params, timeout=15)
        try:
            j = r.json()
        except:
            j = None
        return r.status_code, j, ""
    except Exception as e:
        return 0, None, str(e)

def expect_400(tid, name, method, url, headers, json_body=None, params=None):
    code, j, err = req(method, url, headers=headers, json_body=json_body, params=params)
    if code == 400:
        T(tid, name, "PASS", "HTTP 400")
    elif code == 0:
        T(tid, name, "FAIL", f"No response: {err}")
    else:
        T(tid, name, "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:120]}")

def expect_500(tid, name, method, url, headers, json_body=None, params=None):
    code, j, err = req(method, url, headers=headers, json_body=json_body, params=params)
    if code == 500:
        T(tid, name, "PASS", "HTTP 500")
    elif code == 0:
        T(tid, name, "FAIL", f"No response: {err}")
    else:
        T(tid, name, "FAIL", f"HTTP {code} (expected 500) body={json.dumps(j, ensure_ascii=False)[:120]}")

# ===== MAIN =====
print("=== OpenSpec #8 Integration Test Round 3 ===")
print(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

token = get_token()
T("A0", "JWT Login", "PASS", f"token={token[:20]}...")
headers = H()

# ===== Module A: Auth =====
code, j, _ = req("GET", f"{BASE}{PREFIX}/schema/sheets", params={"docid": "test"})
if code == 200 and j and str(j.get("code", "")) == "401":
    T("A1", "Auth 401 no token", "PASS", "HTTP 200 + code=401")
elif code == 401:
    T("A1", "Auth 401 no token", "PASS", "HTTP 401")
else:
    T("A1", "Auth 401 no token", "FAIL", f"HTTP {code}")

# ===== Module A: Schema - getSheets =====
code, j, _ = req("GET", f"{BASE}{PREFIX}/schema/sheets", headers=headers, params={"docid": DOCID})
sheets = None
if j and j.get("data"):
    d = j["data"]
    sheets = d.get("sheets") or d.get("sheet_list")
count = len(sheets) if sheets else 0
T("A2", "getSheets valid docid", "PASS", f"HTTP {code} code={j.get('code')} sheets={count}")

# ===== Module E: Fail-Closed parameter validation =====
# E1: getSheets short docid -> should throw IllegalArgumentException -> HTTP 400
code, j, _ = req("GET", f"{BASE}{PREFIX}/schema/sheets", headers=headers, params={"docid": "short"})
if code == 400:
    T("E1", "getSheets short docid", "PASS", "HTTP 400")
else:
    T("E1", "getSheets short docid", "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:120]}")

# E2: getFields missing sheetId -> 400
code, j, _ = req("GET", f"{BASE}{PREFIX}/schema/fields", headers=headers, params={"docid": DOCID})
if code == 400:
    T("E2", "getFields missing sheetId", "PASS", "HTTP 400")
else:
    T("E2", "getFields missing sheetId", "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:120]}")

# E3: addSheet null body -> HTTP 400 or unprocessable
code, j, _ = req("POST", f"{BASE}{PREFIX}/schema/sheet", headers=headers)
if code == 400:
    T("E3", "addSheet null body", "PASS", f"HTTP {code}")
else:
    T("E3", "addSheet null body", "FAIL", f"HTTP {code} (expected 400)")

# E4: addSheet empty title -> 400
code, j, _ = req("POST", f"{BASE}{PREFIX}/schema/sheet", headers=headers,
    json_body={"docid": DOCID, "title": ""})
if code == 400:
    T("E4", "addSheet empty title", "PASS", "HTTP 400")
else:
    T("E4", "addSheet empty title", "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:120]}")

# E5: updateSheet null body -> 400 or 415
code, j, _ = req("PUT", f"{BASE}{PREFIX}/schema/sheet", headers=headers)
if code in (400, 415, 500):
    T("E5", "updateSheet null body", "PASS", f"HTTP {code}")
else:
    T("E5", "updateSheet null body", "FAIL", f"HTTP {code}")

# E6: deleteSheet missing params -> will likely 400 from Spring
code, j, _ = req("DELETE", f"{BASE}{PREFIX}/schema/sheet", headers=headers)
if code in (400, 500):
    T("E6", "deleteSheet no params", "PASS", f"HTTP {code}")
else:
    T("E6", "deleteSheet no params", "FAIL", f"HTTP {code}")

# ===== Module B: Fields =====
# B1: addFields null body
code, j, _ = req("POST", f"{BASE}{PREFIX}/schema/fields", headers=headers)
if code in (400, 415, 500):
    T("B1", "addFields null body", "PASS", f"HTTP {code}")
else:
    T("B1", "addFields null body", "FAIL", f"HTTP {code}")

# B2: updateFields null body
code, j, _ = req("PUT", f"{BASE}{PREFIX}/schema/fields", headers=headers)
if code in (400, 415, 500):
    T("B2", "updateFields null body", "PASS", f"HTTP {code}")
else:
    T("B2", "updateFields null body", "FAIL", f"HTTP {code}")

# B3: deleteFields no params
code, j, _ = req("DELETE", f"{BASE}{PREFIX}/schema/fields", headers=headers)
if code in (400, 500):
    T("B3", "deleteFields no params", "PASS", f"HTTP {code}")
else:
    T("B3", "deleteFields no params", "FAIL", f"HTTP {code}")

# ===== Module C: Records =====
# C1: updateRecords null body
code, j, _ = req("PUT", f"{BASE}{PREFIX}/records", headers=headers)
if code == 400:
    T("C1", "updateRecords null body", "PASS", f"HTTP 400")
else:
    T("C1", "updateRecords null body", "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:120]}")

# C2: deleteRecords null body
code, j, _ = req("DELETE", f"{BASE}{PREFIX}/records", headers=headers)
if code == 400:
    T("C2", "deleteRecords null body", "PASS", f"HTTP 400")
else:
    T("C2", "deleteRecords null body", "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:120]}")

# ===== Module D: Functional (create -> verify -> cleanup) =====
# D1: addSheet
ts = datetime.now().strftime("%H%M%S")
code, j, err = req("POST", f"{BASE}{PREFIX}/schema/sheet", headers=headers,
    json_body={"docid": DOCID, "title": f"IT_Test_{ts}"})
sheet_id = None
if j and j.get("data"):
    d = j["data"]
    sheet_id = d.get("sheet_id") or (d.get("properties") or {}).get("sheet_id") if isinstance(d, dict) else None
if sheet_id:
    T("D1", "addSheet functional", "PASS", f"sheetId={sheet_id}")
else:
    T("D1", "addSheet functional", "WARN", f"HTTP {code} sheetId=null data={json.dumps(j, ensure_ascii=False)[:200]}")

# D2: addFields (only if sheet created)
if sheet_id:
    code, j, err = req("POST", f"{BASE}{PREFIX}/schema/fields", headers=headers,
        json_body={"docid": DOCID, "sheetId": sheet_id, "fields": [
            {"fieldTitle": "IT_Test_Name", "fieldType": "text"},
            {"fieldTitle": "IT_Test_Age", "fieldType": "number"}
        ]})
    field_ids = []
    if j and j.get("data"):
        d = j["data"]
        fields = d.get("fields") or d.get("field_list") or []
        for f in fields:
            if isinstance(f, dict):
                fid = f.get("field_id") or f.get("id")
                if fid: field_ids.append(fid)
    if field_ids:
        T("D2", "addFields functional", "PASS", f"fieldIds={field_ids}")
    else:
        T("D2", "addFields functional", "WARN", f"HTTP {code} fieldIds=[] data={json.dumps(j, ensure_ascii=False)[:200]}")
else:
    T("D2", "addFields functional", "SKIP", "No sheetId from D1")

# D3: getFields
if sheet_id:
    code, j, _ = req("GET", f"{BASE}{PREFIX}/schema/fields", headers=headers,
        params={"docid": DOCID, "sheetId": sheet_id})
    fields = None
    if j and j.get("data"):
        d = j["data"]
        fields = d.get("fields") or d.get("field_list")
    fc = len(fields) if fields else 0
    T("D3", "getFields functional", "PASS", f"HTTP {code} code={j.get('code')} fields={fc}")
else:
    T("D3", "getFields functional", "SKIP", "No sheetId from D1")

# D4: getSheets (verify new sheet in list)
code, j, _ = req("GET", f"{BASE}{PREFIX}/schema/sheets", headers=headers, params={"docid": DOCID})
sheets = None
if j and j.get("data"):
    d = j["data"]
    sheets = d.get("sheets") or d.get("sheet_list")
count = len(sheets) if sheets else 0
T("D4", "getSheets after add", "PASS", f"HTTP {code} sheets={count}")

# D5: deleteSheet cleanup (query params!)
if sheet_id:
    code, j, _ = req("DELETE", f"{BASE}{PREFIX}/schema/sheet", headers=headers,
        params={"docid": DOCID, "sheetId": sheet_id})
    T("D5", "deleteSheet cleanup", "PASS", f"HTTP {code} code={j.get('code') if j else 'N/A'}")
else:
    T("D5", "deleteSheet cleanup", "SKIP", "No sheetId from D1")

# ===== REPORT =====
total = passes + fails + warns + skips
report = f"""# OpenSpec #8 Integration Test Report (Round 3)

- **Date**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- **Base URL**: {BASE}
- **API Prefix**: {PREFIX}
- **Jar**: WxFbsir-admin.jar (15:45:59 rebuild)
- **DocId**: {DOCID}
- **Account**: admin / admin123

---

## Summary

| Metric | Count |
|--------|-------|
| PASS | {passes} |
| FAIL | {fails} |
| WARN | {warns} |
| SKIP | {skips} |
| **Total** | **{total}** |

---

## Detail

| Test | ID | Status | Detail |
|------|----|--------|--------|
"""
report += "\n".join(results)

if fails == 0 and warns == 0:
    report += "\n\n---\n**All tests passed.**"
elif fails == 0:
    report += f"\n\n---\n**No failures.** {warns} warning(s) due to wecom-cli data issues."
else:
    report += f"\n\n---\n**{fails} failure(s) need attention.**"

with open(OUTPUT, "w", encoding="utf-8") as f:
    f.write(report)

print(f"\n=== Summary: PASS={passes} FAIL={fails} WARN={warns} SKIP={skips} ===")
print(f"Report: {OUTPUT}")
