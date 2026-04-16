# -*- coding: utf-8 -*-
import subprocess
import json
import sys

# 强制 UTF-8 输出
sys.stdout.reconfigure(encoding='utf-8')

# 使用 wecom-cli 查询智能表格记录
cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"

# 使用 URL（更可靠）
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "uvREDw"  # commercial_hub

# JSON 输入
input_data = {
    "url": url,
    "sheet_id": sheet_id
}

cmd = [
    cli_path, "doc", "smartsheet_get_records",
    json.dumps(input_data)
]

result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

# 写入文件
with open("sheet_records.json", "w", encoding="utf-8") as f:
    f.write(result.stdout)

print("已保存到 sheet_records.json")

# 解析并显示记录数
try:
    data = json.loads(result.stdout)
    records = data.get("records", [])
    print(f"记录数: {len(records)}")
    for i, r in enumerate(records[-5:], 1):  # 显示最近5条
        print(f"\n记录 {i}:")
        values = r.get("values", {})
        for k, v in values.items():
            print(f"  {k}: {v}")
except Exception as e:
    print(f"解析错误: {e}")
