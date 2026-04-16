#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查后端日志中的同步信息"""
import sys
sys.stdout.reconfigure(encoding='utf-8')

log_file = r"G:\Interview\wukongshigang\Weihu\U3W-AI\WxFbsir-admin\logs\sys-info.log"

try:
    with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
        lines = f.readlines()[-200:]  # 最后200行
        for line in lines:
            if any(kw in line.lower() for kw in ['sync', '同步', 'wecom', 'cli', 'error', 'fail']):
                print(line.strip())
except Exception as e:
    print(f"读取日志失败: {e}")
