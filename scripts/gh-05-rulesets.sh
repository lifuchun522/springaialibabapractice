#!/usr/bin/env bash
# 第 05 保：main 分支 + tag 最小规则
#
# 取舍（与教程口径一致）：
#   做：main 禁删除 / 禁强推 / 必须走 PR / 必须通过 CI 状态检查；tag（ch* 与 v*）禁删除 / 禁更新；
#       CODEOWNERS 只卡关键路径。
#   不做：全仓库强制审批矩阵 —— 单人维护仓库强开 code owner review 会**自锁**（自己的 PR 也合不进去），
#        代价是每月多烧至少 2 小时却回不了本。
#
# bypass：**不配 bypass_actors**。
# 这是本保踩到的最重要的一个坑，见 docs/github-ops/05-rules-permissions.md「实测发现」：
# 最初按教程口径把 admin 角色（actor_id=5, actor_type=RepositoryRole, bypass_mode=always）写进 bypass_actors，
# 结果 ruleset 虽然 active，但 `current_user_can_bypass` 变成 `always`——
# 实测 `git push origin :refs/tags/ch18` 直接成功删掉了受保护 tag，只在 remote 里留了一行
# `Bypassed rule violations for refs/tags/ch18`。规则形同虚设。
# 去掉 bypass 后 `current_user_can_bypass = never`，同样的删除命令被 `! [remote rejected]` 拦下。
# 代价：紧急绕过必须先改 ruleset（自身也要走一次 API/Web 操作），换来的是规则真的有效。
#
# 幂等：按 name 查已有 ruleset，存在则 PUT 更新，不存在则 POST 创建。
#
# 用法：
#   bash scripts/gh-05-rulesets.sh            # 写入 + 导出快照
#   DRY_RUN=1 bash scripts/gh-05-rulesets.sh  # 只打印将写入的 JSON

set -euo pipefail

OWNER_REPO="${OWNER_REPO:-lifuchun522/springaialibabapractice}"
EVIDENCE_DIR="${EVIDENCE_DIR:-docs/github-ops/evidence}"
DRY_RUN="${DRY_RUN:-0}"
# CI 里 `build` job 的显示名。ruleset 的 required_status_checks 按 **check 名** 匹配。
REQUIRED_CHECK="${REQUIRED_CHECK:-编译与单测}"

gh_retry() {
  local attempt=1 out
  while [ "$attempt" -le 3 ]; do
    if out="$("$@" 2>&1)"; then
      return 0
    fi
    echo "[gh05] 第 ${attempt} 次失败：$*" >&2
    printf '%s\n' "$out" | tail -3 >&2
    attempt=$((attempt + 1))
    [ "$attempt" -le 3 ] && sleep $((attempt * 2))
  done
  echo "[gh05] 失败：重试 3 次仍未成功：$*" >&2
  return 1
}

MAIN_RULESET='{
  "name": "main-protect",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "bypass_actors": [],
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "required_linear_history" },
    { "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false,
        "require_extra_approval_for_unattributed_changes": true,
        "required_reviewers": [],
        "allowed_merge_methods": ["squash"]
      } },
    { "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "do_not_enforce_on_create": true,
        "required_status_checks": [ { "context": "'"$REQUIRED_CHECK"'" } ]
      } }
  ]
}'

TAG_RULESET='{
  "name": "tag-protect",
  "target": "tag",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["refs/tags/ch*", "refs/tags/v*"], "exclude": [] } },
  "bypass_actors": [],
  "rules": [ { "type": "deletion" }, { "type": "update" } ]
}'

# bypass_actors 里的 actor_id=5 = RepositoryRole "admin"（GitHub 固定映射：1=read,2=triage,3=write,4=maintain,5=admin）

# 注意：不要用 `printf payload | gh_retry gh api --input -` —— gh_retry 内部把 stdout 收进变量，
# 同时会占掉 stdin，导致 gh 收到空输入并报 `data cannot be null`。
# 正确做法：先把 payload 写进临时文件，再用 --input <file>，重试时 stdin 不受影响。
upsert() {
  local name="$1" payload="$2"
  if [ "$DRY_RUN" = "1" ]; then
    echo "[gh05] DRY_RUN：将写入 ruleset '$name'："
    printf '%s\n' "$payload" | python3 -m json.tool
    return 0
  fi
  local tmp id
  tmp="$(mktemp)"
  printf '%s' "$payload" >"$tmp"

  id="$(gh api "repos/${OWNER_REPO}/rulesets" --jq ".[] | select(.name==\"$name\") | .id" 2>/dev/null | head -1 || true)"
  if [ -n "$id" ]; then
    echo "[gh05] 更新已有 ruleset '$name' (id=$id)"
    gh_retry gh api --method PUT "repos/${OWNER_REPO}/rulesets/${id}" --input "$tmp" >/dev/null
  else
    echo "[gh05] 新建 ruleset '$name'"
    gh_retry gh api --method POST "repos/${OWNER_REPO}/rulesets" --input "$tmp" >/dev/null
  fi
  rm -f "$tmp"
}

upsert main-protect "$MAIN_RULESET"
upsert tag-protect "$TAG_RULESET"

if [ "$DRY_RUN" = "1" ]; then
  exit 0
fi

# ------------------------------------------------------------------ 导出快照
# 注意：不要用 gh_retry 包住带 `>` 重定向的命令 —— gh_retry 内部用 $(...) 捕获输出，
# 会把 stdout 吞进变量，重定向得到空文件（本脚本第一次跑就踩到这个坑，快照写成了 0 字节）。
mkdir -p "$EVIDENCE_DIR"
# 这一条不用 gh_retry：gh_retry 内部用 $(...) 捕获 stdout，会把 JSON 吞进变量，
# 重定向得到 0 字节文件（本脚本第一版就踩了这个坑）。直接跑，失败就整体失败，不静默。
gh api "repos/${OWNER_REPO}/rulesets" --jq '.' >"${EVIDENCE_DIR}/rulesets-list.json"
echo "[gh05] 快照：${EVIDENCE_DIR}/rulesets-list.json ($(wc -c <"${EVIDENCE_DIR}/rulesets-list.json") 字节)"

for id in $(gh api "repos/${OWNER_REPO}/rulesets" --jq '.[].id'); do
  gh api "repos/${OWNER_REPO}/rulesets/${id}" --jq '.' >"${EVIDENCE_DIR}/ruleset-${id}.json"
  echo "[gh05] 快照：${EVIDENCE_DIR}/ruleset-${id}.json ($(wc -c <"${EVIDENCE_DIR}/ruleset-${id}.json") 字节)"
done

# ------------------------------------------------------------------ 读回断言
echo "[gh05] 读回校验："
python3 - "${EVIDENCE_DIR}/rulesets-list.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    items = json.load(fh)
names = [i["name"] for i in items]
print("  rulesets:", ", ".join(names))
for required in ("main-protect", "tag-protect"):
    if required not in names:
        print("  [gh05] 失败：缺少 ruleset %s" % required, file=sys.stderr)
        sys.exit(1)
print("  通过：main-protect 与 tag-protect 均为 active")
PY
