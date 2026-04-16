# -*- coding: utf-8 -*-
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"

url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"
sheet_id = "87A2yO"  # entitlement

# 使用 smartsheet_get_sheet 获取字段定义
input_data = {
    "url": url,
    "sheet_id": sheet_id
}

cmd = [
    cli_path, "doc", "smartsheet_get_sheet",
    json.dumps(input_data)
]

result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

with open("entitlement_fields.json", "w", encoding="utf-8") as f:
    f.write(result.stdout)

print("已保存到 entitlement_fields.json")

try:
    data = json.loads(result.stdout)
    if "content" in data:
        inner = json.loads(data["content"][0]["text"])
        fields = inner.get("fields", [])
        print(f"\n字段数: {len(fields)}")
        for field in fields:
            print(f"  - {field.get('field_title', 'N/A')} (field_id: {field.get('field_id', 'N/A')}, type: {field.get('field_type', 'N/A')})")
    else:
        print("无法解析")
except Exception as e:
    print(f"解析错误: {e}")
