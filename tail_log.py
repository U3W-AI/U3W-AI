#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""实时查看日志"""
import os
import sys
sys.stdout.reconfigure(encoding='utf-8')

log_file = r"G:\Interview\wukongshigang\Weihu\U3W-AI\WxFbsir-admin\logs\sys-info.log"

# 读取最后 100 行
with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()[-100:]

print(f"最后 100 行日志（查找 consume/sync/Skill 关键字）:\n")
for line in lines:
    if any(kw in line.lower() for kw in ['consume', 'skill', 'sync', 'wecom']):
        print(line.strip())

print("\n\n全部日志（最后 20 行）:")
for line in lines[-20:]:
    print(line.strip())
