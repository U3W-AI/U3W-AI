#!/usr/bin/env python3
"""查询智能表格的所有子表"""
import subprocess
import json

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
docid = "dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg"

# 尝试获取文档信息（列出所有子表）
print("=== 尝试获取文档信息 ===")
params = {
    "docid": docid
}

result = subprocess.run(
    [cli_path, "doc", "info", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result.stdout)

# 尝试其他可能的命令
print("\n=== 尝试 smartsheet_get ===")
result2 = subprocess.run(
    [cli_path, "doc", "smartsheet_get", json.dumps(params)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result2.stdout)

# 查看帮助
print("\n=== doc 命令帮助 ===")
result3 = subprocess.run(
    [cli_path, "doc", "--help"],
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result3.stdout)
