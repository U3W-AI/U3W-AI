# -*- coding: utf-8 -*-
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"

# 用 smartsheet_get_sheet 查询所有子表
input_data = {"url": url}

cmd = [cli_path, "doc", "smartsheet_get_sheet", json.dumps(input_data)]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

with open("all_sheets.json", "w", encoding="utf-8") as f:
    f.write(result.stdout)

print(result.stdout[:2000])
