#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 commercial_hub 最新记录"""
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

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

print("=== commercial_hub 最新记录 ===")
try:
    output = result.stdout.strip()
    if output.startswith('{'):
        data = json.loads(output)
        if 'content' in data:
            inner = json.loads(data['content'][0]['text'])
            records = inner.get('records', [])
            print(f"记录总数: {len(records)}\n")

            # 显示最后 3 条记录
            for i, rec in enumerate(records[-3:], start=max(1, len(records)-2)):
                print(f"--- 记录 {i} ---")
                values = rec.get('values', {})
                for k, v in values.items():
                    # 提取文本值
                    if isinstance(v, list) and len(v) > 0 and isinstance(v[0], dict):
                        text_val = v[0].get('text', '')
                        if text_val:
                            print(f"  {k}: {text_val}")
                    elif v is not None and str(v).strip():
                        print(f"  {k}: {v}")
except Exception as e:
    print(f"解析错误: {e}")
    print(f"原始输出: {result.stdout[:500]}")
