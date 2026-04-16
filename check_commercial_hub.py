#!/usr/bin/env python3
"""查询 commercial_hub Sheet 的记录"""
import subprocess
import json

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"

# 参数
params = {
    "docid": "dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg",
    "sheet_id": "04bLwp"
}

# 调用 CLI
result = subprocess.run(
    [cli_path, "doc", "smartsheet_get_records"],
    input=json.dumps(params),
    capture_output=True,
    text=True,
    encoding='utf-8'
)

print("=== STDOUT ===")
print(result.stdout)
print("\n=== STDERR ===")
print(result.stderr)
print("\n=== Return Code ===")
print(result.returncode)
