#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试积分消费同步到企微智能表格"""
import requests
import json

BASE_URL = "http://localhost:8080"

def login():
    """登录获取 token"""
    resp = requests.post(f"{BASE_URL}/login", json={
        "username": "admin",
        "password": "admin123"
    })
    data = resp.json()
    if data.get("code") == 200:
        return data["token"]
    raise Exception(f"登录失败: {data}")

def get_user_info(token):
    """获取用户信息"""
    resp = requests.get(f"{BASE_URL}/getInfo", headers={"Authorization": f"Bearer {token}"})
    return resp.json()

def create_scene_pack(token):
    """创建场景包"""
    resp = requests.post(f"{BASE_URL}/fbs/business/scenePack", 
        headers={"Authorization": f"Bearer {token}"},
        json={
            "packCode": f"TEST_PACK_{int(__import__('time').time())}",
            "pointsRuleCode": "RULE_BASIC",
            "status": 0,
            "visibleScope": "PUBLIC"
        })
    data = resp.json()
    if data.get("code") == 200:
        return data["data"]
    raise Exception(f"创建场景包失败: {data}")

def publish_scene_pack(token, packId):
    """发布场景包"""
    resp = requests.put(f"{BASE_URL}/fbs/business/scenePack/publish/{packId}",
        headers={"Authorization": f"Bearer {token}"})
    return resp.json()

def generate_auth_code(token, packId):
    """生成授权码"""
    resp = requests.post(f"{BASE_URL}/fbs/business/authCode/generate",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "packId": packId,
            "count": 1
        })
    data = resp.json()
    if data.get("code") == 200 and data.get("data"):
        return data["data"][0].get("code") if isinstance(data["data"], list) else data["data"]
    raise Exception(f"生成授权码失败: {data}")

def activate_auth_code(token, code):
    """激活授权码"""
    resp = requests.post(f"{BASE_URL}/fbs/business/mySelfService/activateAuthCode",
        headers={"Authorization": f"Bearer {token}"},
        json={"code": code})
    return resp.json()

def consume_points(token, packCode):
    """消费积分"""
    resp = requests.post(f"{BASE_URL}/fbs/business/skill/consume",
        headers={"Authorization": f"Bearer {token}"},
        json={"packCode": packCode})
    return resp.json()

def main():
    print("=== 开始测试积分消费同步 ===\n")
    
    # 1. 登录
    print("1. 登录...")
    token = login()
    print(f"   Token: {token[:20]}...\n")
    
    # 2. 获取用户信息
    print("2. 获取用户信息...")
    user_info = get_user_info(token)
    user_id = user_info.get("user", {}).get("userId")
    points = user_info.get("user", {}).get("points", 0)
    print(f"   userId: {user_id}, points: {points}\n")
    
    # 3. 创建场景包
    print("3. 创建场景包...")
    pack = create_scene_pack(token)
    pack_id = pack.get("packId")
    pack_code = pack.get("packCode")
    print(f"   packId: {pack_id}, packCode: {pack_code}\n")
    
    # 4. 发布场景包
    print("4. 发布场景包...")
    publish_result = publish_scene_pack(token, pack_id)
    print(f"   发布结果: {publish_result.get('msg', 'success')}\n")
    
    # 5. 生成授权码
    print("5. 生成授权码...")
    auth_code = generate_auth_code(token, pack_id)
    print(f"   授权码: {auth_code}\n")
    
    # 6. 激活授权码
    print("6. 激活授权码...")
    activate_result = activate_auth_code(token, auth_code)
    print(f"   激活结果: {activate_result.get('msg', 'success')}\n")
    
    # 7. 消费积分（触发同步）
    print("7. 消费积分（触发同步）...")
    consume_result = consume_points(token, pack_code)
    print(f"   消费结果: {json.dumps(consume_result, ensure_ascii=False, indent=2)}\n")
    
    print("=== 测试完成 ===")
    print(f"\n请在企微智能表格中查看是否有新记录：")
    print(f"  - user_id: {user_id}")
    print(f"  - genre: {pack_code}")
    print(f"  - event: CONSUME")
    print(f"  - delta: -1")

if __name__ == "__main__":
    main()
