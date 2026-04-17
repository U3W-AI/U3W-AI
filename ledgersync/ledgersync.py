#!/usr/bin/env python3
"""
LedgerSync - 积分同步进程

OpenSpec #10: 积分模型适配与 LedgerSync
定期轮询 Skill API /user/info，将积分余额写入 FBS-BookWriter Skill 的 credits-ledger.json。
供 FBS-BookWriter Skill 端离线/弱网访问。

使用方法:
    # 方式1：环境变量配置（持续同步）
    $env:API_KEY = "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    $env:SKILL_ROOT = "C:/Users/加号/.workbuddy/skills/fbs-bookwriter"
    python ledgersync.py

    # 方式2：命令行参数（一次性同步）
    python ledgersync.py --once --skill-root "C:/Users/加号/.workbuddy/skills/fbs-bookwriter" --api-key "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

    # 方式3：旧版兼容（写到本地目录）
    $env:API_KEY = "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
    $env:OUTPUT_PATH = "./credits-ledger.json"
    python ledgersync.py
"""

import requests
import json
import time
import os
import sys
import argparse
from datetime import datetime

# 配置（环境变量）
API_BASE_URL = os.environ.get('API_BASE_URL', 'http://localhost:8080/fbs/skill-api')
API_KEY = os.environ.get('API_KEY', '')
USER_ID = int(os.environ.get('USER_ID', '1'))
SYNC_INTERVAL = int(os.environ.get('SYNC_INTERVAL', '300'))  # 默认 5 分钟
OUTPUT_PATH = os.environ.get('OUTPUT_PATH', '')  # 留空表示自动检测

# Skill 默认路径（如果环境变量未设置）
DEFAULT_SKILL_ROOTS = [
    'C:/Users/加号/.workbuddy/skills/fbs-bookwriter',
    'C:/Users/加号/.workbuddy/skills/FBS-BookWriter',
    'C:/Users/加号/.workbuddy/skills/FBS-BookWriter-Kit',
]


def get_skill_root():
    """获取 skill 根目录（优先环境变量，其次默认路径）"""
    skill_root = os.environ.get('SKILL_ROOT', '')
    if skill_root:
        return skill_root

    # 尝试默认路径
    for path in DEFAULT_SKILL_ROOTS:
        if os.path.exists(path):
            return path

    return None


def fetch_balance():
    """
    调用 /user/info 获取用户积分余额

    Returns:
        int: 用户积分余额

    Raises:
        Exception: API 调用失败时抛出
    """
    url = f"{API_BASE_URL}/user/info"
    headers = {'X-FBS-API-Key': API_KEY}
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


def read_skill_ledger(ledger_path):
    """
    读取 skill 现有的 credits-ledger.json（如果存在）

    Returns:
        dict: skill 格式的账本数据
    """
    if not os.path.exists(ledger_path):
        return {
            '_version': '1.0',
            '_comment': '由 LedgerSync 同步（后端积分 → Skill 本地）',
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
        print(f"[WARN] 读取现有账本失败: {e}，将创建新账本")
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
    ledger = {
        '_version': '1.0',
        '_comment': '由 LedgerSync 同步（后端 sys_user.points → Skill 本地）',
        '_sync_source': 'u3w-backend',
        '_sync_time': datetime.utcnow().isoformat() + 'Z',

        # 权威余额（来自后端）
        'balance': backend_balance,

        # 保留现有统计
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


def write_legacy_ledger(output_path, balance):
    """
    旧版兼容：写入简单格式的 credits-ledger.json

    Args:
        output_path (str): 输出文件路径
        balance (int): 用户积分余额
    """
    data = {
        'user_id': USER_ID,
        'balance': balance,
        'last_updated': datetime.utcnow().isoformat() + 'Z',
        'sync_version': 'v1.0.0'
    }

    output_dir = os.path.dirname(output_path)
    if output_dir and not os.path.exists(output_dir):
        os.makedirs(output_dir, exist_ok=True)

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def resolve_output_path(skill_root=None):
    """
    确定输出路径

    优先级：
    1. --skill-root 参数 → scene-packs/credits-ledger.json
    2. SKILL_ROOT 环境变量 → scene-packs/credits-ledger.json
    3. 自动检测默认 Skill 路径 → scene-packs/credits-ledger.json
    4. OUTPUT_PATH 环境变量（旧版兼容）
    5. ./credits-ledger.json（兜底）

    Returns:
        tuple: (ledger_path, is_skill_format)
            is_skill_format=True 表示写入 Skill 格式（含 total_earned 等）
            is_skill_format=False 表示写入旧版简单格式
    """
    # 1. 明确指定了 skill-root
    if skill_root:
        return os.path.join(skill_root, 'scene-packs', 'credits-ledger.json'), True

    # 2. 环境变量
    env_skill_root = os.environ.get('SKILL_ROOT', '')
    if env_skill_root:
        return os.path.join(env_skill_root, 'scene-packs', 'credits-ledger.json'), True

    # 3. 自动检测
    detected = get_skill_root()
    if detected:
        return os.path.join(detected, 'scene-packs', 'credits-ledger.json'), True

    # 4. 旧版 OUTPUT_PATH
    if OUTPUT_PATH:
        return OUTPUT_PATH, False

    # 5. 兜底
    return './credits-ledger.json', False


def sync_once(ledger_path, is_skill_format):
    """
    执行一次同步

    Args:
        ledger_path (str): 输出路径
        is_skill_format (bool): 是否使用 Skill 格式

    Returns:
        bool: 是否成功
    """
    try:
        balance = fetch_balance()

        if is_skill_format:
            existing = read_skill_ledger(ledger_path)
            ledger = write_skill_ledger(ledger_path, balance, existing)
            print(f"[{datetime.now().isoformat()}] [OK] 同步成功: balance={balance}")
            print(f"   输出文件: {ledger_path}")
        else:
            write_legacy_ledger(ledger_path, balance)
            print(f"[{datetime.now().isoformat()}] [OK] 同步成功: balance={balance}")
            print(f"   输出文件: {ledger_path}")

        return True

    except KeyboardInterrupt:
        raise
    except Exception as e:
        print(f"[{datetime.now().isoformat()}] [FAIL] 同步失败: {e}")
        return False


def main():
    """主入口"""
    global API_KEY, USER_ID  # 必须在函数开头声明

    parser = argparse.ArgumentParser(description='LedgerSync - 后端积分同步到 FBS-BookWriter Skill')
    parser.add_argument('--once', action='store_true', help='只执行一次同步（测试用）')
    parser.add_argument('--skill-root', type=str, help='Skill 根目录路径（自动写入 scene-packs/credits-ledger.json）')
    parser.add_argument('--api-key', type=str, help='FBS API Key')
    parser.add_argument('--user-id', type=int, default=USER_ID, help='用户 ID')
    parser.add_argument('--output', type=str, help='输出文件路径（旧版兼容，不推荐）')
    args = parser.parse_args()

    # 覆盖环境变量
    if args.api_key:
        API_KEY = args.api_key
    if args.output:
        os.environ['OUTPUT_PATH'] = args.output
    USER_ID = args.user_id

    # 配置检查
    if not API_KEY:
        print("[WARN] 未配置 API_KEY")
        print("   请设置环境变量或使用 --api-key 参数")
        print()
        print("   PowerShell:")
        print('   $env:API_KEY = "fbs_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"')
        sys.exit(1)

    # 确定输出路径
    ledger_path, is_skill_format = resolve_output_path(args.skill_root)

    print("LedgerSync 启动")
    print(f"  用户 ID:    {USER_ID}")
    print(f"  API 地址:   {API_BASE_URL}")
    print(f"  输出路径:   {ledger_path}")
    print(f"  格式:       {'Skill' if is_skill_format else '旧版兼容'}")
    if not args.once:
        print(f"  轮询间隔:   {SYNC_INTERVAL}s")
    print()

    # 一次性同步
    if args.once:
        success = sync_once(ledger_path, is_skill_format)
        sys.exit(0 if success else 1)

    # 持续同步
    while True:
        try:
            sync_once(ledger_path, is_skill_format)
        except KeyboardInterrupt:
            print("\n收到中断信号，退出...")
            break

        time.sleep(SYNC_INTERVAL)


if __name__ == '__main__':
    main()
