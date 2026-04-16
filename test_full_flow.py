# -*- coding: utf-8 -*-
import requests
import json
import random
import string
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

# 登录
login_data = {"username": "admin", "password": "admin123"}
session = requests.Session()
login_resp = session.post(f"{BASE_URL}/login", json=login_data)
token = login_resp.json().get("token")
headers = {"Authorization": f"Bearer {token}"}

# 生成随机场景包编码
random_suffix = ''.join(random.choices(string.ascii_lowercase, k=6))
pack_code = f"entitlement_test_{random_suffix}"

# 1. 创建场景包（草稿）
create_data = {
    "packCode": pack_code,
    "packName": f"Entitlement_test_{pack_code}",
    "packType": 1,
    "ownerType": 2,  # 企业包
    "description": "Test entitlement sync",
    "pointsRuleCode": "RULE_100"
}
create_resp = session.post(f"{BASE_URL}/business/fbs/scene-pack", json=create_data, headers=headers)
print(f"Create status: {create_resp.status_code}")
create_result = create_resp.json()
print(f"Create result: {json.dumps(create_result, ensure_ascii=False, indent=2)}")

if create_result.get("code") != 200:
    print("Create failed, exit")
    exit(1)

pack_id = create_result.get("data")
print(f"\nScene pack ID: {pack_id}")

# 2. 发布场景包
publish_data = {"id": pack_id}
publish_resp = session.put(f"{BASE_URL}/business/fbs/scene-pack/publish", json=publish_data, headers=headers)
print(f"\nPublish status: {publish_resp.status_code}")
publish_result = publish_resp.json()
print(f"Publish result: {json.dumps(publish_result, ensure_ascii=False, indent=2)}")

# 3. 检查智能表格 entitlement 是否有新记录
print("\nWait 2 seconds then check smart sheet...")
import time
time.sleep(2)

# 查询智能表格
cli_path = r"G:\Interview\wukongshigang\Weihu\wecom-cli\wecom-cli\node_modules\@wecom\cli-win32-x64\bin\wecom-cli.exe"
import subprocess

url = "https://doc.weixin.qq.com/smartsheet/s3_AcQA0Aa1AIgCNci8FNLxdRaGFbVxz?scode=AMsARAf3AHEBogvdcV"
sheet_id = "87A2yO"

input_data = {"url": url, "sheet_id": sheet_id}
cmd = [cli_path, "doc", "smartsheet_get_records", json.dumps(input_data)]
result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8')

data = json.loads(result.stdout)
records = data.get("records", [])
print(f"\nSmart sheet record count: {len(records)}")

# 查找刚创建的记录
for r in records:
    values = r.get("values", {})
    genre_list = values.get("genre", [])
    if genre_list and genre_list[0].get("text") == pack_code:
        print(f"\n[SUCCESS] Found matching record!")
        print(f"  record_id: {r.get('record_id')}")
        print(f"  genre: {pack_code}")
        print(f"  credits_required: {values.get('credits_required')}")
        print(f"  enterprise_only: {values.get('enterprise_only')}")
        print(f"  trial_allowed: {values.get('trial_allowed')}")
        break
else:
    print(f"\n[FAILED] No matching record found (packCode={pack_code})")
