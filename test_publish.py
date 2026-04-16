# -*- coding: utf-8 -*-
import requests
import json

# 先查询场景包列表
BASE_URL = "http://localhost:8080"

# 登录获取 token
login_data = {
    "username": "admin",
    "password": "admin123"
}

session = requests.Session()

# 登录
login_resp = session.post(f"{BASE_URL}/login", json=login_data)
print(f"登录状态: {login_resp.status_code}")
login_result = login_resp.json()
print(f"登录结果: {json.dumps(login_result, ensure_ascii=False, indent=2)}")

if login_result.get("code") != 200:
    print("登录失败，退出")
    exit(1)

token = login_result.get("token")
headers = {"Authorization": f"Bearer {token}"}

# 查询场景包列表
list_resp = session.get(f"{BASE_URL}/business/fbs/scene-pack/list", headers=headers)
print(f"\n列表状态: {list_resp.status_code}")
list_result = list_resp.json()
print(f"列表结果: {json.dumps(list_result, ensure_ascii=False, indent=2)[:2000]}")

# 找一个场景包来发布
rows = list_result.get("rows", [])
if rows:
    pack = rows[0]
    pack_id = pack.get("id")
    pack_status = pack.get("status")
    pack_code = pack.get("packCode")
    print(f"\n选择场景包: id={pack_id}, status={pack_status}, packCode={pack_code}")

    # 尝试发布
    publish_data = {"id": pack_id}
    publish_resp = session.put(f"{BASE_URL}/business/fbs/scene-pack/publish",
                                json=publish_data, headers=headers)
    print(f"\n发布状态: {publish_resp.status_code}")
    publish_result = publish_resp.json()
    print(f"发布结果: {json.dumps(publish_result, ensure_ascii=False, indent=2)}")
