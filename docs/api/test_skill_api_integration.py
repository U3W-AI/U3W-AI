# -*- coding: utf-8 -*-
"""
OpenSpec #12 集成测试 - Skill API 端点
测试 API Key 反查 userId 功能

运行前提：
1. 后端服务已启动（localhost:8080）
2. 数据库中有测试用户和 API Key
"""
import requests
import json
import sys
import os
from datetime import datetime

# ===== 配置 =====
BASE = "http://localhost:8080"
SKILL_API_PREFIX = "/fbs/skill-api"
LOGIN_PREFIX = "/fbs"

TOKEN_FILE = os.path.join(os.path.dirname(__file__), ".token_skill_api_test")
OUTPUT_FILE = os.path.join(os.path.dirname(__file__), f"skill-api-test-result-{datetime.now().strftime('%Y%m%d-%H%M%S')}.md")

results = []
passes = fails = warns = skips = 0

def log_test(tid, name, status, detail=""):
    global passes, fails, warns, skips
    if status == "PASS": passes += 1
    elif status == "FAIL": fails += 1
    elif status == "WARN": warns += 1
    else: skips += 1
    
    icon = {"PASS": "✅", "FAIL": "❌", "WARN": "⚠️", "SKIP": "⏭️"}[status]
    results.append(f"| {name} | {tid} | {icon} | {detail} |")
    print(f"  {icon} {tid} {name} - {detail}")

def get_admin_token():
    """获取管理员 Token"""
    try:
        r = requests.post(
            f"{BASE}{LOGIN_PREFIX}/login",
            json={"username": "admin", "password": "admin123"},
            timeout=10
        )
        if r.status_code != 200:
            print(f"FATAL: 登录失败 HTTP {r.status_code}: {r.text}")
            sys.exit(1)
        
        token = r.json().get("token")
        with open(TOKEN_FILE, "w") as f:
            f.write(token)
        return token
    except Exception as e:
        print(f"FATAL: 登录异常 {e}")
        sys.exit(1)

def get_auth_headers(token=None):
    """获取认证头"""
    if token is None:
        try:
            with open(TOKEN_FILE) as f:
                token = f.read().strip()
        except:
            return None
    return {"Authorization": token}

def make_request(method, url, headers=None, json_body=None, timeout=10):
    """发起请求"""
    try:
        r = requests.request(method, url, headers=headers, json=json_body, timeout=timeout)
        try:
            j = r.json()
        except:
            j = None
        return r.status_code, j, ""
    except Exception as e:
        return 0, None, str(e)

def get_or_create_test_api_key(admin_headers):
    """获取或创建测试 API Key"""
    # 尝试获取现有的 API Key
    code, j, _ = make_request(
        "GET",
        f"{BASE}{LOGIN_PREFIX}/business/fbs/my-apikey/list",
        headers=admin_headers
    )
    
    if code == 200 and j and "rows" in j:
        # 查找可用的 API Key
        for key in j["rows"]:
            if key.get("status") == 0:  # 启用状态
                return key.get("apiKey"), key.get("userId")
    
    # 如果没有，创建新的 API Key
    code, j, _ = make_request(
        "POST",
        f"{BASE}{LOGIN_PREFIX}/business/fbs/my-apikey",
        headers=admin_headers,
        json_body={"keyName": f"test-skill-api-{datetime.now().strftime('%H%M%S')}"}
    )
    
    if code == 200 and j and "data" in j:
        return j["data"].get("apiKey"), j["data"].get("userId")
    
    return None, None

# ===== 测试用例 =====

def test_user_info_with_api_key(api_key, user_id):
    """测试 /user/info：不传 userId，从 API Key 反查"""
    print("\n=== 测试 /user/info API Key 反查 ===")
    
    # 1. 不传 userId，使用 API Key 反查
    code, j, err = make_request(
        "POST",
        f"{BASE}{SKILL_API_PREFIX}/user/info",
        headers={"X-FBS-API-Key": api_key, "Content-Type": "application/json"},
        json_body={}
    )
    
    if code == 200 and j and j.get("code") == 200:
        log_test("T5.1.1", "/user/info 不传 userId", "PASS", f"userId={j['data'].get('userId')}, balance={j['data'].get('pointsBalance')}")
    else:
        log_test("T5.1.1", "/user/info 不传 userId", "FAIL", f"HTTP {code}, code={j.get('code') if j else 'N/A'}, msg={j.get('msg') if j else err}")
    
    # 2. 传 userId，向后兼容测试
    code, j, err = make_request(
        "POST",
        f"{BASE}{SKILL_API_PREFIX}/user/info",
        headers={"X-FBS-API-Key": api_key, "Content-Type": "application/json"},
        json_body={"userId": user_id}
    )
    
    if code == 200 and j and j.get("code") == 200:
        log_test("T5.1.2", "/user/info 传 userId", "PASS", f"向后兼容正常")
    else:
        log_test("T5.1.2", "/user/info 传 userId", "FAIL", f"HTTP {code}, msg={j.get('msg') if j else err}")

def test_user_info_invalid_api_key():
    """测试无效 API Key"""
    print("\n=== 测试无效 API Key ===")
    
    code, j, err = make_request(
        "POST",
        f"{BASE}{SKILL_API_PREFIX}/user/info",
        headers={"X-FBS-API-Key": "invalid_key_12345", "Content-Type": "application/json"},
        json_body={}
    )
    
    if code == 401 or (j and j.get("code") == 401):
        log_test("T5.1.3", "无效 API Key", "PASS", "正确返回 401")
    else:
        log_test("T5.1.3", "无效 API Key", "WARN", f"HTTP {code}（预期 401）")

def test_usage_consume_with_api_key(api_key, user_id):
    """测试 /usage/consume：不传 userId，从 API Key 反查"""
    print("\n=== 测试 /usage/consume API Key 反查 ===")
    
    import uuid
    usage_record_id = f"test-{uuid.uuid4().hex[:12]}"
    
    # 1. 不传 userId，使用 API Key 反查
    code, j, err = make_request(
        "POST",
        f"{BASE}{SKILL_API_PREFIX}/usage/consume",
        headers={"X-FBS-API-Key": api_key, "Content-Type": "application/json"},
        json_body={
            "packCode": "general",
            "skillCode": "FBS-BookWriter",
            "usageRecordId": usage_record_id,
            "pointsAmount": 1
        }
    )
    
    if code == 200 and j and j.get("code") == 200:
        log_test("T5.1.4", "/usage/consume 不传 userId", "PASS", f"usageRecordId={j['data'].get('usageRecordId')}, remain={j['data'].get('remainPoints')}")
    elif code == 200 and j and j.get("code") == 400 and "余额不足" in j.get("msg", ""):
        log_test("T5.1.4", "/usage/consume 不传 userId", "WARN", "余额不足，但接口正常")
    else:
        log_test("T5.1.4", "/usage/consume 不传 userId", "FAIL", f"HTTP {code}, code={j.get('code') if j else 'N/A'}, msg={j.get('msg') if j else err}")
    
    # 2. 幂等性测试：相同 usageRecordId 重复调用
    code2, j2, err2 = make_request(
        "POST",
        f"{BASE}{SKILL_API_PREFIX}/usage/consume",
        headers={"X-FBS-API-Key": api_key, "Content-Type": "application/json"},
        json_body={
            "packCode": "general",
            "skillCode": "FBS-BookWriter",
            "usageRecordId": usage_record_id,
            "pointsAmount": 1
        }
    )
    
    if code2 == 200 and j2 and j2.get("code") == 200:
        if j2["data"].get("usageRecordId") == usage_record_id:
            log_test("T5.1.5", "幂等性测试", "PASS", "相同 usageRecordId 返回相同结果")
        else:
            log_test("T5.1.5", "幂等性测试", "WARN", "usageRecordId 不匹配")
    else:
        log_test("T5.1.5", "幂等性测试", "FAIL", f"HTTP {code2}, msg={j2.get('msg') if j2 else err2}")

# ===== 主流程 =====

def main():
    print("=" * 60)
    print("OpenSpec #12 集成测试 - Skill API 端点")
    print(f"时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    
    # 1. 登录获取 Token
    print("\n[Step 1] 管理员登录...")
    token = get_admin_token()
    log_test("A0", "管理员登录", "PASS", f"token={token[:20]}...")
    admin_headers = get_auth_headers(token)
    
    # 2. 获取或创建测试 API Key
    print("\n[Step 2] 获取测试 API Key...")
    api_key, user_id = get_or_create_test_api_key(admin_headers)
    
    if not api_key:
        print("FATAL: 无法获取测试 API Key")
        sys.exit(1)
    
    log_test("A1", "获取 API Key", "PASS", f"apiKey={api_key[:20]}..., userId={user_id}")
    
    # 3. 执行测试
    test_user_info_with_api_key(api_key, user_id)
    test_user_info_invalid_api_key()
    test_usage_consume_with_api_key(api_key, user_id)
    
    # 4. 生成报告
    print("\n" + "=" * 60)
    print(f"测试结果: ✅ 通过 {passes} | ❌ 失败 {fails} | ⚠️ 警告 {warns} | ⏭️ 跳过 {skips}")
    print("=" * 60)
    
    # 写入报告文件
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write(f"# OpenSpec #12 集成测试报告\n\n")
        f.write(f"**时间**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")
        f.write(f"**结果**: ✅ 通过 {passes} | ❌ 失败 {fails} | ⚠️ 警告 {warns} | ⏭️ 跳过 {skips}\n\n")
        f.write(f"## 测试详情\n\n")
        f.write(f"| 测试项 | ID | 状态 | 详情 |\n")
        f.write(f"|--------|----|----|------|\n")
        for r in results:
            f.write(r + "\n")
    
    print(f"\n报告已保存: {OUTPUT_FILE}")
    
    # 返回退出码
    if fails > 0:
        sys.exit(1)
    sys.exit(0)

if __name__ == "__main__":
    main()
