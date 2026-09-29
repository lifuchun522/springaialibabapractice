#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""工作流 YAML 门禁（第 08 保）。

起因是一次真实的 startup_failure：`.github/workflows/gh-ops-weekly.yml` 里把
`upload-artifact` 的 `path` 写成了带 shell 命令替换的形式（`*-$(date ...)-*.json`），
GitHub 在**启动阶段**就让整个运行失败：日志为空、`jobs` 为空数组，
`gh run view --log-failed` 报 `log not found`。这类失败最难查——
没有任何日志指向真正的原因，只能靠逐个文件比对。

所以这道门禁做两件事：
  1. 每个 `.github/workflows/*.yml` 必须能被 YAML 解析成映射，且含 `jobs`；
  2. 检查几个已知的「启动即失败」形态：
     - `on` 与 `jobs` 必须存在（缺一个 GitHub 直接不认这个工作流）；
     - `with:` 下的 `path:` / `name:` 值里不能出现 `$(`（shell 命令替换不会被展开，
       某些动作还会因为非法模式直接失败）；
     - `jobs.<id>.runs-on` 必须存在（缺失时工作流无法排队）。

只依赖标准库：内置一个极简 YAML 解析器不够可靠，因此本脚本用**结构化文本检查**
而不是完整解析——检查的是「启动即失败」的最小充分条件，而不是 YAML 合规性。
（仓库里没有 PyYAML 依赖，也不打算为门禁引入它。）

用法：
  python3 scripts/check-workflows.py            # 校验，问题则非零退出
  python3 scripts/check-workflows.py --verbose  # 打印每个文件的检查明细
"""

from __future__ import annotations

import argparse
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(ROOT, ".github", "workflows")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    files = sorted(glob.glob(os.path.join(WF_DIR, "*.yml")) + glob.glob(os.path.join(WF_DIR, "*.yaml")))
    if not files:
        print("[wf] 失败：%s 下没有工作流文件" % WF_DIR, file=sys.stderr)
        return 1

    problems: list[str] = []
    for path in files:
        rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        lines = text.splitlines()

        # 1) 顶层 on: 与 jobs:
        if not re.search(r"^on:", text, re.M):
            problems.append("%s: 缺少顶层 `on:` —— GitHub 不会识别这个工作流" % rel)
        if not re.search(r"^jobs:", text, re.M):
            problems.append("%s: 缺少顶层 `jobs:`" % rel)

        # 2) runs-on 是否在每个 job 下
        # 注意：job id 的正则（两空格缩进的 `key:`）也会匹配到嵌套块（如 `permissions:` 下的键），
        # 所以「job 数」只是粗略指示，不做断言；真正的断言是「两空格缩进的块里必须有 runs-on 或 uses」。
        # 这也是本脚本的目标定位：只检查「启动即失败」的最小充分条件，不做完整 YAML 解析。
        job_ids = re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", text, re.M)
        # 只统计 jobs: 之后的部分
        jobs_idx = next((i for i, ln in enumerate(lines) if ln.startswith("jobs:")), None)
        if jobs_idx is not None:
            job_block = "\n".join(lines[jobs_idx:])
            for job in re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", job_block, re.M):
                seg = job_block.split("\n  %s:" % job, 1)[-1]
                if "runs-on:" not in seg and "uses:" not in seg:
                    problems.append("%s: job `%s` 既没有 runs-on 也没有 uses" % (rel, job))

        # 3) 动作参数里的 shell 命令替换
        for m in re.finditer(r"^\s+(path|name):\s*(.+)$", text, re.M):
            value = m.group(2).strip()
            if "$(" in value:
                lineno = text[: m.start()].count("\n") + 1
                problems.append(
                    "%s:%d: `%s:` 里出现 shell 命令替换 `$(` —— 动作参数不做 shell 展开，会启动即失败"
                    % (rel, lineno, m.group(1))
                )

        # 4) 多行 --body "..." 续行（会让 YAML 块标量缩进错乱）
        if re.search(r'--body\s+"[^"]*$', text, re.M):
            lineno = text[: re.search(r'--body\s+"[^"]*$', text, re.M).start()].count("\n") + 1
            problems.append(
                "%s:%d: `--body \"` 后面接了跨行内容，容易把 YAML 缩进搞乱导致 startup_failure；"
                "改用 `--body-file <临时文件>`" % (rel, lineno)
            )

        if args.verbose:
            print("[wf] 检查 %s：%d 行，%d 个 job" % (rel, len(lines), len(job_ids)))

    print("[wf] 扫描 %d 个工作流文件" % len(files))
    if problems:
        print("[wf] 失败：发现 %d 个问题" % len(problems), file=sys.stderr)
        for p in problems:
            print("        " + p, file=sys.stderr)
        return 1
    print("[wf] 通过：没有发现「启动即失败」形态的问题")
    return 0


if __name__ == "__main__":
    sys.exit(main())
