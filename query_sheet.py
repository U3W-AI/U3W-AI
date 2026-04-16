#!/usr/bin/env python3
"""查询智能表格的所有子表信息"""
import subprocess
import json

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
docid = "dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg"

# 方法1: 尝试获取文档信息
print("=== 方法1: smartsheet_get_sheet ===")
params1 = {
    "docid": docid
}

result = subprocess.run(
    [cli_path, "doc", "smartsheet_get_sheet"],
    input=json.dumps(params1),
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result.stdout)
if result.stderr:
    print("STDERR:", result.stderr)

# 方法2: 尝试获取记录（使用正确的 sheet_id）
print("\n=== 方法2: smartsheet_get_records (sheet_id=04bLwp) ===")
params2 = {
    "docid": docid,
    "sheet_id": "04bLwp"
}

# 用 subprocess 直接传参
result2 = subprocess.run(
    [cli_path, "doc", "smartsheet_get_records", json.dumps(params2)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result2.stdout)
if result2.stderr:
    print("STDERR:", result2.stderr)
