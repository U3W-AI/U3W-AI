#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查最新日志"""
import os
import sys
sys.stdout.reconfigure(encoding='utf-8')

log_file = r"G:\Interview\wukongshigang\Weihu\U3W-AI\WxFbsir-admin\logs\sys-info.log"

# 读取最后 200 行
with open(log_file, 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()[-200:]

print("最后 200 行日志:")
for line in lines:
    print(line.strip())
