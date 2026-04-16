# OpenSpec #8 Integration Test Report (Round 2)

- **Date**: 2026-04-15 15:57:12
- **Base URL**: http://localhost:8080
- **Jar**: WxFbsir-admin.jar (15:45:59 rebuild, with @Order fix)
- **DocId**: doXLPDKGTwo9vSXwD2ckpYqicbA
- **Account**: admin / admin123

---

## Results Summary

| Metric | Count |
|--------|-------|
| PASS | 3 |
| FAIL | 11 |
| WARN | 1 |
| SKIP | 3 |
| **Total** | **18** |

---

## Detail

| Test | ID | Status | Detail |
|------|----|--------|--------|
| JWT Login | A0 | PASS | token=eyJhbGciOiJIUzUxMiJ9... |
| Auth 401 | A1 | PASS | HTTP 200 + code=401 |
| getSheets valid | A2 | PASS | HTTP 200 sheets=0 |
| getSheets short docid | E1 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/getSheets.", "code": 500} |
| getSheetDetail invalid sheetId | E2 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/getSheetDetail.", "code": 500} |
| addSheet null body | E3 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/addSheet.", "code": 500} |
| addSheet empty title | E4 | FAIL | HTTP 200 (expected 400) |
| updateSheet null body | E5 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/updateSheet.", "code": 500} |
| deleteSheet null body | E6 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/deleteSheet.", "code": 500} |
| addField null body | B1 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/addField.", "code": 500} |
| updateField null body | B2 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/updateField.", "code": 500} |
| deleteField null body | B3 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/schema/deleteField.", "code": 500} |
| updateRecords null body | C1 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/sync/updateRecords.", "code": 500} |
| deleteRecords null body | C2 | FAIL | HTTP 200 (expected 400) body={"msg": "No static resource business/wecom/sync/deleteRecords.", "code": 500} |
| addSheet functional | D1 | WARN | HTTP 200 sheetId=null data={"msg": "No static resource business/wecom/schema/addSheet.", "code": 500} |
| addField functional | D2 | SKIP | No sheetId from D1 |
| getSheetDetail | D3 | SKIP | No sheetId from D1 |
| deleteSheet cleanup | D4 | SKIP | No sheetId from D1 |

---
**11 failure(s) need attention.**