#!/usr/bin/env python3
"""
LedgerSync - 积分同步进程

OpenSpec #10: 积分模型适配与 LedgerSync
定期轮询 Skill API /user/info，将积分余额写入本地 JSON 文件。
供 FBS-BookWriter Skill 端离线/弱网访问。

使用方法:
    export API_BASE_URL="http://your-domain/fbs/skill-api"
    export API_KEY="fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    export USER_ID="1"
    export SYNC_INTERVAL="300"
    export OUTPUT_PATH="./credits-ledger.json"
    python ledgersync.py
"""

import requests
import json
import time
import os
import sys
from datetime import datetime

# 配置（环境变量）
API_BASE_URL = os.environ.get('API_BASE_URL', 'http://localhost:8080/fbs/skill-api')
API_KEY = os.environ.get('API_KEY', 'your_api_key_here')
USER_ID = int(os.environ.get('USER_ID', '1'))
SYNC_INTERVAL = int(os.environ.get('SYNC_INTERVAL', '300'))  # 默认 5 分钟
OUTPUT_PATH = os.environ.get('OUTPUT_PATH', './credits-ledger.json')


def fetch_balance():
    """
    调用 /user/info 获取用户积分余额
    
    Returns:
        int: 用户积分余额
        
    Raises:
        Exception: API 调用失败时抛出
    """
    url = f"{API_BASE_URL}/user/info"
    headers = {'X-FBS-API-Key': API_KEY}  # ← 注意：X-FBS-API-Key，不是 X-API-Key
    data = {'userId': USER_ID}
    
    try:
        resp = requests.post(url, headers=headers, json=data, timeout=10)
        resp.raise_for_status()
        result = resp.json()
        
        if result.get('code') == 200:
            balance = result.get('data', {}).get('pointsBalance')
            if balance is None:
                raise Exception('响应中缺少 pointsBalance 字段')
            return balance
        else:
            raise Exception(result.get('msg', '未知错误'))
    except requests.exceptions.RequestException as e:
        raise Exception(f"HTTP 请求失败: {e}")
    except json.JSONDecodeError as e:
        raise Exception(f"JSON 解析失败: {e}")


def write_ledger(balance):
    """
    写入本地 JSON 文件
    
    Args:
        balance (int): 用户积分余额
    """
    data = {
        'user_id': USER_ID,
        'balance': balance,
        'last_updated': datetime.utcnow().isoformat() + 'Z',
        'sync_version': 'v1.0.0'
    }
    
    # 确保目录存在
    output_dir = os.path.dirname(OUTPUT_PATH)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir, exist_ok=True)
    
    with open(OUTPUT_PATH, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def main():
    """
    主循环
    """
    # 配置检查
    if API_KEY == 'your_api_key_here':
        print("[WARN] 警告: 未配置 API_KEY 环境变量")
        print("   请设置: export API_KEY='your_api_key_here'")
        sys.exit(1)
    
    print(f"LedgerSync 启动")
    print(f"  用户 ID: {USER_ID}")
    print(f"  API 地址: {API_BASE_URL}")
    print(f"  轮询间隔: {SYNC_INTERVAL}s")
    print(f"  输出路径: {OUTPUT_PATH}")
    print(f"")
    
    while True:
        try:
            balance = fetch_balance()
            write_ledger(balance)
            print(f"[{datetime.now().isoformat()}] [OK] 同步成功: balance={balance}")
        except KeyboardInterrupt:
            print("\n收到中断信号，退出...")
            break
        except Exception as e:
            print(f"[{datetime.now().isoformat()}] [FAIL] 同步失败: {e}")
        
        time.sleep(SYNC_INTERVAL)


if __name__ == '__main__':
    main()
