#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查最新日志"""
import os
import sys
sys.stdout.reconfigure(encoding='utf-8')

log_dir = r"G:\Interview\wukongshigang\Weihu\U3W-AI\WxFbsir-admin\logs"

# 检查所有日志文件
for f in ['sys-info.log', 'sys-error.log']:
    filepath = os.path.join(log_dir, f)
    print(f"\n=== {f} (最后 50 行) ===")
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as file:
            lines = file.readlines()[-50:]
            for line in lines:
                # 打印所有行（不只是关键字）
                print(line.strip())
    except Exception as e:
        print(f"读取失败: {e}")
