#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""查询 sync_log 表"""
import requests
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

BASE_URL = "http://localhost:8080"

# 登录
resp = requests.post(f"{BASE_URL}/login", json={"username": "admin", "password": "admin123"})
token = resp.json()["token"]
headers = {"Authorization": f"Bearer {token}"}

# 尝试查询 sync_log 列表
# 注意：这个 API 可能不存在
print("尝试查询 sync_log 列表...")

# 尝试不同的 API 路径
for path in ["/fbs/business/wecom/sync/log/list", "/business/fbs/wecom/sync/log/list"]:
    try:
        resp = requests.get(f"{BASE_URL}{path}", headers=headers)
        print(f"{path}: {resp.status_code}")
        if resp.status_code == 200:
            print(json.dumps(resp.json(), ensure_ascii=False, indent=2)[:500])
    except Exception as e:
        print(f"{path}: {e}")

# 直接用数据库查询可能更简单
print("\n直接查看 sync_log 表需要数据库连接...")
print("让我检查 CLI 调用详情...")

# 再写入一条，检查返回
write_data = {
    "sheetName": "commercial_hub",
    "records": [
        {
            "values": {
                "record_type": [{"type": "text", "text": "DEBUG_TEST"}],
                "user_id": [{"type": "text", "text": "DEBUG"}],
                "event": [{"type": "text", "text": "DEBUG"}],
                "delta": 12345,
                "genre": [{"type": "text", "text": "DEBUG"}]
            }
        }
    ]
}

print("\n写入测试...")
resp = requests.post(f"{BASE_URL}/fbs/business/wecom/sync/write", json=write_data, headers=headers)
result = resp.json()
print(json.dumps(result, ensure_ascii=False, indent=2))

sync_log_id = result.get("data", {}).get("syncLogId")
print(f"\nsyncLogId: {sync_log_id}")
