#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 sync_log 表"""
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

# 登录
resp = requests.post(f"{BASE_URL}/login", json={"username": "admin", "password": "admin123"})
token = resp.json()["token"]
print(f"Token: {token[:20]}...")

# 查询 sync_log 表（如果有的话）
# 直接查询数据库可能更简单，但我们没有直接数据库连接
# 让我用一个更简单的方法：查看写入响应中的 syncLogId

# 写入一条新记录，看看 syncLogId
headers = {"Authorization": f"Bearer {token}"}
write_data = {
    "sheetName": "commercial_hub",
    "records": [
        {
            "values": {
                "record_type": [{"type": "text", "text": "TEST_VERIFY"}],
                "user_id": [{"type": "text", "text": "12345"}],  # 改成文本格式
                "event": [{"type": "text", "text": "VERIFY"}],
                "delta": 777,
                "genre": [{"type": "text", "text": "VERIFY_TEST"}]
            }
        }
    ]
}

print(f"\n写入测试记录...")
resp = requests.post(f"{BASE_URL}/fbs/business/wecom/sync/write", 
                     json=write_data, headers=headers)
print(f"响应: {json.dumps(resp.json(), ensure_ascii=False, indent=2)}")

# 等待一下再查询
import time
time.sleep(2)

# 再次查询智能表格
import subprocess
cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "uvREDw"

params = {"url": url, "sheet_id": sheet_id}
result = subprocess.run(
    [cli_path, "doc", "smartsheet_get_records", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)

print(f"\n=== 查询记录 ===")
data = json.loads(result.stdout)
inner = json.loads(data['content'][0]['text'])
records = inner.get('records', [])
print(f"记录总数: {len(records)}")

# 查找包含 VERIFY 的记录
for i, rec in enumerate(records):
    values = rec.get('values', {})
    record_type_val = values.get('record_type', [])
    if record_type_val and isinstance(record_type_val, list):
        text_val = record_type_val[0].get('text', '') if record_type_val else ''
        if 'VERIFY' in text_val or 'TEST' in text_val:
            print(f"\n找到测试记录 {i}:")
            for k, v in values.items():
                if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                    text_val = v[0].get('text', '')
                    if text_val:
                        print(f"  {k}: {text_val}")
                elif v is not None and str(v).strip():
                    print(f"  {k}: {v}")
