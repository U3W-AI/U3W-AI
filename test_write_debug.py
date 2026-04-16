#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试后端写入（打印参数）"""
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

# 登录
resp = requests.post(f"{BASE_URL}/login", json={"username": "admin", "password": "admin123"})
token = resp.json()["token"]

# 模拟后端构建的参数
# 参见 WecomBusinessSyncServiceImpl.buildCommercialHubRecord
headers = {"Authorization": f"Bearer {token}"}

# 使用后端实际构建的格式
write_data = {
    "sheetName": "commercial_hub",
    "records": [
        {
            "values": {
                "record_type": [{"type": "text", "text": "TEST_DIRECT"}],
                "user_id": [{"type": "text", "text": "999"}],  # 文本格式
                "event": [{"type": "text", "text": "MANUAL_TEST"}],
                "delta": 888,  # 数字
                "genre": [{"type": "text", "text": "TEST"}]
            }
        }
    ]
}

print("发送参数:")
print(json.dumps(write_data, ensure_ascii=False, indent=2))

resp = requests.post(f"{BASE_URL}/fbs/business/wecom/sync/write", 
                     json=write_data, headers=headers)
print(f"\n响应: {json.dumps(resp.json(), ensure_ascii=False, indent=2)}")
