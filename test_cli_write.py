#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""直接用 CLI 测试写入"""
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "uvREDw"

# 构建写入参数
params = {
    "url": url,
    "sheet_id": sheet_id,
    "records": [
        {
            "values": {
                "record_type": [{"type": "text", "text": "CLI_DIRECT_TEST"}],
                "user_id": [{"type": "text", "text": "CLI_USER"}],
                "event": [{"type": "text", "text": "CLI_WRITE"}],
                "delta": 999,
                "genre": [{"type": "text", "text": "CLI_TEST"}]
            }
        }
    ]
}

print("=== 直接用 CLI 写入 ===")
print(f"参数: {json.dumps(params, ensure_ascii=False, indent=2)}")

result = subprocess.run(
    [cli_path, "doc", "smartsheet_add_records", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)

print(f"\n响应: {result.stdout}")
print(f"\n错误: {result.stderr}")

# 等待后查询
import time
time.sleep(2)

# 查询记录
print("\n=== 查询记录 ===")
params2 = {"url": url, "sheet_id": sheet_id}
result2 = subprocess.run(
    [cli_path, "doc", "smartsheet_get_records", json.dumps(params2)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)

data = json.loads(result2.stdout)
inner = json.loads(data['content'][0]['text'])
records = inner.get('records', [])
print(f"记录总数: {len(records)}")

# 查找 CLI 测试记录
for i, rec in enumerate(records[-5:]):
    values = rec.get('values', {})
    record_type_val = values.get('record_type', [])
    if record_type_val and isinstance(record_type_val, list):
        text_val = record_type_val[0].get('text', '') if record_type_val else ''
        print(f"记录 {len(records)-5+i}: record_type = {text_val}")
