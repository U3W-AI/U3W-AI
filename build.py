#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""编译项目"""
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')

result = subprocess.run(
    ["mvn", "clean", "package", "-DskipTests"],
    cwd=r"G:\Interview\wukongshigang\Weihu\U3W-AI",
    capture_output=True,
    text=True,
    encoding='utf-8'
)

print("STDOUT:")
print(result.stdout[-2000:] if len(result.stdout) > 2000 else result.stdout)

print("\nSTDERR:")
print(result.stderr[-1000:] if len(result.stderr) > 1000 else result.stderr)

print(f"\nExit code: {result.returncode}")
