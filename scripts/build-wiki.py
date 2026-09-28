# -*- coding: utf-8 -*-
"""把仓库里的章节文档整理成 GitHub Wiki 页面（Home + 每章一页）。"""
import os
import re

REPO = r"D:\src\github\springaialibabapractice"
DOCS = os.path.join(REPO, "docs")
OUT = r"D:\src\github\dh-wiki-staging"
REPO_URL = "https://github.com/lifuchun522/springaialibabapractice"

CHAPTERS = [
    (1, "亢龙有悔", "识势选型", "chapter/01-value-selection", "ch01", 1, "2752108"),
    (2, "飞龙在天", "筑基环境", "chapter/02-baseline-env", "ch02", 3, "2752106"),
    (3, "见龙在田", "数字人底座", "chapter/03-digital-human-demo", "ch03", 7, "2752105"),
    (4, "鸿渐于陆", "御模对话", "chapter/04-chat-model", "ch04", 9, "2752104"),
    (5, "潜龙勿用", "藏忆流式", "chapter/05-memory-streaming", "ch05", 11, "2752103"),
    (6, "利涉大川", "御器工具", "chapter/06-tools", "ch06", 13, "2752102"),
    (7, "突如其来", "通玄 MCP", "chapter/07-mcp", "ch07", 16, "2752101"),
    (8, "震惊百里", "入藏 RAG", "chapter/08-rag", "ch08", 19, "2752097"),
    (9, "或跃在渊", "ReactAgent", "chapter/09-react-agent", "ch09", 26, "2752096"),
    (10, "双龙取水", "百阵流程", "chapter/10-workflow-agents", "ch10", 35, "2752095"),
    (11, "鱼跃于渊", "图谱Graph", "chapter/11-graph-core", "ch11", 38, "2752094"),
    (12, "时乘六龙", "分身多Agent", "chapter/12-multi-agent", "ch12", 40, "2752093"),
]

DELIVERED = {
    1: "五层架构与选型判据 + 以 ChatClient 为唯一出口的最小骨架（DeepSeek 单通道）",
    2: "Maven Wrapper + enforcer 基线门禁（JDK/Maven/依赖收敛）+ 新机器环境自检脚本",
    3: "三张最小表 + 注册登录 + 项目 CRUD + 运行页 + 文本问答与 Bridge 契约端点",
    4: "provider 进数据 + 模型目录 + ChatOptionsFactory 收口 + 五类模型错误确定响应",
    5: "会话记忆（conversationId 三层键）+ SSE 流式 + 消息账本四态（完成/取消/失败）",
    6: "只读/写工具分流 + enum 白名单 Schema + 人类确认门禁 + 工具审计与超时边界",
    7: "展厅预约拆成独立 MCP Server + MCP Client 远程发现与调用 + 错误分层",
    8: "项目级知识库：元数据契约（projectId/docName/chunkIndex）+ 过滤检索 + 系统渲染出处 + 无依据拒答（不调模型）",
    9: "ReactAgent 主脑 + 节点事件流 + 模型调用硬上界（显式结束）+ 工具边界有限重试 + 账本随主脑一起落",
    10: "四类 Flow Agent 编排层：顺序（理解→检索→回答）、并行（知识库∥业务系统 + 显式归并）、路由（售前/售后/兜底，单次分类）、循环（追问有界）+ 节点级埋点",
    11: "售后流程状态图：显式归约策略（APPEND/REPLACE）、并行汇合与条件边、interruptBefore 断点、MySQL 检查点（重启后可恢复）+ 图结构导出",
    12: "多 Agent 协作：接待/知识/业务三角色各带自己的提示词、工具与记忆；Router 定首跳、角色自主 handoff、max-hops 收敛",
}

OUTSTANDING = {
    1: "会话未持久化、无流式、无工具与 RAG、无追踪评测",
    2: "IDEA 断点与 Reactor 调试参数未自动化；无密钥时不能降级启动",
    3: "LiveKit 实时语音未接；令牌为进程内存态（Demo 级）",
    4: "超时/限流只有单测覆盖；第二家 provider 未真实接入；无流式",
    5: "落库仍在响应式回调里阻塞；Memory 仍为内存态；无断线续传与 token 预算",
    6: "REJECTED 审计态未真实产生；审计表无归档分页；工具结果未进账本；超时值全局一刀切",
    7: "MCP Server 无认证；地址仍硬编码配置；无重试熔断；工具描述无变更评审",
    8: "向量化是词法实现（不理解同义）；内存向量库不跨实例；FAQ 可能被切开；单跳检索；无评测集",
    9: "每请求重建 Agent（含图编译）；无跨请求记忆；事件非流式；上界值待评测决定；重试无退避",
    10: "无评测集（命中率受标注口径影响）；循环在同步请求里转不出新信息；并行只支持两条固定分支；编排仍单进程",
    11: "节点间输出契约未完全解决（Agent 节点产物是包装对象，读不到就写「没取到」）；图结构未版本化；检查点只增不删；只支持一种中断语义",
    12: "角色记忆是进程内的（重启即空）；Router 单点误判；交接时该带哪些状态未定义；低置信度兜底未做",
}


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def section(text, keyword):
    """取某个二级标题下的内容（到下一个二级标题为止）。"""
    pattern = re.compile(r"^##\s*(?:\d+、)?\s*" + re.escape(keyword) + r".*$", re.M)
    match = pattern.search(text)
    if not match:
        return ""
    rest = text[match.end():]
    nxt = re.search(r"^##\s", rest, re.M)
    return (rest[:nxt.start()] if nxt else rest).strip()


def first_section(text):
    """取第一个二级标题下的内容。"""
    match = re.search(r"^##\s.*$", text, re.M)
    if not match:
        return ""
    rest = text[match.end():]
    nxt = re.search(r"^##\s", rest, re.M)
    return (rest[:nxt.start()] if nxt else rest).strip()


os.makedirs(OUT, exist_ok=True)
written = []

home = ["# 降 SpringAI 阿里 十八掌 · 实践仓库文档", ""]
home.append("本 Wiki 记录这个实践仓库的**背景**与**每一掌的交付与验收**，正文设计文档与验收记录在仓库的 `docs/` 下。")
home.append("")
home.append("## 一、这个仓库在做什么")
home.append("")
home.append("它跟着腾讯云开发者社区的系列文章《降 SpringAI 阿里》十八掌，"
            "把一套能进企业系统的 Java AI 应用**从最小骨架逐掌长成数字人 Agent 平台**。")
home.append("")
home.append("系列文章：<https://cloud.tencent.com/developer/article/2752108>")
home.append("")
home.append("每一掌都按同一套节奏推进，不留「应该能通」：")
home.append("")
home.append("```text")
home.append("Issue（本章要落的能力与验收标准）")
home.append("   └─ 分支 chapter/NN-主题   ← 只做这一章")
home.append("        └─ PR（关联 Issue，贴真实验证证据）")
home.append("             └─ 合并进 main ＋ 打 tag（chNN）")
home.append("```")
home.append("")
home.append("## 二、技术基线（可核验，不是抄文档）")
home.append("")
home.append("| 项 | 取值 | 怎么钉住的 |")
home.append("|----|------|-----------|")
home.append("| JDK | 21 LTS | enforcer `requireJavaVersion [21,22)` |")
home.append("| Maven | 3.9.11 | 仓库自带 `./mvnw`，enforcer `requireMavenVersion [3.9,)` |")
home.append("| Spring Boot | 3.5.10 | 与 SAA 1.1.2.2 的 POM 对齐 |")
home.append("| Spring AI | 1.1.2 | `spring-ai-bom` 统一管理 |")
home.append("| Spring AI Alibaba | 1.1.2.2 | `spring-ai-alibaba-bom` + `extensions-bom` |")
home.append("| 模型通道 | DeepSeek（OpenAI 兼容） | 只留一条**可验证**的通道 |")
home.append("| 依赖一致性 | enforcer `dependencyConvergence` | 已三次拦住真实版本分叉 |")
home.append("")
home.append("## 三、仓库结构")
home.append("")
home.append("```text")
home.append("pom.xml                 父 POM：BOM 统一版本 + enforcer 基线门禁")
home.append("mvnw / mvnw.cmd         Maven Wrapper：把 Maven 版本钉在仓库里")
home.append("digital-human/          数字人应用（ChatClient 出口、工具、记忆、MCP Client）")
home.append("digital-human-mcp/      展厅预约 MCP Server：独立进程、独立库")
home.append("scripts/env-check.ps1   新机器环境自检")
home.append("docs/                   每掌的设计文档与验收记录")
home.append("```")
home.append("")
home.append("## 四、怎么跑起来")
home.append("")
home.append("```bash")
home.append("# 测试（离线，内存库，不需要外部依赖）")
home.append("./mvnw -B -ntp clean test")
home.append("")
home.append("# 运行（需要一个 MySQL 8；第 7 掌起还要起 MCP Server）")
home.append("docker run -d --name dh-mysql -e MYSQL_ROOT_PASSWORD=root \\")
home.append("  -e MYSQL_DATABASE=digital_human -p 3306:3306 mysql:8")
home.append("export DEEPSEEK_API_KEY=sk-xxxx")
home.append("./mvnw -pl digital-human spring-boot:run")
home.append("```")
home.append("")
home.append("## 五、章节进度")
home.append("")
home.append("| 掌 | 卦象 · 主题 | 分支 | 标签 | 本章交付 |")
home.append("|----|-------------|------|------|----------|")
for n, gua, topic, branch, tag, pr, _ in CHAPTERS:
    home.append("| %d | [%s · %s](%s) | `%s` | `%s` | %s |"
                % (n, gua, topic, "ch%02d-%s" % (n, topic.replace(" ", "-")), branch, tag, DELIVERED[n]))
home.append("| 10～18 | 待做 |  |  |  |")
home.append("")
home.append("## 六、写在最前面的三条判断")
home.append("")
home.append("1. **模型只写参数，代码才动数据**（第 6 掌）：所有安全边界只能画在自己的代码里。")
home.append("2. **一个能力该不该拆成服务，看调用方有几个**（第 7 掌）：只有一个调用方时，拆是纯成本。")
home.append("3. **失败要显式**：200 但内容为空、工具静默为空、取消没有留痕——这三类「看起来没事」的失败，"
            "都比报错更难查，所以每一掌都把它们变成确定的行为。")
home.append("")
home.append("---")
home.append("")
home.append("仓库地址：<%s> ｜ 每掌的完整证据在 `docs/chNN-验收记录.md`" % REPO_URL)

with open(os.path.join(OUT, "Home.md"), "w", encoding="utf-8") as handle:
    handle.write("\n".join(home) + "\n")
written.append("Home.md")

for n, gua, topic, branch, tag, pr, article in CHAPTERS:
    design_path = os.path.join(DOCS, "ch%02d-%s.md" % (n, topic))
    accept_path = os.path.join(DOCS, "ch%02d-验收记录.md" % n)
    design = read(design_path) if os.path.exists(design_path) else ""
    accept = read(accept_path) if os.path.exists(accept_path) else ""

    page = []
    page.append("# 第 %d 掌 · %s · %s" % (n, gua, topic))
    page.append("")
    page.append("| 项 | 值 |")
    page.append("|----|----|")
    page.append("| 系列文章 | <https://cloud.tencent.com/developer/article/%s> |" % article)
    page.append("| 分支 | `%s` |" % branch)
    page.append("| PR | [#%d](%s/pull/%d) |" % (pr, REPO_URL, pr))
    page.append("| 标签 | `%s` |" % tag)
    page.append("| 设计文档 | [docs/ch%02d-%s.md](%s/blob/main/docs) |"
                % (n, topic, REPO_URL))
    page.append("| 验收记录 | [docs/ch%02d-验收记录.md](%s/blob/main/docs) |" % (n, REPO_URL))
    page.append("")
    page.append("## 本章交付")
    page.append("")
    page.append(DELIVERED[n])
    page.append("")

    problem = section(design, "一、") or first_section(design)
    if problem:
        page.append("## 这一掌要解决的问题")
        page.append("")
        page.append(problem)
        page.append("")

    if accept:
        page.append("## 验收记录（节选自查，完整证据见仓库文档）")
        page.append("")
        first = first_section(accept)
        page.append(first)
        page.append("")
        findings = section(accept, "核验发现与踩坑") or section(accept, "核验中发现的与文章不一致之处")
        if findings:
            page.append("## 核验发现与踩坑")
            page.append("")
            page.append(findings)
            page.append("")
        todo = section(accept, "本掌遗留问题")
        if todo:
            page.append("## 遗留问题")
            page.append("")
            page.append(todo)
            page.append("")

    page.append("---")
    page.append("")
    # 下一掌的页面只在它已经生成时才给链接，否则 wiki 上会留一堆点不开的红链
    done = [n for n, *_ in CHAPTERS]
    if n + 1 in done:
        page.append("返回 [Home](Home) ｜ 下一掌：[第 %d 掌](%s)"
                    % (n + 1, "ch%02d-%s" % (n + 1, dict((c[0], c[2]) for c in CHAPTERS)[n + 1])))
    else:
        page.append("返回 [Home](Home) ｜ 下一掌：第 %d 掌（待做）" % (n + 1))
    page.append("")
    page.append("> 本掌与文章口径的差异、以及实测中发现的坑，都写在仓库的 `docs/ch%02d-验收记录.md` 里，不做粉饰。" % n)

    name = "ch%02d-%s.md" % (n, topic.replace(" ", "-"))
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as handle:
        handle.write("\n".join(page) + "\n")
    written.append(name)

print("生成 %d 个 wiki 页面：" % len(written))
for name in written:
    print(" -", name)
