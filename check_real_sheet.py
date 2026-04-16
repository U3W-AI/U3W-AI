#!/usr/bin/env python3
"""查询正确的智能表格"""
import subprocess
import json

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"

# 从链接提取的正确 docid: s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz
# 去掉 s3_ 前缀
docid = "AcQA0Aa1AIgCNci8FNLxdRaGFbVxz"

print(f"=== 使用正确的 docid: {docid} ===\n")

# 1. 获取所有子表
print("=== 1. 查询所有子表 ===")
params1 = {"docid": docid}
result1 = subprocess.run(
    [cli_path, "doc", "smartsheet_get_sheet", json.dumps(params1)],
    capture_output=True,
    text=True,
    encoding='utf-8'
)
print(result1.stdout)

# 解析结果
try:
    output = result1.stdout.strip()
    if output.startswith('{'):
        data = json.loads(output)
        if 'content' in data:
            inner = json.loads(data['content'][0]['text'])
            print("\n子表列表:")
            if 'sheet_list' in inner:
                for sheet in inner['sheet_list']:
                    sheet_id = sheet.get('sheet_id')
                    sheet_title = sheet.get('title')
                    print(f"  - {sheet_title} (sheet_id={sheet_id})")
                    
                    # 查询每个子表的字段
                    print(f"\n=== 查询 '{sheet_title}' 字段 ===")
                    params2 = {"docid": docid, "sheet_id": sheet_id}
                    result2 = subprocess.run(
                        [cli_path, "doc", "smartsheet_get_fields", json.dumps(params2)],
                        capture_output=True,
                        text=True,
                        encoding='utf-8'
                    )
                    
                    # 解析字段
                    try:
                        field_output = result2.stdout.strip()
                        if field_output.startswith('{'):
                            field_data = json.loads(field_output)
                            if 'content' in field_data:
                                field_inner = json.loads(field_data['content'][0]['text'])
                                if 'fields' in field_inner:
                                    print(f"  字段数: {len(field_inner['fields'])}")
                                    for f in field_inner['fields']:
                                        print(f"    - {f.get('field_title')} ({f.get('field_type')})")
                    except:
                        print(result2.stdout[:500])
except Exception as e:
    print(f"解析错误: {e}")
