# -*- coding: utf-8 -*-
"""结构体检：把这次重排最容易被静默搞坏的东西全部打出来。"""
import io
import re
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "README.md"
text = io.open(path, encoding="utf-8").read()

print("行数：%d" % len(text.splitlines()))
print("``` 出现次数：%d（应为偶数）" % text.count("```"))
print("`` 出现次数：%d（应为 0）" % text.count("``"))

print("\n--- 围栏行 ---")
for i, line in enumerate(text.splitlines(), 1):
    if line.startswith("```"):
        print("%4d %s" % (i, line))

print("\n--- 标题 ---")
for i, line in enumerate(text.splitlines(), 1):
    if re.match(r"^#{1,3} ", line):
        print("%4d %s" % (i, line))

print("\n--- 表格行数（按表头统计）---")
blocks = re.findall(r"(?:^\|.*\|\s*$\n)+", text, re.M)
for b in blocks:
    first = b.splitlines()[0]
    print("%d 行  %s" % (len(b.strip().splitlines()) - 2, first[:70]))
