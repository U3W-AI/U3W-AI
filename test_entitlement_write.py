# -*- coding: utf-8 -*-
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"
sheet_id = "87A2yO"

# 模拟后端发送的格式
record = {
    "genre": [{"type": "text", "text": "TEST_PACK_DIRECT"}],
    "credits_required": 999,
    "trial_allowed": [{"id": "oO12F6", "style": 11, "text": "□"}],
    "enterprise_only": [{"id": "o7jw5f", "style": 13, "text": "✅"}]
}

input_data = {
    "url": url,
    "sheet_id": sheet_id,
    "records": [{"values": record}]
}

cmd = [cli_path, "doc", "smartsheet_add_records", json.dumps(input_data)]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

print("CLI 返回:")
print(result.stdout)
if result.stderr:
    print("错误:")
    print(result.stderr)
