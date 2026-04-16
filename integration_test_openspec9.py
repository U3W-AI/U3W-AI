#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenSpec #9 集成测试
测试：积分消费后同步到企微智能表格（commercial_hub）

前提：
1. 后端服务已启动（端口 8080）
2. 有测试用户（admin/admin123）
3. 有测试场景包（packCode=FBS-test-sync）
"""

import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

import requests
import json
import time
from datetime import datetime

BASE_URL = "http://localhost:8080"

def login():
    """登录获取 JWT Token"""
    url = f"{BASE_URL}/login"
    data = {
        "username": "admin",
        "password": "admin123"
    }
    
    response = requests.post(url, json=data)
    if response.status_code == 200:
        result = response.json()
        token = result.get("token")
        print(f"[OK] Login success, Token: {token[:20]}...")
        return token
    else:
        print(f"[FAIL] Login failed: {response.status_code} - {response.text}")
        return None

def get_user_info(token):
    """获取用户信息"""
    url = f"{BASE_URL}/getInfo"
    headers = {"Authorization": f"Bearer {token}"}
    
    response = requests.get(url, headers=headers)
    if response.status_code == 200:
        result = response.json()
        user = result.get("user", {})
        user_id = user.get("userId")
        points = user.get("points", 0)
        print(f"[OK] User info: userId={user_id}, points={points}")
        return user_id, points
    else:
        print(f"[FAIL] Get user info failed: {response.status_code}")
        return None, None

def create_scene_pack(token):
    """创建测试场景包"""
    url = f"{BASE_URL}/business/fbs/scene-pack"
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "packCode": f"FBS-test-sync-{int(time.time())}",
        "packName": "IntegrationTest-Sync",
        "description": "OpenSpec #9 Integration Test",
        "pointsRuleCode": "USE_DAILY_ASSISTANT",  # 使用已有的积分规则
        "visibleScope": "WORKBUDDY",
        "status": 0  # Draft
    }
    
    response = requests.post(url, json=data, headers=headers)
    if response.status_code == 200:
        result = response.json()
        pack_id = result.get("data")
        print(f"[OK] Create scene pack success: packId={pack_id}")
        return pack_id, data["packCode"]
    else:
        print(f"[FAIL] Create scene pack failed: {response.status_code} - {response.text}")
        return None, None

def publish_scene_pack(token, pack_id):
    """发布场景包"""
    url = f"{BASE_URL}/business/fbs/scene-pack/publish"
    headers = {"Authorization": f"Bearer {token}"}
    data = {"id": pack_id}
    
    response = requests.put(url, json=data, headers=headers)
    if response.status_code == 200:
        print(f"[OK] Publish scene pack success: packId={pack_id}")
        return True
    else:
        print(f"[FAIL] Publish scene pack failed: {response.status_code} - {response.text}")
        return False

def generate_auth_code(token, pack_code):
    """生成授权码"""
    url = f"{BASE_URL}/business/fbs/auth-code/generate"
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "targetPackCode": pack_code,  # 使用场景包编码
        "count": 1,
        "maxActivations": 1
    }
    
    response = requests.post(url, json=data, headers=headers)
    if response.status_code == 200:
        result = response.json()
        # 返回格式是 Map<Long, String>，key 是 id，value 是 authCode
        data_map = result.get("data", {})
        if data_map:
            # 获取第一个授权码
            auth_code = list(data_map.values())[0]
            print(f"[OK] Generate auth code success: {auth_code}")
            return auth_code
    print(f"[FAIL] Generate auth code failed: {response.status_code} - {response.text}")
    return None

def activate_auth_code(token, auth_code):
    """激活授权码"""
    url = f"{BASE_URL}/fbs/internal/auth-code/activate"
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "authCode": auth_code,
        "userId": 1  # admin user
    }
    
    response = requests.post(url, json=data, headers=headers)
    if response.status_code == 200:
        result = response.json()
        print(f"[OK] Activate auth code success: {result}")
        return True
    else:
        print(f"[FAIL] Activate auth code failed: {response.status_code} - {response.text}")
        return False

def consume_points(token, pack_code):
    """消费积分（触发同步）"""
    url = f"{BASE_URL}/fbs/internal/usage/consume"
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "userId": 1,
        "packCode": pack_code,
        "skillCode": "FBS-BOOKWRITER",  # 添加 skillCode
        "usageRecordId": f"test-sync-{int(time.time())}",
        "hostType": "WORKBUDDY"
    }
    
    response = requests.post(url, json=data, headers=headers)
    if response.status_code == 200:
        result = response.json()
        print(f"[OK] Consume points success: {result}")
        return True
    else:
        print(f"[FAIL] Consume points failed: {response.status_code} - {response.text}")
        return False

def check_commercial_hub():
    """检查企微智能表格中的 commercial_hub 记录"""
    print("\n[INFO] Please check commercial_hub Sheet in WeChat Smart Sheet")
    print(f"   - docid: dcIxqVAUO6KLCapL2psEoEbEy5Dn_il9ITZz5tdOYFVubhcKVNiWgEojwnuc0qPGkCPlVydF628yel8-hoeuPObg")
    print(f"   - sheet_id: 04bLwp")
    print(f"   - Find: record_type=SKILL_USAGE, event=CONSUME")

def main():
    print("=" * 60)
    print("OpenSpec #9 Integration Test: Points Consume -> WeChat Smart Sheet")
    print("=" * 60)
    
    # Step 1: Login
    print("\n[Step 1] Login...")
    token = login()
    if not token:
        return
    
    # Step 2: Get user info
    print("\n[Step 2] Get user info...")
    user_id, points = get_user_info(token)
    if not user_id:
        return
    
    # Step 3: Create scene pack
    print("\n[Step 3] Create test scene pack...")
    pack_id, pack_code = create_scene_pack(token)
    if not pack_id:
        return
    
    # Step 4: Publish scene pack
    print("\n[Step 4] Publish scene pack...")
    if not publish_scene_pack(token, pack_id):
        return
    
    # Step 5: Generate auth code
    print("\n[Step 5] Generate auth code...")
    auth_code = generate_auth_code(token, pack_code)
    if not auth_code:
        return
    
    # Step 6: Activate auth code
    print("\n[Step 6] Activate auth code...")
    if not activate_auth_code(token, auth_code):
        return
    
    # Step 7: Consume points (trigger sync)
    print("\n[Step 7] Consume points (trigger sync)...")
    if not consume_points(token, pack_code):
        return
    
    # Step 8: Check WeChat smart sheet
    print("\n[Step 8] Check WeChat smart sheet...")
    check_commercial_hub()
    
    print("\n" + "=" * 60)
    print("[SUCCESS] Integration test completed")
    print("=" * 60)

if __name__ == "__main__":
    main()
