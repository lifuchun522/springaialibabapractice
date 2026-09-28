# -*- coding: utf-8 -*-
"""把十八掌进度同步到 GitHub Projects（V2）。

为什么写成脚本而不是手工点：进度要能随每掌推进重复同步，且「哪一章对应哪个分支/PR/tag」
这份对应关系应该只有一处真源（下面的 CHAPTERS），不然 wiki、README、Project 三处必然漂移。

用法：
    python scripts/sync-github-project.py            # 同步（已存在的条目只更新状态与正文）
    python scripts/sync-github-project.py --dry-run  # 只打印将要做什么
"""
import argparse
import json
import os
import shutil
import subprocess
import sys

OWNER = "lifuchun522"
PROJECT_NUMBER = "1"
PROJECT_ID = "PVT_kwHOE6THGM4Bk5wB"
REPO = "lifuchun522/springaialibabapractice"
REPO_URL = "https://github.com/" + REPO
STATUS_FIELD_ID = "PVTSSF_lAHOE6THGM4Bk5wBzhjolio"
STATUS_OPTIONS = {
    "Done": "98236657",
    "In progress": "47fc9ee4",
    "Backlog": "f75ad846",
}


def gh_bin():
    """优先环境变量 GH_BIN，其次 PATH 里的 gh。

    注意：调用时绝不用 shell=True —— Windows 上它会把参数拼成命令串，
    中文正文里的换行与引号会被拆坏（本项目实测踩过）。
    """
    return os.environ.get("GH_BIN") or shutil.which("gh") or "gh"

# (掌号, 卦象, 主题, 分支, tag, PR 号, 状态, 本章交付)
CHAPTERS = [
    (1, "亢龙有悔", "识势选型", "chapter/01-value-selection", "ch01", 2, "Done",
     "五层架构与选型判据 + 以 ChatClient 为唯一出口的最小骨架（DeepSeek 单通道）"),
    (2, "飞龙在天", "筑基环境", "chapter/02-baseline-env", "ch02", 3, "Done",
     "Maven Wrapper + enforcer 基线门禁（JDK/Maven/依赖收敛）+ 环境自检脚本"),
    (3, "见龙在田", "数字人底座", "chapter/03-digital-human-demo", "ch03", 7, "Done",
     "三张最小表 + 注册登录 + 项目 CRUD + 运行页 + Bridge 契约端点"),
    (4, "鸿渐于陆", "御模对话", "chapter/04-chat-model", "ch04", 9, "Done",
     "provider 进数据 + 模型目录 + ChatOptionsFactory 收口 + 五类模型错误确定响应"),
    (5, "潜龙勿用", "藏忆流式", "chapter/05-memory-streaming", "ch05", 11, "Done",
     "会话记忆（三层 conversationId）+ SSE 流式 + 消息账本四态"),
    (6, "利涉大川", "御器工具", "chapter/06-tools", "ch06", 13, "Done",
     "只读/写工具分流 + enum 白名单 Schema + 人类确认门禁 + 工具审计与超时"),
    (7, "突如其来", "通玄 MCP", "chapter/07-mcp", "ch07", 16, "Done",
     "展厅预约拆成独立 MCP Server + MCP Client 远程发现与调用 + 错误分层"),
    (8, "震惊百里", "入藏 RAG", "chapter/08-rag", "ch08", 19, "Done",
     "项目级知识库 + 元数据契约隔离 + 带出处回答与无据拒答"),
    (9, "或跃在渊", "ReactAgent", "chapter/09-react-agent", "ch09", 26, "Done",
     "ReactAgent 主脑 + 节点事件流 + 模型调用硬上界（显式结束）+ 工具边界有限重试 + 账本随主脑一起落"),
    (10, "双龙取水", "百阵流程", "chapter/10-workflow-agents", "ch10", 35, "Done",
     "四类 Flow Agent 编排层：顺序/并行/路由/循环 + 节点级埋点（聚合视图 + 序列视图）"),
    (11, "鱼跃于渊", "图谱 Graph", "chapter/11-graph-core", "ch11", 38, "Done",
     "售后流程状态图：显式归约策略 + 并行汇合/条件边 + interruptBefore 断点 + MySQL 检查点（重启可恢复）"),
    (12, "时乘六龙", "分身多 Agent", "chapter/12-multi-agent", "ch12", 40, "Done",
     "接待/知识/业务三角色各带自己的提示词、工具与记忆；Router 首跳 + 自主 handoff + max-hops 收敛"),
    (13, "密云不雨", "跨域 A2A", "chapter/13-a2a-nacos", "ch13", 44, "Done",
     "知识 Agent 独立进程 + A2A 协议（能力声明/任务生命周期/流式/版本协商）+ 发现层可换 + traceId 对账"),
    (14, "损则有孚", "溯源源码", "chapter/14-source-pr", "ch14", 46, "Done",
     "源码定位（模块→类→方法→调用者→行号）+ 最小复现 + 符合上游模板的 Issue 草稿 + tag 与 main 的差异记录"),
    (15, "龙战于野", "试炼评测", "chapter/15-eval-guard", None, None, "Backlog", "待做"),
    (16, "履霜冰至", "立派服务", "chapter/16-spring-service", None, None, "Backlog", "待做"),
    (17, "羝羊触藩", "观星治理", "chapter/17-observability-admin", None, None, "Backlog", "待做"),
    (18, "神龙摆尾", "登云 K8s", "chapter/18-k8s-production", None, None, "Backlog", "待做"),
]


def gh(*args):
    result = subprocess.run([gh_bin(), *args], capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        raise RuntimeError("gh %s 失败：%s" % (" ".join(args), (result.stderr or result.stdout).strip()))
    return result.stdout


def item_title(n, gua, topic):
    return "ch%02d · %s · %s" % (n, gua, topic)


def item_body(n, gua, topic, branch, tag, pr, delivery):
    lines = ["## 第 %d 掌 · %s · %s" % (n, gua, topic), ""]
    lines.append("| 项 | 值 |")
    lines.append("|----|----|")
    lines.append("| 分支 | `%s` |" % branch)
    if tag:
        lines.append("| 标签 | `%s` |" % tag)
    if pr:
        lines.append("| PR | [#%d](%s/pull/%d) |" % (pr, REPO_URL, pr))
    lines.append("| 设计文档 | [docs/ch%02d-%s.md](%s/blob/main/docs) |" % (n, topic, REPO_URL))
    lines.append("| 验收记录 | [docs/ch%02d-验收记录.md](%s/blob/main/docs) |" % (n, REPO_URL))
    lines.append("| Wiki | [%s](%s/wiki)" % ("章节文档", REPO_URL + "/wiki"))
    lines.append("")
    lines.append("**本章交付**：%s" % delivery)
    if pr:
        lines.append("")
        lines.append("**验收**：见验收记录（含真实环境原始输出）。")
    return "\n".join(lines)


def existing_items():
    raw = gh("project", "item-list", PROJECT_NUMBER, "--owner", OWNER, "--format", "json", "--limit", "100")
    data = json.loads(raw)
    return {item["title"]: item["id"] for item in data.get("items", [])}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    existing = {} if args.dry_run else existing_items()
    created, updated = 0, 0

    for n, gua, topic, branch, tag, pr, status, delivery in CHAPTERS:
        title = item_title(n, gua, topic)
        body = item_body(n, gua, topic, branch, tag, pr, delivery)

        if title in existing:
            item_id = existing[title]
        else:
            if args.dry_run:
                print("[dry-run] 新建条目：%s（状态 %s）" % (title, status))
                continue
            out = gh("project", "item-create", PROJECT_NUMBER, "--owner", OWNER,
                     "--title", title, "--body", body, "--format", "json")
            item_id = json.loads(out)["id"]
            created += 1

        if args.dry_run:
            print("[dry-run] 设置状态：%s → %s" % (title, status))
            continue

        gh("project", "item-edit", "--id", item_id, "--project-id", PROJECT_ID,
           "--field-id", STATUS_FIELD_ID, "--single-select-option-id", STATUS_OPTIONS[status])
        updated += 1

    print("同步完成：新建 %d 个条目，更新 %d 个条目状态" % (created, updated))
    return 0


if __name__ == "__main__":
    sys.exit(main())
