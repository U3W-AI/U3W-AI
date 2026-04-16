# -*- coding: utf-8 -*-
import requests
import json

BASE_URL = "http://localhost:8080"

# 1. 登录获取 token
login_resp = requests.post(
    f"{BASE_URL}/login",
    json={"username": "admin", "password": "admin123"}
)
token = login_resp.json().get("token")
headers = {"Authorization": f"Bearer {token}"}

# 查询积分规则表
# 使用 SQL 查询接口（如果有的话）
# 或者直接查询 MySQL

import mysql.connector
conn = mysql.connector.connect(
    host='119.45.71.36',
    port=3306,
    user='root',
    password='Aa112211',
    database='wxfbsir',
    charset='utf8mb4'
)

cursor = conn.cursor()
cursor.execute("SELECT rule_code, rule_name, points, status FROM fbs_points_rule WHERE status = '0'")
rules = cursor.fetchall()
print("积分规则列表:")
for rule in rules:
    print(f"  - {rule[0]}: {rule[1]}, points={rule[2]}")

cursor.close()
conn.close()
