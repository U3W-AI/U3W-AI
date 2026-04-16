# -*- coding: utf-8 -*-
import requests
import json
import uuid

BASE_URL = "http://localhost:8080"

# 1. 登录获取 token
login_resp = requests.post(
    f"{BASE_URL}/login",
    json={"username": "admin", "password": "admin123"}
)
token = login_resp.json().get("token")
headers = {"Authorization": f"Bearer {token}"}

print(f"Token: {token[:30]}...")

# 2. 创建一个免费场景包（pointsRuleCode=null = 免费包）
pack_code = f"test_pack_{uuid.uuid4().hex[:8]}"
create_data = {
    "packCode": pack_code,
    "packName": "测试场景包",
    "packType": 1,
    "ownerType": 1,
    "description": "测试用-免费包",
    "pointsRuleCode": None,  # NULL = 免费包
    "visibleScope": "ALL"
}

create_resp = requests.post(
    f"{BASE_URL}/fbs/internal/scene-pack/create",
    headers=headers,
    json=create_data
)
print(f"\n创建场景包: {create_resp.status_code}")
print(json.dumps(create_resp.json(), ensure_ascii=False, indent=2))

if create_resp.status_code != 200:
    print("创建失败，退出")
    exit(1)

pack_id = create_resp.json().get("data", {}).get("id")
print(f"\n场景包 ID: {pack_id}, packCode: {pack_code}")

# 3. 发布场景包（status=1）
publish_resp = requests.put(
    f"{BASE_URL}/business/fbs/scene-pack/publish",
    headers=headers,
    json={"id": pack_id}
)
print(f"\n发布场景包: {publish_resp.status_code}")
print(json.dumps(publish_resp.json(), ensure_ascii=False, indent=2))

# 4. 生成授权码
generate_data = {
    "codeType": 1,  # 场景包权益码
    "targetType": "SCENE_PACK",
    "targetId": pack_id,
    "issuerType": 1,  # 平台
    "issuerId": 1,
    "maxActivations": 10,
    "description": "测试用授权码"
}

generate_resp = requests.post(
    f"{BASE_URL}/fbs/internal/auth-code/generate",
    headers=headers,
    json=generate_data
)
print(f"\n生成授权码: {generate_resp.status_code}")
print(json.dumps(generate_resp.json(), ensure_ascii=False, indent=2))

if generate_resp.status_code != 200:
    print("生成授权码失败，退出")
    exit(1)

auth_code = generate_resp.json().get("data", {}).get("authCode")
print(f"\n授权码: {auth_code}")

# 5. 激活授权码（用户ID=1）
activate_data = {
    "authCode": auth_code,
    "userId": 1
}

activate_resp = requests.post(
    f"{BASE_URL}/fbs/internal/auth-code/activate",
    headers=headers,
    json=activate_data
)
print(f"\n激活授权码: {activate_resp.status_code}")
print(json.dumps(activate_resp.json(), ensure_ascii=False, indent=2))

# 6. 调用 Skill 消费 API
consume_data = {
    "userId": 1,
    "packCode": pack_code,
    "skillCode": "skill_test_001",
    "usageRecordId": str(uuid.uuid4()),
    "hostType": "STANDALONE",
    "hostSessionId": "test-session",
    "authCode": None
}

consume_resp = requests.post(
    f"{BASE_URL}/fbs/internal/usage/consume",
    headers=headers,
    json=consume_data
)
print(f"\n消耗积分响应: {consume_resp.status_code}")
print(json.dumps(consume_resp.json(), ensure_ascii=False, indent=2))

# 7. 查询用户当前积分
points_resp = requests.get(f"{BASE_URL}/points/getUserPoints", headers=headers)
print(f"\n当前积分: {points_resp.json()}")
