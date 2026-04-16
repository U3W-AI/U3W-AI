#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 commercial_hub 字段定义"""
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "uvREDw"

params = {"url": url, "sheet_id": sheet_id}
result = subprocess.run(
    [cli_path, "doc", "smartsheet_get_fields", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)

print("=== commercial_hub 字段定义 ===")
try:
    output = result.stdout.strip()
    if output.startswith('{'):
        data = json.loads(output)
        if 'content' in data:
            inner = json.loads(data['content'][0]['text'])
            fields = inner.get('fields', [])
            print(f"字段数: {len(fields)}\n")
            for f in fields:
                print(f"  - {f['field_title']} ({f['field_type']})")
except Exception as e:
    print(f"解析错误: {e}")
    print(f"原始输出: {result.stdout[:1000]}")
