# -*- coding: utf-8 -*-
"""架构图一致性自检：把「图上少了组件」这类问题变成一条可执行的门禁。

这个脚本存在的理由是一次真实偏差：架构图里少画了 5 个组件
（livekit-agent / livekit-server / tts / asr / nacos），
而当时没有任何一道检查会发现它——图是散文，散文不会失败。

它检查四件事，全部只依赖标准库，可以在 CI 里跑：

1. 必需组件出现在指定文件的 mermaid 代码块里（逐个点名，缺一个就报出来）
2. 中英两份 README 的节点标识集合一致（防止中英架构漂移）
3. 每张图的图注存在（图注回答的是「这张图解决什么问题」）
4. 被标记为「预留」的组件必须同时出现在非交付清单里（防止把占位读成已交付）

用法：
    python scripts/check-diagrams.py            # 检查默认目标
    python scripts/check-diagrams.py --json     # 机器可读输出

退出码：0 全部通过；1 有失败项。
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DOC = "docs/ch19-数字人功能升级.md"
README_ZH = "README.md"
README_EN = "README.en.md"

# 这些组件是本掌必须画进架构图的东西。少一个就算失败——
# 这条清单就是「架构图少了 5 个组件」那次偏差的回归用例。
# redis 是后补的第 6 项：它是 livekit-server / livekit-agent 的集群控制（多副本前置条件），
# 同样属于「预留但必须画出来」的组件，所以一并纳入门禁。
REQUIRED_COMPONENTS = {
    "livekit-agent": ["livekit-agent", "livekit agent"],
    "livekit-server": ["livekit-server", "livekit server"],
    "tts": ["TTS"],
    "asr": ["ASR"],
    "nacos": ["Nacos", "nacos"],
    "redis": ["Redis", "redis"],
}

# 每个组件至少要出现的图（文件名 -> 组件名集合）
EXPECTED_IN = {
    DOC: set(REQUIRED_COMPONENTS.keys()),
    README_ZH: set(REQUIRED_COMPONENTS.keys()),
    README_EN: set(REQUIRED_COMPONENTS.keys()),
}

LANE_MARKERS = ["管理链路", "对话链路", "注册链路"]
LANE_MARKERS_EN = ["admin lane", "conversation lane", "registry lane"]


def read(rel):
    path = os.path.join(ROOT, rel)
    if not os.path.exists(path):
        return None
    return io.open(path, encoding="utf-8").read()


def mermaid_blocks(text):
    return re.findall(r"```mermaid\n(.*?)```", text, re.S)


def node_ids(block):
    """取出 flowchart 里显式声明的节点标识（形如 ID["label"] 或 ID{"label"}）。"""
    return set(re.findall(r'^\s*([A-Za-z][A-Za-z0-9_]*)\s*[\[\(\{]', block, re.M))


class Report(object):
    def __init__(self):
        self.checks = []

    def add(self, name, ok, detail=""):
        self.checks.append({"check": name, "ok": bool(ok), "detail": detail})

    @property
    def failed(self):
        return [c for c in self.checks if not c["ok"]]


def main():
    as_json = "--json" in sys.argv
    rep = Report()

    texts = {}
    for rel in (DOC, README_ZH, README_EN):
        texts[rel] = read(rel)
        rep.add("文件存在：%s" % rel, texts[rel] is not None)

    # 1. 必需组件逐个点名
    for rel, required in EXPECTED_IN.items():
        text = texts.get(rel)
        if text is None:
            continue
        joined = "\n".join(mermaid_blocks(text))
        for comp in sorted(required):
            aliases = REQUIRED_COMPONENTS[comp]
            hit = next((a for a in aliases if a in joined), None)
            rep.add(
                "%s 的图中含组件 %s" % (rel, comp),
                hit is not None,
                "" if hit else "在 mermaid 代码块中找不到别名 %s" % aliases,
            )

    # 2. 中英 README 节点集合一致
    zh_nodes = set()
    en_nodes = set()
    for block in mermaid_blocks(texts.get(README_ZH) or ""):
        zh_nodes |= node_ids(block)
    for block in mermaid_blocks(texts.get(README_EN) or ""):
        en_nodes |= node_ids(block)
    only_zh = sorted(zh_nodes - en_nodes)
    only_en = sorted(en_nodes - zh_nodes)
    rep.add(
        "中英 README 架构图节点集合一致",
        not only_zh and not only_en,
        "仅中文有 %s；仅英文有 %s" % (only_zh, only_en),
    )

    # 3. 三条链路都要在图上被指名
    zh_text = texts.get(README_ZH) or ""
    en_text = texts.get(README_EN) or ""
    for marker in LANE_MARKERS:
        rep.add("中文 README 声明链路：%s" % marker, marker in "".join(mermaid_blocks(zh_text)) or marker in zh_text)
    for marker in LANE_MARKERS_EN:
        rep.add("英文 README 声明链路：%s" % marker, marker in "".join(mermaid_blocks(en_text)) or marker in en_text)

    # 4. 预留组件必须在非交付清单里也出现（防止虚线被读成已交付）
    doc = texts.get(DOC) or ""
    non_delivery = re.search(r"## 八、明确不交付(.*?)(?:\n## |\Z)", doc, re.S)
    rep.add("ch19 文档含「明确不交付」章节", non_delivery is not None)
    if non_delivery:
        body = non_delivery.group(1)
        for comp in ("livekit-server", "livekit-agent", "Redis"):
            rep.add("非交付清单点名 %s" % comp, comp in body)

    # 5. 图注：每张图后面都应有说明它回答什么问题的文字
    for rel in (DOC, README_ZH, README_EN):
        text = texts.get(rel)
        if text is None:
            continue
        blocks = mermaid_blocks(text)
        missing = []
        for i, block in enumerate(blocks, 1):
            after = text.split(block, 1)[-1][:1200] if block in text else ""
            if not re.search(r"图注|This diagram|answers|这张图", after):
                missing.append(i)
        rep.add(
            "%s 每张图都有图注" % rel,
            not missing,
            "缺图注的图序号：%s" % missing if missing else "",
        )

    if as_json:
        print(json.dumps(
            {"checks": rep.checks, "failed": len(rep.failed), "total": len(rep.checks)},
            ensure_ascii=False, indent=2,
        ))
    else:
        for c in rep.checks:
            print("%s  %s%s" % ("PASS" if c["ok"] else "FAIL", c["check"],
                                ("  <- " + c["detail"]) if c["detail"] else ""))
        print("\n合计 %d 项，失败 %d 项" % (len(rep.checks), len(rep.failed)))

    return 1 if rep.failed else 0


if __name__ == "__main__":
    sys.exit(main())
