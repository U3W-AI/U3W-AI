#!/usr/bin/env python3
"""查询智能表格的所有子表和字段"""
import subprocess
import json

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
docid = "dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg"

# 1. 获取所有子表
print("=== 1. 查询所有子表 (smartsheet_get_sheet) ===")
params1 = {"docid": docid}
result1 = subprocess.run(
    [cli_path, "doc", "smartsheet_get_sheet", json.dumps(params1)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result1.stdout)

# 解析结果获取 sheet_id
try:
    # 提取 JSON
    output = result1.stdout.strip()
    if output.startswith('{'):
        data = json.loads(output)
        if 'content' in data:
            inner = json.loads(data['content'][0]['text'])
            print("\n解析后的子表信息:")
            print(json.dumps(inner, indent=2, ensure_ascii=False))
            
            # 遍历每个 sheet，查询其字段
            if 'sheet_list' in inner:
                for sheet in inner['sheet_list']:
                    sheet_id = sheet.get('sheet_id')
                    sheet_title = sheet.get('title')
                    print(f"\n=== 2. 查询子表 '{sheet_title}' (sheet_id={sheet_id}) 的字段 ===")
                    params2 = {"docid": docid, "sheet_id": sheet_id}
                    result2 = subprocess.run(
                        [cli_path, "doc", "smartsheet_get_fields", json.dumps(params2)],
                        capture_output=True,
                        text=True,
                        encoding='utf-8'
                    )
                    print(result2.stdout)
except Exception as e:
    print(f"解析错误: {e}")
