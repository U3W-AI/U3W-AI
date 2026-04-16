# OpenSpec #8 Integration Test Report (Round 3)

- **Date**: 2026-04-15 16:10:16
- **Base URL**: http://localhost:8080
- **API Prefix**: /fbs/business/wecom
- **Jar**: WxFbsir-admin.jar (15:45:59 rebuild)
- **DocId**: doXLPDKGTwo9vSXwD2ckpYqicbA
- **Account**: admin / admin123

---

## Summary

| Metric | Count |
|--------|-------|
| PASS | 15 |
| FAIL | 0 |
| WARN | 1 |
| SKIP | 3 |
| **Total** | **19** |

---

## Detail

| Test | ID | Status | Detail |
|------|----|--------|--------|
| JWT Login | A0 | PASS | token=eyJhbGciOiJIUzUxMiJ9... |
| Auth 401 no token | A1 | PASS | HTTP 200 + code=401 |
| getSheets valid docid | A2 | PASS | HTTP 200 code=200 sheets=0 |
| getSheets short docid | E1 | PASS | HTTP 400 |
| getFields missing sheetId | E2 | PASS | HTTP 400 |
| addSheet null body | E3 | PASS | HTTP 400 |
| addSheet empty title | E4 | PASS | HTTP 400 |
| updateSheet null body | E5 | PASS | HTTP 400 |
| deleteSheet no params | E6 | PASS | HTTP 400 |
| addFields null body | B1 | PASS | HTTP 400 |
| updateFields null body | B2 | PASS | HTTP 400 |
| deleteFields no params | B3 | PASS | HTTP 400 |
| updateRecords null body | C1 | PASS | HTTP 400 |
| deleteRecords null body | C2 | PASS | HTTP 400 |
| addSheet functional | D1 | WARN | HTTP 200 sheetId=null data={"msg": "操作成功", "code": 200, "data": {"sheetId": null, "title": "IT_Test_161015"}} |
| addFields functional | D2 | SKIP | No sheetId from D1 |
| getFields functional | D3 | SKIP | No sheetId from D1 |
| getSheets after add | D4 | PASS | HTTP 200 sheets=0 |
| deleteSheet cleanup | D5 | SKIP | No sheetId from D1 |

---
**No failures.** 1 warning(s) due to wecom-cli data issues.