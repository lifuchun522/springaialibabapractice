#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""docs/github-ops 索引死链门禁（Issue #61 / 第 14 保）。

为什么需要它：`docs/github-ops/README.md` 是十三保的索引，一旦文件名变更就会出现死链，
而死链**不会让任何东西失败**——只会在读者点进去时才发现。同类问题在架构图上已经真实发生过一次
（README 的架构图少画 5 个组件，当时没有检查能发现），所以这里按同样的思路补一道门禁。

范围与边界：
  - 校验**相对链接**指向的文件是否真实存在（`x.md`、`./x.md`、`x/y.md`、`x.md#anchor`）；
  - 跨目录相对链接（`../../scripts/xxx.sh`）同样校验；
  - 站内绝对链接（`/blob/main/...`、`/issues/1`）与 http(s) 外链**不校验**：
    前者需要鉴权、后者需要网络，两者都会让门禁因外部因素红灯，而红灯不该来自外部抖动；
  - 只依赖标准库，与 scripts/check-diagrams.py、check-md.py 保持同一风格。

用法：
  python3 scripts/check-github-ops-links.py            # 校验，死链则非零退出
  python3 scripts/check-github-ops-links.py --verbose  # 打印每个被校验的链接
"""

from __future__ import annotations

import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCAN_DIR = os.path.join(ROOT, "docs", "github-ops")
SELF = os.path.relpath(os.path.abspath(__file__), ROOT).replace(os.sep, "/")

# Markdown 行内链接：[文字](目标) 与图片 ![alt](目标)；不匹配引用式链接（[a][b]），本目录未使用。
LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
# 需要跳过的目标前缀
SKIP_PREFIX = ("http://", "https://", "mailto:", "tel:", "#", "/", "data:")


def is_local(target: str) -> bool:
    return not target.startswith(SKIP_PREFIX)


def resolve(src_file: str, target: str) -> str:
    path = target.split("#", 1)[0]
    path = path.split("?", 1)[0]
    if not path:
        return ""
    if path.startswith("/"):
        # 仓库根相对
        return os.path.join(ROOT, path.lstrip("/"))
    return os.path.normpath(os.path.join(os.path.dirname(src_file), path))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    if not os.path.isdir(SCAN_DIR):
        print("[links] 失败：找不到 %s" % SCAN_DIR, file=sys.stderr)
        return 1

    checked = 0
    broken: list[str] = []
    md_files = []
    for dirpath, dirnames, filenames in os.walk(SCAN_DIR):
        dirnames[:] = [d for d in dirnames if d != "raw"]
        for name in filenames:
            if name.endswith(".md"):
                md_files.append(os.path.join(dirpath, name))
    md_files.sort()

    for md_file in md_files:
        rel_md = os.path.relpath(md_file, ROOT).replace(os.sep, "/")
        if rel_md == SELF:
            continue
        with open(md_file, encoding="utf-8") as fh:
            for lineno, line in enumerate(fh, start=1):
                for raw in LINK_RE.findall(line):
                    target = raw.strip().strip("<>")
                    if not is_local(target):
                        continue
                    resolved = resolve(md_file, target)
                    if not resolved:  # 纯锚点
                        continue
                    checked += 1
                    ok = os.path.exists(resolved)
                    if args.verbose:
                        print("[links] %s: %s -> %s" % ("OK " if ok else "BAD", rel_md, target))
                    if not ok:
                        broken.append("%s:%d -> %s" % (rel_md, lineno, target))

    print("[links] 扫描 %d 个 Markdown，校验 %d 个相对链接" % (len(md_files), checked))
    if broken:
        print("[links] 失败：发现 %d 个死链" % len(broken), file=sys.stderr)
        for item in broken:
            print("        " + item, file=sys.stderr)
        return 1
    print("[links] 通过：无死链")
    return 0


if __name__ == "__main__":
    sys.exit(main())
