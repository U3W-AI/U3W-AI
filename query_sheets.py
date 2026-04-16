#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 FBSskill后台表格 的所有子表"""
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"

# 获取所有子表
params = {"url": url}
result = subprocess.run(
    [cli_path, "doc", "smartsheet_get_sheet", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)

print("=== FBSskill后台表格 的所有子表 ===")
try:
    output = result.stdout.strip()
    if output.startswith('{'):
        data = json.loads(output)
        if 'content' in data:
            inner = json.loads(data['content'][0]['text'])
            print(json.dumps(inner, indent=2, ensure_ascii=False))
except Exception as e:
    print(f"解析错误: {e}")
    print(f"原始输出: {result.stdout}")
