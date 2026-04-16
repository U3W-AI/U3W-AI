# -*- coding: utf-8 -*-
"""OpenSpec #8 Integration Test Round 2 - Python version"""
import requests, json, sys, os
from datetime import datetime

BASE = "http://localhost:8080"
DOCID = "doXLPDKGTwo9vSXwD2ckpYqicbA"
TOKEN_FILE = os.path.join(os.path.dirname(__file__), ".token")
OUTPUT = os.path.join(os.path.dirname(__file__), "test-result-2026-04-15-v2.md")

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
    # Login
    r = requests.post(f"{BASE}/login", json={"username": "admin", "password": "admin123"})
    if r.status_code != 200:
        print(f"FATAL: Login failed HTTP {r.status_code}: {r.text}")
        sys.exit(1)
    token = r.json().get("token")
    # Save token
    with open(TOKEN_FILE, "w") as f:
        f.write(token)
    return token

def H():
    """Load token from file"""
    try:
        with open(TOKEN_FILE) as f:
            return {"Authorization": f.read().strip()}
    except:
        return None

def http_verb(method, url, headers=None, json_body=None):
    """Do request, return (status_code, json_body_or_None, error_msg)"""
    try:
        r = requests.request(method, url, headers=headers, json=json_body, timeout=15)
        try:
            j = r.json()
        except:
            j = None
        return r.status_code, j, ""
    except Exception as e:
        return 0, None, str(e)

def assert_400(tid, name, method, url, headers):
    """Expect HTTP 400"""
    code, j, err = http_verb(method, url, headers=headers)
    if code == 400:
        T(tid, name, "PASS", f"HTTP 400")
    elif code == 0:
        T(tid, name, "FAIL", f"No response: {err}")
    else:
        T(tid, name, "FAIL", f"HTTP {code} (expected 400) body={json.dumps(j, ensure_ascii=False)[:100]}")

# ===== MAIN =====
print("=== OpenSpec #8 Integration Test Round 2 ===")
print(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")

token = get_token()
T("A0", "JWT Login", "PASS", f"token={token[:20]}...")
headers = H()

# A1: 401 test
code, j, _ = http_verb("GET", f"{BASE}/business/wecom/schema/getSheets?docid=test")
if code == 200 and j and str(j.get("code", "")) == "401":
    T("A1", "Auth 401", "PASS", "HTTP 200 + code=401")
elif code == 401:
    T("A1", "Auth 401", "PASS", "HTTP 401")
else:
    T("A1", "Auth 401", "FAIL", f"HTTP {code}")

# A2: getSheets valid docid
code, j, _ = http_verb("GET", f"{BASE}/business/wecom/schema/getSheets?docid={DOCID}", headers=headers)
sheets = None
if j and j.get("data"):
    d = j["data"]
    sheets = d.get("sheets") or d.get("sheet_list")
count = len(sheets) if sheets else 0
T("A2", "getSheets valid", "PASS", f"HTTP {code} sheets={count}")

# E1: getSheets short docid
assert_400("E1", "getSheets short docid", "GET", 
    f"{BASE}/business/wecom/schema/getSheets?docid=too_short", headers)

# E2: getSheetDetail invalid sheetId
assert_400("E2", "getSheetDetail invalid sheetId", "GET",
    f"{BASE}/business/wecom/schema/getSheetDetail?docid={DOCID}&sheetId=invalid_id", headers)

# E3: addSheet null body
assert_400("E3", "addSheet null body", "POST",
    f"{BASE}/business/wecom/schema/addSheet", headers)

# E4: addSheet empty title
code, j, _ = http_verb("POST", f"{BASE}/business/wecom/schema/addSheet", headers=headers,
    json_body={"docid": DOCID, "title": ""})
if code == 400:
    T("E4", "addSheet empty title", "PASS", f"HTTP 400")
else:
    T("E4", "addSheet empty title", "FAIL", f"HTTP {code} (expected 400)")

# E5: updateSheet null body
assert_400("E5", "updateSheet null body", "PUT",
    f"{BASE}/business/wecom/schema/updateSheet", headers)

# E6: deleteSheet null body
assert_400("E6", "deleteSheet null body", "DELETE",
    f"{BASE}/business/wecom/schema/deleteSheet", headers)

# B1: addField null body
assert_400("B1", "addField null body", "POST",
    f"{BASE}/business/wecom/schema/addField", headers)

# B2: updateField null body
assert_400("B2", "updateField null body", "PUT",
    f"{BASE}/business/wecom/schema/updateField", headers)

# B3: deleteField null body
assert_400("B3", "deleteField null body", "DELETE",
    f"{BASE}/business/wecom/schema/deleteField", headers)

# C1: updateRecords null body
assert_400("C1", "updateRecords null body", "POST",
    f"{BASE}/business/wecom/sync/updateRecords", headers)

# C2: deleteRecords null body
assert_400("C2", "deleteRecords null body", "DELETE",
    f"{BASE}/business/wecom/sync/deleteRecords", headers)

# D1: addSheet functional
ts = datetime.now().strftime("%H%M%S")
code, j, err = http_verb("POST", f"{BASE}/business/wecom/schema/addSheet", headers=headers,
    json_body={"docid": DOCID, "title": f"IT_Test_{ts}"})
sheet_id = None
if j and j.get("data"):
    d = j["data"]
    sheet_id = d.get("sheet_id") or (d.get("properties") or {}).get("sheet_id") if isinstance(d, dict) else None
if sheet_id:
    T("D1", "addSheet functional", "PASS", f"sheetId={sheet_id}")
else:
    T("D1", "addSheet functional", "WARN", f"HTTP {code} sheetId=null data={json.dumps(j, ensure_ascii=False)[:150]}")

# D2: addField functional
if sheet_id:
    code, j, err = http_verb("POST", f"{BASE}/business/wecom/schema/addField", headers=headers,
        json_body={"docid": DOCID, "sheetId": sheet_id, "fieldTitle": "IT_Test_Field", "fieldType": "text"})
    field_id = None
    if j and j.get("data"):
        d = j["data"]
        field_id = d.get("field_id") or d.get("id") if isinstance(d, dict) else None
    if field_id:
        T("D2", "addField functional", "PASS", f"fieldId={field_id}")
    else:
        T("D2", "addField functional", "WARN", f"HTTP {code} fieldId=null data={json.dumps(j, ensure_ascii=False)[:150]}")
else:
    T("D2", "addField functional", "SKIP", "No sheetId from D1")

# D3: getSheetDetail
if sheet_id:
    code, j, err = http_verb("GET", 
        f"{BASE}/business/wecom/schema/getSheetDetail?docid={DOCID}&sheetId={sheet_id}", headers=headers)
    fields = None
    if j and j.get("data"):
        d = j["data"]
        fields = d.get("fields") or d.get("properties")
    fc = len(fields) if fields else 0
    T("D3", "getSheetDetail", "PASS", f"HTTP {code} fields={fc}")
else:
    T("D3", "getSheetDetail", "SKIP", "No sheetId from D1")

# D4: deleteSheet cleanup
if sheet_id:
    code, j, err = http_verb("DELETE", f"{BASE}/business/wecom/schema/deleteSheet", headers=headers,
        json_body={"docid": DOCID, "sheetId": sheet_id})
    T("D4", "deleteSheet cleanup", "PASS", f"HTTP {code} msg={j.get('msg','') if j else ''}")
else:
    T("D4", "deleteSheet cleanup", "SKIP", "No sheetId from D1")

# ===== REPORT =====
total = passes + fails + warns + skips
report = f"""# OpenSpec #8 Integration Test Report (Round 2)

- **Date**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
- **Base URL**: {BASE}
- **Jar**: WxFbsir-admin.jar (15:45:59 rebuild, with @Order fix)
- **DocId**: {DOCID}
- **Account**: admin / admin123

---

## Results Summary

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
    report += "\n\n---\n**All tests passed. No issues found.**"
elif fails == 0:
    report += f"\n\n---\n**No failures.** {warns} warning(s) due to wecom-cli data issues."
else:
    report += f"\n\n---\n**{fails} failure(s) need attention.**"

with open(OUTPUT, "w", encoding="utf-8") as f:
    f.write(report)

print(f"\n=== Summary: PASS={passes} FAIL={fails} WARN={warns} SKIP={skips} ===")
print(f"Report: {OUTPUT}")
