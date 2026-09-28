<div align="center">

<img src="docs/images/banner.svg" alt="Learning Spring AI Alibaba in 18 moves" width="100%">

# Learning Spring AI Alibaba in 18 moves

**After Spring AI 2.0 GA: how a Java team should "tame" an agent framework.**

Companion code for an 18-part article series — every chapter is a loop you can run, verify and roll back.

This is not a "type along and move on" sample dump. Each chapter gets its own branch, pull request
and tag, plus an acceptance record quoting **raw output** — real model responses, database queries,
log lines.

[![CI](https://github.com/lifuchun522/springaialibabapractice/actions/workflows/ci.yml/badge.svg)](https://github.com/lifuchun522/springaialibabapractice/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-21%20LTS-orange.svg)](pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.10-6DB33F.svg)](pom.xml)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.2-6DB33F.svg)](pom.xml)
[![Spring AI Alibaba](https://img.shields.io/badge/Spring%20AI%20Alibaba-1.1.2.2-FF6A00.svg)](pom.xml)
[![Tests](https://img.shields.io/badge/tests-offline%20%26%20no%20keys-brightgreen.svg)](#7-one-command-startup-a-single-docker-compose)
[![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](.github/pull_request_template.md)

[简体中文](README.md) · [English](README.en.md)

[Who is this for](#1-is-this-for-you-30-seconds) · [Which chapters](#5-which-chapters-to-read-pick-your-route) · [All 18 chapters](#6-all-18-chapters-articles--videos) · [One-command startup](#7-one-command-startup-a-single-docker-compose) · [Articles](#12-the-article-series-three-more-judgements-worth-reading)

</div>

> One-line orientation: this is a Spring AI Alibaba practice series (pinned to the v1.1.2.2 production baseline) told through a single thread — a digital-human project. **18 articles + 18 videos**, all published on the Tencent Cloud developer community, running from selection and environment through to K8s rollout, with a verifiable completion criterion in every move. This repo is the **practice repository**: the series explains why each decision was made, the repo shows what actually happened when it ran.

---

## 1. Is this for you? (30 seconds)

**This is for you if:**

- you build AI or agent applications in **Java / Spring Boot**;
- you are choosing between Spring AI, Spring AI Alibaba, AgentScope and LangChain4j and want an
  **evidence-based** comparison;
- your project already runs, but **every new requirement moves the structure**;
- you need to **deliver, deploy and be evaluated**, not just demo.

**Not for you if:**

- you want a "first ChatBot in 10 minutes" tutorial — official docs and examples are faster;
- you are on **Python** — except the protocol parts of chapters 7 and 13, everything here is a
  Spring-side engineering decision;
- you want a ready-made framework — this content delivers **contracts and judgements**, not a product.

**One body of content, three landing spots — pick by your patience:**

| Where | For | What |
|-------|-----|------|
| [Wiki home](https://github.com/lifuchun522/springaialibabapractice/wiki) | people who want the summary | series guide + one page per chapter |
| **This README** | people who want one pass | the guide + the deviations measured here |
| [`docs/chNN-*.md`](docs) | people following along | full design doc + acceptance record with raw output |
| [Projects](https://github.com/users/lifuchun522/projects/1) | people tracking progress | one item per chapter: Done / In progress / Backlog |

| If you want to... | Go to |
| --- | --- |
| know whether this content fits you | [1. Is this for you](#1-is-this-for-you-30-seconds) |
| know why agent frameworks matter *now* | [2. Why now](#2-why-now-the-heat-is-in-the-framework-not-the-model) |
| stop reworking the structure for every feature | [3. The demo runs, the architecture does not](#3-why-most-teams-get-stuck-in-the-same-place-the-demo-runs-the-architecture-does-not) |
| know which chapters to read | [5. Pick your route](#5-which-chapters-to-read-pick-your-route) |
| run the whole thing first | [7. One-command startup](#7-one-command-startup-a-single-docker-compose) |
| see the deviations this repo measured | [8. Measured deviations](#8-measured-deviations-not-copied-from-docs) |


## 2. Why now: the heat is in the framework, not the model

Three things in the Java ecosystem became impossible to route around this year:

| When / what | What it actually changed for us |
| --- | --- |
| **Spring AI 2.0.0 GA** ([announcement](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now)) | The abstraction layer is final: Advisor, Tool Calling, MCP, RAG, Observability are all stable. "Connecting Java to an AI model" stopped being the problem |
| **Spring AI Alibaba 1.0 GA + Agent Framework / Graph Runtime** ([blog](https://java2ai.com/blog/spring-ai-alibaba-1.0-ga-release/), [GitHub](https://github.com/alibaba/spring-ai-alibaba), [docs](https://java2ai.com)) | The agent layer and the graph runtime arrived, killing the "Java can only do CRUD around a model" stereotype |
| **A crowded field: AgentScope Java 2.0, several ADKs** | Selection stopped being "is there anything to choose" and became "how expensive is choosing wrong" — **framework choice is now an architecture decision, not a dependency coordinate** |

So the thing blocking Java teams is no longer "how do I get the model to answer", but
"now that it answers, how do I grow an agent on top without redoing the structure".

Most published content stops at the first stop. This series starts at the second one — and this
repository is the set of real commits behind it.

## 3. Why most teams get stuck in the same place: the demo runs, the architecture does not

If your code looks like this, the series is written for you:

- The system prompt is a string literal in a controller, so ops needs a release to change a sentence;
- Session state lives in a `ConcurrentHashMap`, and two browser tabs cross wires;
- "Look up an order" means one more `if-else`; RAG means another branch; multi-agent means a `role` field;
- Switching model vendors means editing `import` statements instead of configuration;
- When something breaks you only see the final text — not which tool ran, what was retrieved, which branch was taken.

Chapter 1 names this failure and explains why it is inevitable: **the old design is correct for
single-turn Q&A. Once requirements cross the line of stateful / multi-step / interruptible /
observable, what fails is not a piece of code but the whole structure.**

Same judgement, expressed as the trade-off rule of the series:

> Selection is boundary, boundary is cost, cost is architecture, architecture is trade-off.

## 4. What "降" (tame) means here: not a downgrade, but bringing the framework under control

"降" means to subdue and hold, not to downgrade. The whole series does one thing repeatedly:
**compress new technology into an engineering-controllable range.** It shows up as three habits:

1. **Pin versions, do not chase the newest.** The production baseline is **v1.1.2.2**;
   `v2.0.0-M1.1` is watched only. Many `NoSuchMethodError`s are not bugs but late invoices for a
   selection decision — shipping a pre-release to production transfers version risk to the business.
   This repo turns that into a build gate, see [Baseline](#10-baseline-documented-is-not-enough-it-must-fail-validate).
2. **Draw boundaries before comparing features.** Put Spring AI, Spring AI Alibaba Extensions, Agent
   Framework, Graph Runtime and Admin/Studio in their proper layers, answer "which layer does my
   requirement belong to" first, and only then discuss modules.
3. **Add nothing you do not need.** Text completion only? The Spring AI base abstractions suffice.
   Not even multi-turn? A plain Java service plus one HTTP call is optimal. **A framework only pays
   off once the requirement crosses a threshold; below it, it is pure cost.**
## 5. Which chapters to read: pick your route

The 18 moves are **one dependency chain**, not 18 parallel articles. Reading out of order trips two
traps: the contracts used in move N were frozen in move N−1, and the troubleshooting section of move
N reproduces a failure left behind by move N−1.

**So pick the route that matches your role, then read the index below.**

| Your role | Route | What you take away |
| --- | --- | --- |
| **Architect / tech lead**<br>(choosing, not building yet) | **1 → 9 → 10 → 13 → 15** | **Decision criteria**: should we use an agent at all, how far, hard-wired orchestration versus autonomous reasoning, where the MCP/A2A line sits, how to build evaluation |
| **Backend / full-stack**<br>(already building AI features) | **1 → 2 → 3 → 4 → 5 → 6 → 7 → 8** | **A reusable base contract**: `projectId` as the single anchor, management path separated from the realtime path, reasoning growing in exactly one place |
| **SRE / platform / QA**<br>(shipping and delivering) | **14 → 15 → 16 → 17 → 18** | **A deliverable evidence chain**: which line of code decides a behaviour → six regression sets → one traceId locating model / tool / RAG / graph node / remote agent → deploys that do not drop traffic, self-heal and roll back |

Chapter 3 has a line worth taping to your desk: *the thinner the base, the faster everything after it grows.*

Only want to know how far the code has come? Jump to [chapter progress](#11-chapter-progress-what-already-runs).


## 6. All 18 chapters (articles + videos)

> Every move follows the same shape: **story → problem → principle → architecture → one real run →
> troubleshooting → tuning → insight → landing in the system**. Articles carry the reproducible
> detail; videos carry the reasoning. Articles and videos are **published on the Tencent Cloud
> developer community** and open without a login; videos are vertical, 3–5 minutes each. Article and
> video ids also live in [`scripts/build-wiki.py`](scripts/build-wiki.py), which generates the wiki pages.

| Ch | Topic | Read | Watch |
|----|-------|------|-------|
| 1 | 亢龙有悔 · Selection | [Article](https://cloud.tencent.com/developer/article/2752108) | [Video](https://cloud.tencent.com/developer/video/87798) |
| 2 | 飞龙在天 · Environment | [Article](https://cloud.tencent.com/developer/article/2752106) | [Video](https://cloud.tencent.com/developer/video/87796) |
| 3 | 见龙在田 · Base app | [Article](https://cloud.tencent.com/developer/article/2752105) | [Video](https://cloud.tencent.com/developer/video/87794) |
| 4 | 鸿渐于陆 · Model | [Article](https://cloud.tencent.com/developer/article/2752104) | [Video](https://cloud.tencent.com/developer/video/87793) |
| 5 | 潜龙勿用 · Memory & streaming | [Article](https://cloud.tencent.com/developer/article/2752103) | [Video](https://cloud.tencent.com/developer/video/87792) |
| 6 | 利涉大川 · Tools | [Article](https://cloud.tencent.com/developer/article/2752102) | [Video](https://cloud.tencent.com/developer/video/87791) |
| 7 | 突如其来 · MCP | [Article](https://cloud.tencent.com/developer/article/2752101) | [Video](https://cloud.tencent.com/developer/video/87790) |
| 8 | 震惊百里 · RAG | [Article](https://cloud.tencent.com/developer/article/2752097) | [Video](https://cloud.tencent.com/developer/video/87789) |
| 9 | 或跃在渊 · ReactAgent | [Article](https://cloud.tencent.com/developer/article/2752096) | [Video](https://cloud.tencent.com/developer/video/87788) |
| 10 | 双龙取水 · Workflows | [Article](https://cloud.tencent.com/developer/article/2752095) | [Video](https://cloud.tencent.com/developer/video/87787) |
| 11 | 鱼跃于渊 · Graph core | [Article](https://cloud.tencent.com/developer/article/2752094) | [Video](https://cloud.tencent.com/developer/video/87786) |
| 12 | 时乘六龙 · Multi-agent | [Article](https://cloud.tencent.com/developer/article/2752093) | [Video](https://cloud.tencent.com/developer/video/87785) |
| 13 | 密云不雨 · A2A | [Article](https://cloud.tencent.com/developer/article/2752092) | [Video](https://cloud.tencent.com/developer/video/87784) |
| 14 | 损则有孚 · Source PR | [Article](https://cloud.tencent.com/developer/article/2752091) | [Video](https://cloud.tencent.com/developer/video/87783) |
| 15 | 龙战于野 · Evaluation | [Article](https://cloud.tencent.com/developer/article/2752089) | [Video](https://cloud.tencent.com/developer/video/87782) |
| 16 | 履霜冰至 · Service | [Article](https://cloud.tencent.com/developer/article/2752087) | [Video](https://cloud.tencent.com/developer/video/87781) |
| 17 | 羝羊触藩 · Observability | [Article](https://cloud.tencent.com/developer/article/2752086) | [Video](https://cloud.tencent.com/developer/video/87797) |
| 18 | 神龙摆尾 · K8s | [Article](https://cloud.tencent.com/developer/article/2752084) | [Video](https://cloud.tencent.com/developer/video/87795) |

**How to watch**: the 18 videos map one-to-one onto the 18 articles. Articles give the reproducible
detail (versions, dependencies, troubleshooting, completion criteria); videos rehearse the **decision
process** — why this way, which alternatives were rejected, where the red lines are. The intended
rhythm is **video first for the trade-off, article second for the code**; reading the article first
tends to lose the decision inside the detail.
## 7. One-command startup: a single `docker compose`

**No JDK, no Maven, no manual database setup.** The images compile from source, MySQL comes up with
them, and Flyway migrations run inside the containers.

```bash
git clone https://github.com/lifuchun522/springaialibabapractice.git
cd springaialibabapractice/deploy

export DEEPSEEK_API_KEY=sk-xxxx
docker compose -f docker-compose.quickstart.yml up -d --build
```

The first run takes a few minutes (base images, dependency download, packaging); after that it is
seconds. All three containers should be `healthy`:

```console
$ docker compose -f docker-compose.quickstart.yml ps
NAME            IMAGE                                    STATUS
dh-quick-mysql  mysql:8.0                                Up (healthy)
dh-quick-mcp    saa-quickstart/digital-human-mcp:local   Up (healthy)
dh-quick-app    saa-quickstart/digital-human:local       Up (healthy)
```

Now **open it in a browser**: <http://localhost:8080/run/1>

![Digital-human run page: title, opening line and model all come from the database](docs/images/quickstart-run-page.png)

Everything on that screen is real, and everything on it is data: the title and opening line come from
`digital_human_project`, the model name from `agent_config`, and the `1` in the address bar is the
`projectId` — **the opening line changes without a release, just refresh the page** (the deliverable
of chapters 3 and 4).

| To verify | How |
| --- | --- |
| The tool really was discovered over MCP | `docker compose -f docker-compose.quickstart.yml logs digital-human \| grep 工具` → expects `MCP 远程工具已发现 1 个：showroom_query_availability` |
| The schema was migrated by the app itself | `docker compose -f docker-compose.quickstart.yml logs digital-human \| grep Migrating` → expects the V1 migrations |
| Run tests only, no services | `./mvnw -B -ntp test` (**offline**: H2 + fake `ChatModel`, no keys, no database) |

Knobs (all defaulted in `docker-compose.quickstart.yml`, so nothing must be set):
`QUICK_APP_PORT` (8080), `QUICK_MCP_PORT` (8081), `QUICK_DB_PORT` (3307), `QUICK_DB_PASSWORD`.

Tear down and wipe the database: `docker compose -f docker-compose.quickstart.yml down -v`.

> One-command startup uses `deploy/docker-compose.quickstart.yml` (local experience: built from source
> plus a bundled MySQL). `deploy/docker-compose.yml` is the one that deploys **pre-built images**
> (CI pushes images → server runs `up -d`). Do not mix the two.

### Prefer no Docker? Run the two processes locally

```bash
git clone https://github.com/lifuchun522/springaialibabapractice.git
cd springaialibabapractice
./mvnw -B -ntp test
```

Since chapter 15 the tests are split into five layers by JUnit tag, and only the two offline layers run by
default (142 tests: digital-human 133 + mcp 5 + knowledge-agent 4):

```bash
./mvnw -B -ntp clean verify                                                                    # L1 unit + L2 component (the CI blocking path)
./mvnw -B -ntp test -pl digital-human -Dsurefire.groups=integration -Dsurefire.excludedGroups=  # L3 real MySQL container
./mvnw -B -ntp test -pl digital-human -Dsurefire.groups=eval -Dsurefire.excludedGroups=         # L4 recorded-answer replay
./mvnw -B -ntp test -pl digital-human -Dsurefire.groups=eval-live -Dsurefire.excludedGroups=    # L5 live evaluation (needs a key)
```

> `groups` and `excludedGroups` must always be passed **together**: Surefire gives exclusion priority, so
> `-Dsurefire.groups=integration` alone runs nothing at all — and still reports success.

Running a real agent chain needs MySQL 8 and a DeepSeek key:

```bash
docker run -d --name dh-mysql -e MYSQL_ROOT_PASSWORD=root -p 33079:3306 mysql:8

export DEEPSEEK_API_KEY=sk-xxxx
export DIGITAL_HUMAN_DB_URL='jdbc:mysql://127.0.0.1:33079/digital_human?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export DIGITAL_HUMAN_DB_USER=root
export DIGITAL_HUMAN_DB_PASSWORD=root

./mvnw -pl digital-human spring-boot:run
```

```bash
# Smallest possible chain: no project, no knowledge base — just prove service + model channel are alive
curl -s "http://localhost:8080/api/chat?q=用一句话介绍你自己"
```

```json
{
  "reply": "深圳展厅每天开放时间是早上九点到晚上六点，不过周一闭馆，所以要避开周一去哦。",
  "traceId": "b68e4df4ff75",
  "modelCalls": 2,
  "events": ["agent.start", "model#1", "tool:knowledge_search", "model#2", "agent.end"]
}
```

(the STT → Agent → TTS chain did not change);
`events` / `modelCalls` / `traceId` are for whoever debugs it.
**The first payoff of an agent framework is observability, not prettier answers.**

## 8. Measured deviations (not copied from docs)

Docs give you APIs, articles give you direction, but "make the chain actually run and match the acceptance criteria" is a stretch nobody walks with you: versions drift, parameter semantics change, snippets do not hold on the version you installed. So the repo writes the deviations into `docs/chNN-验收记录.md` (Chinese) instead of hiding them in commit messages:

Docs give you APIs, articles give you direction, but "make the chain actually run and match the
acceptance criteria" is a stretch nobody walks with you: versions drift, parameter semantics change,
snippets do not hold on the version you installed. So the repo writes the deviations into
`docs/chNN-验收记录.md` (Chinese) instead of hiding them in commit messages:

| Ch | What the article says | What we measured here | What we did |
|----|-----------------------|-----------------------|-------------|
| 7 | A failed MCP client silently yields "no tools" | It throws `McpTransportException` (404 on `/sse`) — behaviour differs | Documented the gap; made an empty tool list a fail-fast at startup |
| 8 | `similarityThreshold` controls retrieval | Threshold 0 makes the "no basis, refuse" branch unreachable (score 0 still hits) | Wide candidate fetch from the store, separate relevance floor in business code |
| 9 | Attach the framework's tool-retry interceptor | Tool failures are already converted to readable results at the tool boundary, so the outer interceptor **never fires** | Failure policy owned by the tool boundary; no configured-but-dead channel left behind |
| 9 | Spring AI Alibaba and Spring AI share one version set | `agent-framework → graph-core` depends on MCP SDK **0.14.0** while Spring AI 1.1.2 uses **0.17.0**; the enforcer gate stops the build | Unified on 0.17.0 and proven with **real remote tool calls** (this also explains the chapter 7 protocol gap) |
| 10 | Routing accuracy is just "is the model good?" | Same 20 samples, same stated rules, three runs in a row: **17 / 16 / 16**. And the three "misses" were **our own mislabels** | Rules live in config, not in someone's head; routing calls pinned to `temperature: 0` → two reruns matched **line by line** |
| 11 | Reduction strategy is just "a setting" | When two parallel branches write the same key, `REPLACE` **silently loses data**: no exception, no log, output drops from 2 to 1 | Every used key declares its strategy; "what happens if you get it wrong" became a runnable contrast test |
| 12 | Splitting into multi-agent makes the system "more capable" | When three roles share tools and memory, the context budget is paid **every turn** (12 tool descriptions, related to the question or not); the router really did misroute once | Tool ownership frozen as "no two roles share a tool" (assertable); memory isolated per `role:sessionId`; the criterion is "one persona no longer fits", not "many tools" |
| 13 | The A2A/Nacos starters named in the article just work | `spring-ai-alibaba-starter-a2a-server`, `-a2a-client` and `-nacos-discovery` **do not exist** in 1.1.2.2 (Maven Central has nothing under those coordinates) | Implemented a thin layer ourselves from the protocol semantics: capability card + task lifecycle + streaming + version negotiation, with Nacos as just one discovery implementation |
| 14 | "The source I read says so" | The same code **behaves differently** on the tag and on main: in 1.1.2.2 `ToolRetryInterceptor` retries only thrown exceptions, while main also retries non-success responses | Truth pinned to the tag; the locator script keeps the version as an explicit constant, so a dependency bump makes the behaviour assertion fail and warn |
| 15 | Give an LLM judge a criterion and it scores quality | First evaluation run, a case whose **criterion was fully satisfied got 0 from the judge**: the criterion said "no discounts invented beyond the material", but the judge only received the criterion and the answer, never the material that already stated "7.5 off from 20 units" — **a judge that cannot see the facts calls facts fabrication** | Added a `reference` parameter to `LlmJudge` so retrieved material is handed over too; calibration samples must carry their own source material, otherwise you measure "are my samples complete", not "is the judge accurate" |
| 15 | Assert "must refuse" for the security suite | Twice, the same case: the model clearly refused both times, but the wording changed from "I **won't play** that role" to "I **won't take** that 'new role'" — **two false reds** | Phrasing is not a rule: the four security cases became "rules for hard constraints on forbidden content + judge for whether the refusal is explicit", and the stopgap of adding words to the marker list was reverted |
| 15 | No basis → refuse, without calling the model | That branch is **unreachable in the current configuration**: local hashing embeddings return an unrelated chunk for any Chinese question (score ≈ 0.0995 > `min-score=0.01`), so the "has basis" branch runs and the model itself says "no relevant content in the material" | **Not fixed**: it belongs to chapter 8's threshold and knowledge contract; it stays in the dataset as a **permanently failing** case (`pk-003`), so the L4 replay reproduces the defect every run |
| 16 | Add a health check and the service is deliverable | A real load test hit a run where the stream sent **not a single chunk**: HTTP 200, connection closed cleanly, the client received nothing — and the ledger recorded that assistant turn as `COMPLETED` (length 0). The same prompt through the blocking endpoint fails loudly with `EMPTY_RESPONSE` | The streaming path now fails explicitly via `switchIfEmpty` and the ledger records FAILED; an assertion pins that the conversation lock is released after a failure (`EmptyStreamContractTest`) — one semantic must behave the same on both contracts |
| 16 | Layering means moving files into packages | Writing the dependency direction as ArchUnit rules **caught a real violation on the very first run**: `ToolController` injected a repository directly (authorization, ownership and querying all inside the HTTP layer). The same run showed **my own rule was too wide** (`..web..` also matched Spring's `org.springframework.web..`, 23 false positives) | Added `ToolAuditService` so the use case lives in the service layer; narrowed the rule to `com.example.digitalhuman.web..` — a gate is code too, and both its width and its narrowness need calibrating against a real violation |
| 16 | If it runs locally, the service is fine | A real streaming request printed a framework warning: `default Spring MVC SimpleAsyncTaskExecutor … not suitable for production use under load` (a new thread per request, no bounds, no queue). **Every functional test passed**; the delivery criterion did not | Configured `WebAsyncConfig`: bounded pool (4/32/200) + explicit 5-minute timeout + graceful shutdown; the warning no longer appears on the same real path after the fix |
| 16 | Externalizing config is enough | compose had `${APP_DEEPSEEK_API_KEY}` — unset means empty string, so the gap travelled into the container and surfaced as a 401 on the first request (exactly the article's chain A) | Required items became `${VAR:?message}`: `docker compose config` now refuses to render and prints which variable is missing and where to declare it; the probe also moved from `/actuator/health` to `/actuator/health/readiness` |



## 9. What you get (and what you can verify here)

The series gives judgements; the repo gives evidence. Every chapter maps to a branch, a PR, a tag
and an acceptance record quoting raw output:

| Ch | Capability | What you can verify yourself |
|----|-----------|------------------------------|
| 1 | Selection and layering | Business code has exactly one `ChatClient` exit; no low-level model object leaks |
| 2 | Environment gate | JDK / Maven / dependency convergence failures break the `validate` phase |
| 3 | Digital-human base | Register/login, project CRUD, run page with SSE chat |
| 4 | Model governance | provider/model in the database, model catalog validation, deterministic error semantics (400 / 502 / 504) |
| 5 | Memory and ledger | Memory is a projection, `chat_message` is the fact; cancels and failures are recorded too |
| 6 | Tool gate | Read/write tools separated; writes need a single-use confirmation token; every call is audited, timed out and retried |
| 7 | MCP | Showroom booking split into its own process and database; the app discovers and calls it over MCP |
| 8 | RAG | Per-project knowledge base; isolation enforced by a **metadata contract**, not by retrieval luck; refuses when there is no basis |
| 9 | Agent brain | Full node event sequence, a hard cap on model calls, tool failures retried without killing the turn |
| 10 | Orchestration | Four flow-agent patterns — sequential / parallel / routing / looping — with node-level tracing |
| 11 | Graph runtime | Exportable, interruptible, resumable state graph; reduction strategies declared explicitly |
| 12 | Multi-agent | Three roles, each with its own prompt, tools and memory; router picks the first hop, handoffs are bounded |

### Three boundaries that hold across the whole repo

1. **The model never fills in identity.** Project/user ids travel out-of-band in `ToolContext`,
   never inside the tool's JSON Schema — the model cannot see them, so it cannot get them wrong.
2. **Failures stop at the tool boundary.** Bounded retries plus a fallback result hand the fact
   "this tool is unavailable right now" to the model instead of killing the conversation.
3. **Budgets live in engineering, not in the prompt.** Model calls per request have a hard cap and
   exceeding it ends the run **explicitly** (not by timeout), leaving event evidence behind.


## 10. Baseline: documented is not enough, it must fail `validate`

```mermaid
flowchart LR
    U["Browser"] -->|"HTTP + SSE"| API
    subgraph APP["digital-human: Spring Boot 3.5.10"]
        API["REST API<br/>auth / projects / chat / rag / agent"]
        AGENT["ReactAgent brain<br/>Hooks for boundaries + interceptors for tracing"]
        TOOLS["Tool layer<br/>read-only · write+token · audit/timeout/retry"]
        RAG["Project knowledge base<br/>metadata-contract isolation"]
    end
    AGENT -->|"ChatClient"| LLM["DeepSeek<br/>OpenAI-compatible"]
    AGENT --> TOOLS
    AGENT --> RAG
    TOOLS -->|"MCP STREAMABLE /mcp"| MCP["digital-human-mcp<br/>showroom booking: own process + own DB"]
    APP --> DB[("MySQL 8<br/>Flyway V1–V5")]
```

| Item | Value |
|------|-------|
| JDK | 21 LTS (enforcer pins `[21,22)`) |
| Maven | 3.9.11, supplied by the committed `./mvnw` (enforcer pins `[3.9,)`) |
| Spring Boot | 3.5.10 |
| Spring AI | 1.1.2 |
| Spring AI Alibaba | 1.1.2.2 (`v2.0.0-M1.1` is pre-release: watched, not shipped) |
| Model | DeepSeek, default `deepseek-flash` |
| Data | MySQL 8 + Flyway in runtime; H2 in tests |

The baseline is enforced, not documented: `mvnw` pins Maven, `requireJavaVersion` pins the JDK,
`dependencyConvergence` pins dependency versions — miss any of them and the build fails at `validate`
(it has already caught three real version divergences).

There is exactly **one model channel**: DeepSeek over the OpenAI-compatible protocol
(`spring.ai.model.chat=openai`, key from `DEEPSEEK_API_KEY`). The article series uses DashScope, but
this repo refuses to keep a configured-but-unused channel; it will be added in the chapter that
needs it. Without a key the service **refuses to start** (the SDK asserts a non-empty API key at
startup) — if the key is wrong, the service should not pretend to be healthy.

**Running both processes (from chapter 7 on)**:

```bash
# 1) MCP server (it owns its own database)
docker exec -i <mysql> mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS digital_human_ext"
MCP_DB_URL='jdbc:mysql://127.0.0.1:33079/digital_human_ext?...' ./mvnw -pl digital-human-mcp spring-boot:run

# 2) Digital-human service (defaults to http://localhost:8081/mcp)
./mvnw -pl digital-human spring-boot:run
```

Startup logs should show `MCP 远程工具已发现 1 个：showroom_query_availability`. If the list is empty and
`digital-human.mcp.fail-fast=true`, the service **fails to start** and explains the common causes.

## 11. Chapter progress: what already runs

| Ch | Hexagram · Topic | Branch | Tag | Status |
|----|------------------|--------|-----|--------|
| 1 | 亢龙有悔 · Selection | `chapter/01-value-selection` | `ch01` | ✅ Layering + single `ChatClient` exit |
| 2 | 飞龙在天 · Environment | `chapter/02-baseline-env` | `ch02` | ✅ mvnw + version gates + env self-check |
| 3 | 见龙在田 · Base app | `chapter/03-digital-human-demo` | `ch03` | ✅ Tables + auth + project CRUD + run page |
| 4 | 鸿渐于陆 · Model | `chapter/04-chat-model` | `ch04` | ✅ provider in DB + catalog + deterministic errors |
| 5 | 潜龙勿用 · Memory | `chapter/05-memory-streaming` | `ch05` | ✅ Memory + SSE streaming + message ledger |
| 6 | 利涉大川 · Tools | `chapter/06-tools` | `ch06` | ✅ Read/write split + human confirmation + audit & timeout |
| 7 | 突如其来 · MCP | `chapter/07-mcp` | `ch07` | ✅ Standalone MCP server + remote discovery and calls |
| 8 | 震惊百里 · RAG | `chapter/08-rag` | `ch08` | ✅ Per-project KB + metadata contract + cited answers, refusal without basis |
| 9 | 或跃在渊 · ReactAgent | `chapter/09-react-agent` | `ch09` | ✅ Event stream + hard model-call cap + tool-boundary retry + ledger |
| 10 | 双龙取水 · Workflows | `chapter/10-workflow-agents` | `ch10` | ✅ Four flow-agent patterns + node-level tracing (timings, emissions, sequence) |
| 11 | 鱼跃于渊 · Graph core | `chapter/11-graph-core` | `ch11` | ✅ State graph + explicit reduction strategies + interrupt + MySQL checkpoints (survives restart) |
| 12 | 时乘六龙 · Multi-agent | `chapter/12-multi-agent` | `ch12` | ✅ Three roles, each with its own prompt/tools/memory + router + handoff + max-hops |
| 13 | 密云不雨 · A2A | `chapter/13-a2a-nacos` | `ch13` | ✅ Standalone knowledge agent + capability card, task lifecycle, version negotiation, swappable discovery ([article](https://cloud.tencent.com/developer/article/2752092)) |
| 14 | 损则有孚 · Source PR | `chapter/14-source-pr` | `ch14` | ✅ Behaviour pinned to 1.1.2.2 line numbers + minimal reproduction + upstream issue draft ([article](https://cloud.tencent.com/developer/article/2752091)) |
| 15 | 龙战于野 · Evaluation | `chapter/15-eval-guard` | `ch15` | ✅ Five test layers (L1 unit / L2 MockWebServer at the HTTP boundary / L3 Testcontainers / L4 snapshot replay / L5 live evaluation) + six regression suites, 24 cases + judge calibration ([article](https://cloud.tencent.com/developer/article/2752089)) |
| 16 | 履霜冰至 · Service | `chapter/16-spring-service` | `ch16` | ✅ Dependency-direction gate (6 ArchUnit rules) + startup deployment contract + health groups (liveness / readiness) + SSE heartbeat with a bounded async executor + production boundary (`/internal/llm/v1` is 404 under prod) + executable API contract ([article](https://cloud.tencent.com/developer/article/2752087)) |
| 17 | 羝羊触藩 · Observability | `chapter/17-observability-admin` | — | ⬜ Planned ([article](https://cloud.tencent.com/developer/article/2752086) and video already published) |
| 18 | 神龙摆尾 · K8s | `chapter/18-k8s-production` | — | ⬜ Planned ([article](https://cloud.tencent.com/developer/article/2752084) and video already published) |

### Delivery flow: issue → branch → PR → main → tag

`main` is always the latest working state; no chapter writes to `main` directly:

```text
Issue (capability and acceptance criteria for this chapter)
   └─ branch chapter/NN-topic      ← this chapter only
        └─ PR (linked to the issue, with real verification evidence)
             └─ merge into main → tag chNN
```

Commits are `type(chNN): description`; the tag sits on the merge commit in `main` and its message
names the branch, the PR and what was delivered — so "what did chapter N actually ship" is answerable
from the tag itself.
## 12. The article series: three more judgements worth reading

「降 SpringAI 阿里」十八掌 (Learning Spring AI Alibaba in 18 moves) on the Tencent Cloud developer
community, by 李福春. Every article and video link is in
[All 18 chapters](#6-all-18-chapters-articles--videos); the front door is
[chapter 1 · selection](https://cloud.tencent.com/developer/article/2752108)
([video](https://cloud.tencent.com/developer/video/87798)) — it decides whether the following 17
chapters make you rework anything.

If you only have time for three passages, take these — each is counter-intuitive **and** backed by evidence:

1. **On the realtime path, "one hop less" is often a pessimisation** (chapter 3): once the realtime
   process connects to the model itself, a second context and a second tool registry grow there —
   and **every later chapter has to be changed twice**.
2. **The model never executes code, it only writes arguments** (chapter 6): therefore **a prompt is
   not a security boundary**. "Please do not modify data" is a probabilistic constraint: it lowers
   the chance of an incident without changing the capability. Narrowing capability takes structure,
   not tone.
3. **The boundary of your tests is the boundary of your mocks** (chapter 15): mocking `ChatModel`
   entirely hard-codes "the model always returns the text we expect" — while model output is exactly
   the object under test, not a background condition.

**This is for you if**: you build AI / agent applications in Java or Spring Boot; you are choosing
between Spring AI, Spring AI Alibaba, AgentScope and LangChain4j and want an evidence-based
comparison; your project runs but every new requirement moves the structure; you need to deliver,
deploy and be evaluated rather than demo.

**Not for you if**: you want a "first ChatBot in 10 minutes" tutorial (official docs are faster);
you are on Python (except the protocol parts of chapters 7 and 13, everything here is a Spring-side
engineering decision); you want a ready-made framework — this content delivers **contracts and judgements**.

> Base first, then layers; layers first, then multiplicity; rails first, then visibility; visibility first, then shipping.

## Layout

```text
pom.xml                 Parent POM: BOM-managed versions + enforcer baseline gates
mvnw / mvnw.cmd         Maven Wrapper: the Maven version is pinned in the repo
digital-human/          The app: ChatClient exit, tools, memory, RAG, ReactAgent, MCP client
digital-human-mcp/      Showroom-booking MCP server: own process, own database
deploy/                 Docker Compose, remote deploy script, DingTalk notification
.github/workflows/      CI (build + test) and release (build → images → deploy → notify)
scripts/                Env self-check, wiki generator, Projects sync
docs/                   Series guide + per-chapter design docs and acceptance records
```

## Secrets

- Real keys live in environment variables only, or in a local `.env.local` / `application-local.yml`
  (both git-ignored).
- The repo only ever contains placeholders such as `.env.example` with `sk-xxxx`.
- Before committing: `git diff --cached | findstr sk-` — if a real key shows up, do not commit.

## Conventions

- Framework versions come from BOMs; submodules never declare them.
- Business code depends on `ChatClient` only, never on low-level model objects.
- One commit per chapter; messages are `type(chNN): description`.
- `.ps1` scripts are saved as UTF-8 with BOM (required by PowerShell 5.1).
- The single source of the series guide is `docs/系列导读.md`; the wiki (`Home` + one page per
  chapter) and Projects are generated by `scripts/build-wiki.py` and `scripts/sync-github-project.py`
  — do not edit them by hand.

## License

[Apache License 2.0](LICENSE).

<div align="center">

If this repo saved you some debugging, a **star** ⭐ helps. Found a gap? Open an issue with your raw output —
articles give direction, repos give evidence.

</div>
