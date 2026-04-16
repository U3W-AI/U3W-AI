# -*- coding: utf-8 -*-
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

# 1. 登录获取 token
login_data = {
    "username": "admin",
    "password": "admin123"
}

print("=== 1. 登录获取 Token ===")
resp = requests.post(f"{BASE_URL}/login", json=login_data)
print(f"状态码: {resp.status_code}")

if resp.status_code != 200:
    print(f"登录失败: {resp.text}")
    sys.exit(1)

token = resp.json().get("token")
if not token:
    print(f"未获取到 token: {resp.json()}")
    sys.exit(1)

print(f"Token: {token[:20]}...")

headers = {"Authorization": f"Bearer {token}"}

# 2. 创建场景包
import random
import string
pack_code = f"entitlement_test_{''.join(random.choices(string.ascii_lowercase, k=6))}"

create_data = {
    "packCode": pack_code,
    "packName": f"Entitlement测试包_{pack_code}",
    "description": "测试 entitlement 同步",
    "pointsRuleCode": "RULE_100"  # 设置积分规则
}

print(f"\n=== 2. 创建场景包 ===")
print(f"packCode: {pack_code}")
resp = requests.post(f"{BASE_URL}/business/fbs/scene-pack", json=create_data, headers=headers)
print(f"状态码: {resp.status_code}")
print(f"响应: {resp.text[:500]}")

if resp.status_code != 200:
    print("创建失败")
    sys.exit(1)

pack_id = resp.json().get("data")
print(f"场景包 ID: {pack_id}")

# 3. 发布场景包（触发 entitlement 同步）
publish_data = {"id": pack_id}

print(f"\n=== 3. 发布场景包（触发同步）===")
resp = requests.put(f"{BASE_URL}/business/fbs/scene-pack/publish", json=publish_data, headers=headers)
print(f"状态码: {resp.status_code}")
print(f"响应: {resp.text}")

# 4. 查询智能表格 entitlement
print(f"\n=== 4. 查询智能表格 entitlement ===")
import subprocess

cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEOJh2SlI"
sheet_id = "87A2yO"

input_data = {"url": url, "sheet_id": sheet_id}

cmd = [cli_path, "doc", "smartsheet_get_records", json.dumps(input_data)]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

try:
    data = json.loads(result.stdout)
    records = data.get("records", [])
    print(f"记录总数: {len(records)}")
    
    # 查找刚写入的记录
    for r in records[-3:]:
        values = r.get("values", {})
        genre = values.get("genre", [{}])[0].get("text", "") if values.get("genre") else ""
        if pack_code in genre:
            print(f"\n✅ 找到匹配记录:")
            print(f"  genre: {genre}")
            print(f"  credits_required: {values.get('credits_required')}")
            print(f"  trial_allowed: {values.get('trial_allowed')}")
            print(f"  enterprise_only: {values.get('enterprise_only')}")
except Exception as e:
    print(f"解析错误: {e}")
    print(f"原始输出: {result.stdout[:1000]}")

print("\n=== 测试完成 ===")
