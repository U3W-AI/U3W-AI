#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查所有日志文件"""
import os
import sys
sys.stdout.reconfigure(encoding='utf-8')

log_dir = r"G:\Interview\wukongshigang\Weihu\U3W-AI\WxFbsir-admin\logs"
print(f"日志目录: {log_dir}")
print(f"文件列表: {os.listdir(log_dir) if os.path.exists(log_dir) else '不存在'}")

# 检查所有日志文件
for f in os.listdir(log_dir):
    if f.endswith('.log'):
        filepath = os.path.join(log_dir, f)
        print(f"\n=== {f} (最后 30 行) ===")
        try:
            with open(filepath, 'r', encoding='utf-8', errors='ignore') as file:
                lines = file.readlines()[-30:]
                for line in lines:
                    if any(kw in line.lower() for kw in ['sync', 'wecom', 'cli', 'consume', 'skill', 'error', 'fail', 'warn']):
                        print(line.strip())
        except Exception as e:
            print(f"读取失败: {e}")
