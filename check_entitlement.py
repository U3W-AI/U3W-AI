# -*- coding: utf-8 -*-
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"
sheet_id = "87A2yO"

input_data = {"url": url, "sheet_id": sheet_id}

cmd = [cli_path, "doc", "smartsheet_get_records", json.dumps(input_data)]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

with open("entitlement_latest.json", "w", encoding="utf-8") as f:
    f.write(result.stdout)

data = json.loads(result.stdout)
records = data.get("records", [])
print(f"总记录数: {len(records)}")
print("\n最新记录:")
if records:
    latest = records[-1]
    print(f"record_id: {latest.get('record_id')}")
    values = latest.get("values", {})
    for k, v in values.items():
        if v:
            print(f"  {k}: {v}")
