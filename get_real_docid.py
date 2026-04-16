#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 commercial_hub 的记录"""
import subprocess
import json
import sys

# 强制 UTF-8 输出
sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "uvREDw"  # 正确的 commercial_hub sheet_id

print(f"=== 查询 commercial_hub 记录 ===")
params = {"url": url, "sheet_id": sheet_id}
result = subprocess.run(
    [cli_path, "doc", "smartsheet_get_records", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)

# 解析记录
try:
    output = result.stdout.strip()
    if output.startswith('{'):
        data = json.loads(output)
        if 'content' in data:
            inner = json.loads(data['content'][0]['text'])
            if 'records' in inner:
                print(f"记录数: {len(inner['records'])}")
                for i, rec in enumerate(inner['records'][:5]):  # 显示前5条
                    print(f"\n--- 记录 {i+1} ---")
                    values = rec.get('values', {})
                    for k, v in values.items():
                        # 简化显示
                        if v and str(v).strip():
                            val_str = str(v)[:100] if len(str(v)) > 100 else str(v)
                            print(f"  {k}: {val_str}")
except Exception as e:
    print(f"解析错误: {e}")
    print(f"原始输出: {result.stdout[:1000]}")
