#!/usr/bin/env bash
# 第 13 保：创建任务池（good first issue）
#
# 任务池的意义：开源运营漏斗的最后一段是「访问 → …… → Contributor」。
# 没有任务池，贡献者来了也不知道能做什么，漏斗就断在最后一步；
# 有了任务池但任务写不清（没有验收标准、没说改哪些文件），
# 贡献者会做一半卡住，维护者反而更累——所以每个任务必须带「要做什么 / 验收标准 / 改动范围」。
#
# 幂等：按标题查重，已存在则跳过。
#
# 用法：bash scripts/gh-13-seed-good-first-issues.sh

set -uo pipefail

REPO="${REPO:-lifuchun522/springaialibabapractice}"
LABEL="${LABEL:-good first issue}"

create() {
  local title="$1" body="$2"
  local n
  n="$(gh issue list -R "$REPO" --state all --search "in:title \"$title\"" --json title \
        --jq ".[] | select(.title==\"$title\") | .title" 2>/dev/null | head -1)"
  if [ -n "$n" ]; then
    echo "[gh13] 已存在，跳过：${title}"
    return 0
  fi
  local tmp
  tmp="$(mktemp)"
  printf '%s\n' "$body" >"$tmp"
  if url="$(gh issue create -R "$REPO" --title "$title" --body-file "$tmp" --label "$LABEL" 2>&1)"; then
    echo "[gh13] 已创建：${title}"
    echo "        ${url}"
  else
    echo "[gh13] 创建失败：${title}" >&2
    echo "        ${url}" >&2
    return 1
  fi
  rm -f "$tmp"
}

body_md_check() {
  cat <<'MD'
### 要做什么

把 `scripts/check-md.py` 从「打印体检信息」升级成**能当门禁用**的脚本。

当前它只 `print` 行数、围栏次数、标题与表格行数，无论发现什么问题都返回 0，
所以接不进 CI。目标：

1. 发现结构问题时**以非零退出码结束**，并逐条点名「文件:行号 → 问题」；
2. 支持一次检查多个文件或整个目录（例如 `python3 scripts/check-md.py docs/`），
   当前只接受单个路径参数；
3. 保持**只依赖 Python 标准库**，不联网；
4. 保留现有的「信息打印」能力（可加 `--info` 开关），因为排查时那些信息有用。

### 验收标准

- 构造一个围栏不闭合的 Markdown（`` ``` `` 出现奇数次）→ 脚本非零退出并打印该文件与行号；
- 构造一个 ``` `` ``` 出现在正文里的 Markdown → 脚本非零退出并点名；
- 正常文件与正常目录 → 退出码 0；
- 一次性传整个 `docs/` 目录能跑完并汇总「检查了 N 个文件，M 个有问题」；
- 未安装任何第三方依赖（`grep -E '^(import|from) ' scripts/check-md.py` 只出现标准库模块）。

### 改动范围

- `scripts/check-md.py`（主要改动，预计 60–100 行）
- 可选：如果决定接进 CI，同时改 `.github/workflows/ci.yml`（加一个步骤）

**不需要**动 `docs/` 下任何文档内容——这是脚本改进，不是文档修正。

### 提示

现有门禁的风格可以参考 `scripts/check-diagrams.py` 与 `scripts/check-github-ops-links.py`：
都是「只依赖标准库 + 非零退出 + 逐条点名」。
MD
}

body_readme_en() {
  cat <<'MD'
### 要做什么

`README.md` 已经加了「首屏五段」（项目定位 / 快速开始 / 掌次路线 / 技术栈 / 参与贡献），
但 `README.en.md`（英文版）还是旧结构，中英两份首屏已经漂移。

目标：把英文版的首屏对齐成同样的五段结构，并**不删除**英文版原有的内容——
只补结构、调顺序、把中文版新加的内链换成等价的英文表述。

### 验收标准

- `README.en.md` 首屏能依次找到五段：Project positioning / Quick start / Chapter roadmap / Tech stack / Contributing；
- 英文版的「Contributing」段落链接到 `CONTRIBUTING.md`；
- 英文版不出现「待补充 / TODO / coming soon」这类占位；
- 中英两份的**章节数**与**tag 名**一致（`ch01`–`ch18`、`v*` 语义说明都在）；
- `markdownlint` 或人工检查：两份的标题层级都对得上（`#` → `##`，不跳级）。

### 改动范围

- `README.en.md`（主要改动）
- 如果英文版缺 `docs/github-ops` 的入口，可在文末补一行指向中文治理索引

**不需要**翻译 `docs/` 下的正文（本仓库的判定口径是：正文中文，技术名词首现写「英文原名（中文）」）。

### 提示

对照检查时可以用：`Select-String -Path README.md -Pattern '^## '` 与英文版做同样的列出，逐条对齐。
MD
}

body_gfi_doc() {
  cat <<'MD'
### 要做什么

`docs/github-ops/13-open-source-operations.md` 里写了七级漏斗的取数方式，
但**没有一个「一次跑完」的命令**——新人要逐级手敲七条命令才知道漏斗各段现状。

目标：新增一个 `scripts/gh-13-funnel-report.sh`，把七级漏斗的现状一次性打印成一张 Markdown 表，
可以直接贴进 Discussions 或周报。

### 验收标准

- 一条命令输出七个级别的当前值（访问 / README 入口 / Clone / Discussion / Issue / PR / Contributor）；
- 每级的取数方式与 `docs/github-ops/13-open-source-operations.md` 里写的**完全一致**（同一端点、同一命令）；
- 某级取不到数据时打印「缺（原因）」而不是空白或 0；
- 只依赖 `gh` 与 `python3`（仓库既有标准），不引入新依赖；
- 脚本**只读**：不含任何写 GitHub 状态的操作。

### 改动范围

- 新增 `scripts/gh-13-funnel-report.sh`
- `docs/github-ops/13-open-source-operations.md` 补一节「一键取数」（加 5–10 行）

### 提示

第 12 保的 `scripts/gh-insights-report.sh` 已经有「取数 → 固定口径表格 → 缺失标注」的完整写法，
直接复用它的 `metric()` 思路即可，不必从零写。
MD
}

create "[文档] 英文版 README 首屏与中文版对齐（五段结构）" "$(body_readme_en)"
create "[脚本] check-md.py 增加非零退出与多文件支持，接进 CI 门禁" "$(body_md_check)"
create "[脚本] 给运营漏斗加一键取数脚本（七级现状一张表）" "$(body_gfi_doc)"

echo
echo "[gh13] 当前开放的任务池："
gh issue list -R "$REPO" --label "$LABEL" --state open --json number,title,url \
  --jq '.[] | "  #\(.number) \(.title)\n        \(.url)"'
