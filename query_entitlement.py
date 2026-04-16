# -*- coding: utf-8 -*-
import subprocess
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "87A2yO"

input_data = {"url": url, "sheet_id": sheet_id}

cmd = [cli_path, "doc", "smartsheet_get_records", json.dumps(input_data)]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

# 写入文件
with open("entitlement_query.json", "w", encoding="utf-8") as f:
    f.write(result.stdout)

print("已保存到 entitlement_query.json")

# 解析
try:
    # CLI 返回的是包装格式 {"content": [{"text": "..."}]}
    wrapper = json.loads(result.stdout)
    if "content" in wrapper:
        inner_json = wrapper["content"][0]["text"]
        data = json.loads(inner_json)
        records = data.get("records", [])
        print(f"记录总数: {len(records)}")
        for i, r in enumerate(records[-5:], 1):
            print(f"\n记录 {i}:")
            values = r.get("values", {})
            for k, v in values.items():
                if k == "genre" and isinstance(v, list) and len(v) > 0:
                    print(f"  {k}: {v[0].get('text', '')}")
                else:
                    print(f"  {k}: {v}")
    else:
        print(f"未找到 content: {list(wrapper.keys())}")
except Exception as e:
    print(f"解析错误: {e}")
    print(f"原始输出: {result.stdout[:500]}")
