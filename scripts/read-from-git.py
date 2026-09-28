# -*- coding: utf-8 -*-
"""用 git cat-file 取出指定版本的若干文件，按 UTF-8/LF 写回工作区。

不要用 PowerShell 的 `git show > file`：PowerShell 5.1 的重定向会写成 UTF-16LE，
Python 再按 UTF-8 读就炸（实测踩过：README 变成 0xFF 开头）。
"""
import subprocess
import sys

REF = sys.argv[1]
FILES = sys.argv[2:]

for f in FILES:
    blob = subprocess.run(["git", "cat-file", "-p", "%s:%s" % (REF, f)],
                          stdout=subprocess.PIPE, check=True).stdout
    text = blob.decode("utf-8")
    open(f, "w", encoding="utf-8", newline="\n").write(text)
    print("%-28s %5d 行  ``` x%d" % (f, len(text.splitlines()), text.count("```")))
