# -*- coding: utf-8 -*-
"""把仓库里的章节文档整理成 GitHub Wiki 页面（Home + 每章一页）。

Home 页取自 `docs/系列导读.md`（系列导读的唯一来源：正文手写，第五掌的索引表
由本脚本生成），章节页取自 `docs/chNN-*.md` 与 `docs/chNN-验收记录.md`。
生成结果推到 Wiki 仓（`*.wiki.git`）的 master 分支；不要在 Wiki 上直接改，
下次生成会覆盖。
"""
import os
import re

REPO = r"D:\src\github\springaialibabapractice"
DOCS = os.path.join(REPO, "docs")
OUT = r"D:\src\github\dh-wiki-staging"
REPO_URL = "https://github.com/lifuchun522/springaialibabapractice"
WIKI_URL = REPO_URL + "/wiki"
ARTICLE_URL = "https://cloud.tencent.com/developer/article/%s"
VIDEO_URL = "https://cloud.tencent.com/developer/video/%s"

# 掌号, 卦象, 主题, 分支, 标签, PR, 文章 ID, 视频 ID
CHAPTERS = [
    (1, "亢龙有悔", "识势选型", "chapter/01-value-selection", "ch01", 1, "2752108", "87798"),
    (2, "飞龙在天", "筑基环境", "chapter/02-baseline-env", "ch02", 3, "2752106", "87796"),
    (3, "见龙在田", "数字人底座", "chapter/03-digital-human-demo", "ch03", 7, "2752105", "87794"),
    (4, "鸿渐于陆", "御模对话", "chapter/04-chat-model", "ch04", 9, "2752104", "87793"),
    (5, "潜龙勿用", "藏忆流式", "chapter/05-memory-streaming", "ch05", 11, "2752103", "87792"),
    (6, "利涉大川", "御器工具", "chapter/06-tools", "ch06", 13, "2752102", "87791"),
    (7, "突如其来", "通玄 MCP", "chapter/07-mcp", "ch07", 16, "2752101", "87790"),
    (8, "震惊百里", "入藏 RAG", "chapter/08-rag", "ch08", 19, "2752097", "87789"),
    (9, "或跃在渊", "ReactAgent", "chapter/09-react-agent", "ch09", 26, "2752096", "87788"),
    (10, "双龙取水", "百阵流程", "chapter/10-workflow-agents", "ch10", 35, "2752095", "87787"),
    (11, "鱼跃于渊", "图谱Graph", "chapter/11-graph-core", "ch11", 38, "2752094", "87786"),
    (12, "时乘六龙", "分身多Agent", "chapter/12-multi-agent", "ch12", 40, "2752093", "87785"),
    (13, "密云不雨", "跨域A2A", "chapter/13-a2a-nacos", "ch13", 44, "2752092", "87784"),
]

# 第 13～18 掌文章与视频都已发布，但配套代码与验收记录要等章节落地：只进 Home 的
# 索引表（链回腾讯云原文），不生成会点不开的 Wiki 页面。
PENDING = [
    (14, "损则有孚", "溯源源码", "chapter/14-source-pr", "2752091", "87783"),
    (15, "龙战于野", "试炼评测", "chapter/15-eval-guard", "2752089", "87782"),
    (16, "履霜冰至", "立派服务", "chapter/16-spring-service", "2752087", "87781"),
    (17, "羝羊触藩", "观星治理", "chapter/17-observability-admin", "2752086", "87797"),
    (18, "神龙摆尾", "登云K8s", "chapter/18-k8s-production", "2752084", "87795"),
]

# 导读正文的唯一来源；第五掌的索引表在这里被替换成下面生成的内容
GUIDE = os.path.join(DOCS, "系列导读.md")
GUIDE_INDEX_MARK = "<!-- WIKI-CHAPTER-INDEX -->"

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
    13: "跨服务 A2A：知识 Agent 独立进程（独立库、独立 jar）；能力声明 + 任务生命周期 + 流式 + 版本协商；发现层可换（Nacos / 静态表），贯穿 traceId 可对账",
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
    13: "本环境未起 Nacos（多实例用静态表验证）；任务表在内存；流式只做服务端分片；无契约灰度策略",
}


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def write(path, lines):
    """统一写 LF：Wiki 仓里换行符就是 LF，避免 Windows 上写出 CRLF 再被 git 改写。"""
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")


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


def page_name(n, topic):
    return "ch%02d-%s.md" % (n, topic.replace(" ", "-"))


def chapter_index_table():
    """18 掌索引表：有 Wiki 页面的掌链到 Wiki，其余链回腾讯云原文。"""
    rows = ["| 掌 | 主题 | 这一掌交付什么 | 视频（腾讯云社区） | 文章（腾讯云社区） |",
            "| --- | --- | --- | --- | --- |"]
    for n, gua, topic, branch, tag, pr, article, video in CHAPTERS:
        rows.append("| %d | %s · %s | %s | [▶ 视频](%s) | [第%d掌 · Wiki](%s) ｜ [原文](%s) |"
                    % (n, gua, topic, DELIVERED[n], VIDEO_URL % video, n,
                       page_name(n, topic)[:-3], ARTICLE_URL % article))
    for n, gua, topic, branch, article, video in PENDING:
        rows.append("| %d | %s · %s | 待做（文章与视频已发布） | [▶ 视频](%s) | [第%d掌](%s) |"
                    % (n, gua, topic, VIDEO_URL % video, n, ARTICLE_URL % article))
    return "\n".join(rows)


# Home 页 = docs/系列导读.md（正文） + 仓库侧的可核验信息
home = read(GUIDE)
if GUIDE_INDEX_MARK not in home:
    raise SystemExit("docs/系列导读.md 里找不到索引占位：%s" % GUIDE_INDEX_MARK)
home = home.replace(GUIDE_INDEX_MARK, chapter_index_table())
home = home.rstrip("\n").split("\n")

home += [
    "",
    "---",
    "",
] + [
    "## 附录：这个仓库里能核验到什么",
    "",
    "导读给判断，仓库给证据。上面每一掌在仓库里都对应一条分支、一个 PR、一个 tag，",
    "外加一份贴着**原始输出**（真实模型返回、数据库查询、日志行）的验收记录。",
    "",
    "### 技术基线（不是抄文档，是门禁）",
    "",
    "| 项 | 取值 | 怎么钉住的 |",
    "|----|------|-----------|",
    "| JDK | 21 LTS | enforcer `requireJavaVersion [21,22)` |",
    "| Maven | 3.9.11 | 仓库自带 `./mvnw`，enforcer `requireMavenVersion [3.9,)` |",
    "| Spring Boot | 3.5.10 | 与 SAA 1.1.2.2 的 POM 对齐 |",
    "| Spring AI | 1.1.2 | `spring-ai-bom` 统一管理 |",
    "| Spring AI Alibaba | 1.1.2.2 | `spring-ai-alibaba-bom` + `extensions-bom`（`v2.0.0-M1.1` 只观察） |",
    "| 模型通道 | DeepSeek（OpenAI 兼容） | 只留一条**可验证**的通道 |",
    "| 依赖一致性 | enforcer `dependencyConvergence` | 已三次拦住真实版本分叉 |",
    "",
    "### 怎么跑起来",
    "",
    "```bash",
    "# 测试（离线：H2 内存库 + 假 ChatModel，不需要密钥、不需要数据库）",
    "./mvnw -B -ntp clean test",
    "",
    "# 运行（需要一个 MySQL 8；第 7 掌起还要单独起 MCP Server）",
    "docker run -d --name dh-mysql -e MYSQL_ROOT_PASSWORD=root \\",
    "  -e MYSQL_DATABASE=digital_human -p 33079:3306 mysql:8",
    "export DEEPSEEK_API_KEY=sk-xxxx",
    "./mvnw -pl digital-human spring-boot:run",
    "```",
    "",
    "### 仓库结构",
    "",
    "```text",
    "pom.xml                 父 POM：BOM 统一版本 + enforcer 基线门禁",
    "mvnw / mvnw.cmd         Maven Wrapper：把 Maven 版本钉在仓库里",
    "digital-human/          数字人应用（ChatClient 出口、工具、记忆、RAG、Agent、MCP Client）",
    "digital-human-mcp/      展厅预约 MCP Server：独立进程、独立库",
    "deploy/                 Docker Compose、远程部署脚本、钉钉通知",
    "scripts/                环境自检、Wiki 生成、Projects 同步",
    "docs/                   系列导读 + 每掌的设计文档与验收记录",
    "```",
    "",
    "### 实测与文章的偏差（写在验收记录里，不做粉饰）",
    "",
    "| 掌 | 文章里的写法 | 仓库实测到的 | 处置 |",
    "|----|--------------|--------------|------|",
    "| 7 | MCP Client 连不上时「工具静默为空」 | 实测直接 `McpTransportException`（404 on `/sse`） | 「清单为空」改成启动期 fail-fast |",
    "| 8 | `similarityThreshold` 控制检索 | 阈值 0 会让「无依据拒答」永不触发 | 向量库宽口径取候选，业务侧另设相关度下限 |",
    "| 9 | 挂框架的工具重试拦截器 | 工具失败已在工具边界转成可读结果，外层拦截器**永不触发** | 失败策略收归工具边界 |",
    "| 9 | SAA 与 Spring AI 是一套版本 | `graph-core` 依赖 MCP SDK 0.14.0，Spring AI 1.1.2 用 0.17.0，enforcer 拦下 | 统一到 0.17.0，并用真实远程工具调用证明没拆坏 |",
    "| 10 | 路由命中率 = 模型准不准 | 同口径连跑三轮：17 / 16 / 16，三条 MISS 其实是**标注错** | 口径写进配置，路由固定 `temperature: 0` |",
    "| 11 | 归约策略只是「配一下」 | 并行两分支写同一 key，`REPLACE` **静默丢数据** | 每个 key 显式声明策略，并做成对照测试 |",
    "| 12 | 拆多 Agent 是为了「能力更强」 | 三角色共用工具与记忆时，上下文预算每轮都要付 | 工具归属「任意两角色不共享」可断言；判据是人格装不下 |",
    "",
    "### 同一份内容，三个落点",
    "",
    "| 落点 | 读者 | 内容 |",
    "|------|------|------|",
    "| [`docs/chNN-*.md`](%s/tree/main/docs) | 跟着做的人 | 完整设计文档 + 验收记录（含原始输出与踩坑） |" % REPO_URL,
    "| Wiki（本页 + 每章一页） | 只想看结论的人 | 系列导读 + 每章交付、发现与遗留问题 |",
    "| [Projects](https://github.com/users/lifuchun522/projects/1) | 关心进度的人 | 每章一个条目，状态 Done / In progress / Backlog |",
    "",
    "Wiki 与 Projects 都由脚本生成，**不要手改**（下次生成会覆盖）：",
    "`python scripts/build-wiki.py`、`python scripts/sync-github-project.py`。",
    "",
    "---",
    "",
    "仓库地址：<%s> ｜ Wiki：<%s> ｜ 每掌的完整证据在 `docs/chNN-验收记录.md`" % (REPO_URL, WIKI_URL),
]

write(os.path.join(OUT, "Home.md"), home)
written.append("Home.md")

for n, gua, topic, branch, tag, pr, article, video in CHAPTERS:
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
    page.append("| 配套视频 | <https://cloud.tencent.com/developer/video/%s> |" % video)
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
    pending = dict((p[0], p[4]) for p in PENDING)
    if n + 1 in done:
        page.append("返回 [Home](Home) ｜ 下一掌：[第 %d 掌](%s)"
                    % (n + 1, page_name(n + 1, dict((c[0], c[2]) for c in CHAPTERS)[n + 1])[:-3]))
    elif n + 1 in pending:
        page.append("返回 [Home](Home) ｜ 下一掌：第 %d 掌（代码待落地，"
                    "[文章](%s)与视频已发布）" % (n + 1, ARTICLE_URL % pending[n + 1]))
    else:
        page.append("返回 [Home](Home) ｜ 下一掌：第 %d 掌（待做）" % (n + 1))
    page.append("")
    page.append("> 本掌与文章口径的差异、以及实测中发现的坑，都写在仓库的 `docs/ch%02d-验收记录.md` 里，不做粉饰。" % n)

    name = page_name(n, topic)
    write(os.path.join(OUT, name), page)
    written.append(name)

print("生成 %d 个 wiki 页面：" % len(written))
for name in written:
    print(" -", name)
