# -*- coding: utf-8 -*-
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"

# 使用 URL
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"

# 查询文档子表信息
cmd = [cli_path, "doc", "smartsheet_get_sheet", json.dumps({"url": url})]

result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

# 解析结果
with open("sheets_result.json", "w", encoding="utf-8") as f:
    f.write(result.stdout)

try:
    data = json.loads(result.stdout)
    if "content" in data:
        content_text = data["content"][0]["text"]
        content = json.loads(content_text)
        sheets = content.get("sheets", [])
        print(f"文档子表数: {len(sheets)}")
        print("\n子表列表:")
        for sheet in sheets:
            sheet_id = sheet.get("sheet_id", "")
            title = sheet.get("title", "")
            print(f"  sheet_id: {sheet_id}, title: {title}")
    else:
        sheets = data.get("sheets", [])
        print(f"文档子表数: {len(sheets)}")
        for sheet in sheets:
            print(f"  sheet_id: {sheet.get('sheet_id')}, title: {sheet.get('title')}")
except Exception as e:
    print(f"解析错误: {e}")
    print(f"已保存原始输出到 sheets_result.json")
