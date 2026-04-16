#!/usr/bin/env python3
"""
LedgerSync Skill Bridge - 后端积分同步到 FBS-BookWriter Skill

将 U3W-AI 后端的用户积分同步到 FBS-BookWriter skill 的 credits-ledger.json，
实现后端积分 → Skill 本地文件的单向同步。

使用方法:
    # 方式1：环境变量配置
    $env:FBS_API_KEY = "fbs_ledgersync_test_key_00000000000001"
    $env:FBS_SKILL_ROOT = "C:/Users/加号/.workbuddy/skills/FBS-BookWriter-Kit"
    python ledgersync-skill-bridge.py
    
    # 方式2：命令行参数（一次性同步）
    python ledgersync-skill-bridge.py --once --skill-root "C:/Users/加号/.workbuddy/skills/FBS-BookWriter-Kit"
"""

import requests
import json
import time
import os
import sys
import argparse
from datetime import datetime

# 配置（环境变量）
API_BASE_URL = os.environ.get('FBS_API_BASE_URL', 'http://localhost:8080/fbs/skill-api')
API_KEY = os.environ.get('FBS_API_KEY', '')
USER_ID = int(os.environ.get('FBS_USER_ID', '1'))
SYNC_INTERVAL = int(os.environ.get('FBS_SYNC_INTERVAL', '300'))  # 默认 5 分钟

# Skill 默认路径（如果环境变量未设置）
DEFAULT_SKILL_ROOTS = [
    'C:/Users/加号/.workbuddy/skills/FBS-BookWriter',
    'C:/Users/加号/.workbuddy/skills/FBS-BookWriter-Kit',
]


def get_skill_root():
    """获取 skill 根目录（优先环境变量，其次默认路径）"""
    skill_root = os.environ.get('FBS_SKILL_ROOT', '')
    if skill_root:
        return skill_root
    
    # 尝试默认路径
    for path in DEFAULT_SKILL_ROOTS:
        if os.path.exists(path):
            return path
    
    return None


def fetch_balance_from_backend():
    """
    从后端 API 获取用户积分余额
    
    Returns:
        dict: { balance: int, pointsBalance: int, activatedPacks: list }
    """
    if not API_KEY:
        raise Exception('未配置 FBS_API_KEY 环境变量')
    
    url = f"{API_BASE_URL}/user/info"
    headers = {'X-FBS-API-Key': API_KEY}
    data = {'userId': USER_ID}
    
    try:
        resp = requests.post(url, headers=headers, json=data, timeout=10)
        resp.raise_for_status()
        result = resp.json()
        
        if result.get('code') == 200:
            points_balance = result.get('data', {}).get('pointsBalance')
            if points_balance is None:
                raise Exception('响应中缺少 pointsBalance 字段')
            return {
                'balance': points_balance,
                'pointsBalance': points_balance,
                'activatedPacks': result.get('data', {}).get('activatedPacks', [])
            }
        else:
            raise Exception(result.get('msg', '未知错误'))
    except requests.exceptions.RequestException as e:
        raise Exception(f"HTTP 请求失败: {e}")
    except json.JSONDecodeError as e:
        raise Exception(f"JSON 解析失败: {e}")


def read_skill_ledger(ledger_path):
    """
    读取 skill 现有的 credits-ledger.json（如果存在）
    
    Returns:
        dict: skill 格式的账本数据
    """
    if not os.path.exists(ledger_path):
        return {
            '_version': '1.0',
            '_comment': '由 LedgerSync Skill Bridge 自动同步（后端积分）',
            'balance': 0,
            'total_earned': 0,
            'total_spent': 0,
            'createdAt': datetime.utcnow().isoformat() + 'Z',
            'updatedAt': datetime.utcnow().isoformat() + 'Z',
        }
    
    try:
        with open(ledger_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"⚠️  读取现有账本失败: {e}，将创建新账本")
        return {
            '_version': '1.0',
            'balance': 0,
            'total_earned': 0,
            'total_spent': 0,
        }


def write_skill_ledger(ledger_path, backend_balance, existing_ledger):
    """
    写入 skill 格式的 credits-ledger.json
    
    策略：
    - balance = 后端余额（权威来源）
    - total_earned = max(existing, backend_balance)（取较大值）
    - 保留 first_install_done、last_daily_login 等幂等标记
    
    Args:
        ledger_path (str): skill 账本路径
        backend_balance (int): 后端积分余额
        existing_ledger (dict): 现有 skill 账本
    
    Returns:
        dict: 新账本数据
    """
    # 合并后端余额和现有 skill 数据
    ledger = {
        '_version': '1.0',
        '_comment': '由 LedgerSync Skill Bridge 同步（后端 sys_user.points → Skill 本地）',
        '_sync_source': 'u3w-backend',
        '_sync_time': datetime.utcnow().isoformat() + 'Z',
        
        # 权威余额（来自后端）
        'balance': backend_balance,
        
        # 保留现有统计（如果后端余额更大，说明有新收入）
        'total_earned': max(existing_ledger.get('total_earned', 0), backend_balance),
        'total_spent': existing_ledger.get('total_spent', 0),
        
        # 保留幂等标记
        'first_install_done': existing_ledger.get('first_install_done', False),
        'last_daily_login': existing_ledger.get('last_daily_login', None),
        
        # 时间戳
        'createdAt': existing_ledger.get('createdAt', datetime.utcnow().isoformat() + 'Z'),
        'updatedAt': datetime.utcnow().isoformat() + 'Z',
    }
    
    # 确保目录存在
    ledger_dir = os.path.dirname(ledger_path)
    if ledger_dir:
        os.makedirs(ledger_dir, exist_ok=True)
    
    # 原子写入（tmp + rename）
    tmp_path = ledger_path + '.tmp'
    with open(tmp_path, 'w', encoding='utf-8') as f:
        json.dump(ledger, f, ensure_ascii=False, indent=2)
    
    # Windows 需要先删除再重命名
    if os.path.exists(ledger_path):
        os.remove(ledger_path)
    os.rename(tmp_path, ledger_path)
    
    return ledger


def sync_once(skill_root=None):
    """
    执行一次同步
    
    Args:
        skill_root (str): skill 根目录，如果为 None 则自动检测
    
    Returns:
        bool: 是否成功
    """
    # 确定路径
    if not skill_root:
        skill_root = get_skill_root()
    
    if not skill_root:
        print("❌ 未找到 skill 根目录")
        print("   请设置环境变量 FBS_SKILL_ROOT 或使用 --skill-root 参数")
        return False
    
    ledger_path = os.path.join(skill_root, 'scene-packs', 'credits-ledger.json')
    
    try:
        # 1. 从后端获取余额
        backend_data = fetch_balance_from_backend()
        backend_balance = backend_data['balance']
        
        # 2. 读取现有 skill 账本
        existing_ledger = read_skill_ledger(ledger_path)
        
        # 3. 写入 skill 格式账本
        ledger = write_skill_ledger(ledger_path, backend_balance, existing_ledger)
        
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] ✅ 同步成功")
        print(f"   后端余额: {backend_balance}")
        print(f"   Skill 余额: {ledger['balance']}")
        print(f"   输出文件: {ledger_path}")
        
        return True
        
    except Exception as e:
        print(f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] ❌ 同步失败: {e}")
        return False


def main():
    """主入口"""
    global API_KEY, USER_ID  # 必须在函数开头声明
    
    parser = argparse.ArgumentParser(description='后端积分同步到 FBS-BookWriter Skill')
    parser.add_argument('--once', action='store_true', help='只执行一次同步（测试用）')
    parser.add_argument('--skill-root', type=str, help='Skill 根目录路径')
    parser.add_argument('--api-key', type=str, help='FBS API Key')
    parser.add_argument('--user-id', type=int, default=USER_ID, help='用户 ID')
    args = parser.parse_args()
    
    # 覆盖环境变量
    if args.api_key:
        API_KEY = args.api_key
    
    USER_ID = args.user_id
    
    # 检查 API Key
    if not API_KEY:
        print("⚠️  未配置 FBS_API_KEY")
        print("   请设置环境变量或使用 --api-key 参数")
        print()
        print("   PowerShell:")
        print('   $env:FBS_API_KEY = "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"')
        sys.exit(1)
    
    # 确定路径
    skill_root = args.skill_root or get_skill_root()
    
    if not skill_root:
        print("⚠️  未找到 skill 根目录")
        print("   请设置 FBS_SKILL_ROOT 环境变量或使用 --skill-root 参数")
        print()
        print("   PowerShell:")
        print('   $env:FBS_SKILL_ROOT = "C:/Users/加号/.workbuddy/skills/FBS-BookWriter-Kit"')
        sys.exit(1)
    
    ledger_path = os.path.join(skill_root, 'scene-packs', 'credits-ledger.json')
    
    print("════════════════════════════════════════════════════")
    print("LedgerSync Skill Bridge")
    print("════════════════════════════════════════════════════")
    print(f"  用户 ID:    {USER_ID}")
    print(f"  API 地址:   {API_BASE_URL}")
    print(f"  Skill 路径: {skill_root}")
    print(f"  输出文件:   {ledger_path}")
    if not args.once:
        print(f"  轮询间隔:   {SYNC_INTERVAL}s")
    print("════════════════════════════════════════════════════")
    print()
    
    # 一次性同步
    if args.once:
        success = sync_once(skill_root)
        sys.exit(0 if success else 1)
    
    # 持续同步
    print(f"开始持续同步... (按 Ctrl+C 停止)")
    print()
    
    while True:
        try:
            sync_once(skill_root)
            print(f"  等待 {SYNC_INTERVAL} 秒...")
            print()
            time.sleep(SYNC_INTERVAL)
        except KeyboardInterrupt:
            print("\n收到中断信号，退出...")
            break


if __name__ == '__main__':
    main()
