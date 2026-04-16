#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""直接测试写入智能表格"""
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

# 1. 登录
resp = requests.post(f"{BASE_URL}/login", json={"username": "admin", "password": "admin123"})
token = resp.json()["token"]
print(f"Token: {token[:20]}...")

# 2. 直接调用写入 API（如果存在）
headers = {"Authorization": f"Bearer {token}"}

# 测试写入（使用正确格式）
write_data = {
    "sheetName": "commercial_hub",
    "records": [
        {
            "values": {
                "record_type": [{"type": "text", "text": "TEST_DIRECT"}],
                "user_id": 999,
                "event": [{"type": "text", "text": "MANUAL_TEST"}],
                "delta": 0,
                "genre": [{"type": "text", "text": "TEST"}]
            }
        }
    ]
}

print(f"\n尝试写入测试记录...")
resp = requests.post(f"{BASE_URL}/fbs/business/wecom/sync/write", 
                     json=write_data, headers=headers)
print(f"响应: {resp.status_code}")
print(json.dumps(resp.json(), ensure_ascii=False, indent=2))
