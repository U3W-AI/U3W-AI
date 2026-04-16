# -*- coding: utf-8 -*-
import subprocess
import json
import sys
from datetime import datetime

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"

url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"
sheet_id = "87A2yO"  # entitlement

# 测试写入一条记录
test_values = {
    "genre": "TEST_PACK_PYTHON",
    "credits_required": 123,
    "trial_allowed": True,
    "enterprise_only": False
}

input_data = {
    "url": url,
    "sheet_id": sheet_id,
    "records": [
        {"values": test_values}
    ]
}

cmd = [
    cli_path, "doc", "smartsheet_add_records",
    json.dumps(input_data, ensure_ascii=False)
]

result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

print("写入结果:")
print(result.stdout)

try:
    data = json.loads(result.stdout)
    if "content" in data:
        inner = json.loads(data["content"][0]["text"])
        print(f"\nerrcode: {inner.get('errcode')}")
        print(f"errmsg: {inner.get('errmsg')}")
        if inner.get('errcode') == 0:
            print("✅ 写入成功！")
        else:
            print("❌ 写入失败")
except Exception as e:
    print(f"解析错误: {e}")
