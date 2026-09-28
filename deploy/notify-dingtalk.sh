#!/usr/bin/env bash
# 钉钉机器人通知。支持加签（DINGTALK_SECRET 非空时自动签名）。
#
# 需要的环境变量：
#   DINGTALK_WEBHOOK  机器人 webhook（必填，例如 https://oapi.dingtalk.com/robot/send?access_token=xxx）
#   DINGTALK_SECRET   加签密钥（可选；机器人安全设置选「加签」时必填）
#   TITLE / TEXT      通知标题与正文（markdown）
set -euo pipefail

if [[ -z "${DINGTALK_WEBHOOK:-}" ]]; then
  echo "未配置 DINGTALK_WEBHOOK，跳过钉钉通知"
  exit 0
fi

URL="${DINGTALK_WEBHOOK}"

# 加签：timestamp + "\n" + secret → HMAC-SHA256 → base64 → urlencode
if [[ -n "${DINGTALK_SECRET:-}" ]]; then
  TIMESTAMP="$(date +%s%3N)"
  STRING_TO_SIGN="${TIMESTAMP}"$'\n'"${DINGTALK_SECRET}"
  SIGN="$(printf '%s' "${STRING_TO_SIGN}" | openssl dgst -sha256 -hmac "${DINGTALK_SECRET}" -binary | base64)"
  SIGN_ENCODED="$(printf '%s' "${SIGN}" | jq -sRr @uri)"
  if [[ "${URL}" == *"?"* ]]; then
    URL="${URL}&timestamp=${TIMESTAMP}&sign=${SIGN_ENCODED}"
  else
    URL="${URL}?timestamp=${TIMESTAMP}&sign=${SIGN_ENCODED}"
  fi
fi

PAYLOAD_FILE="$(mktemp)"
trap 'rm -f "${PAYLOAD_FILE}"' EXIT

jq -n \
  --arg title "${TITLE:-通知}" \
  --arg text "### ${TITLE:-通知}
${TEXT:-}" \
  '{msgtype: "markdown", markdown: {title: $title, text: $text}}' > "${PAYLOAD_FILE}"

RESPONSE="$(curl -sS -X POST "${URL}" -H 'Content-Type: application/json' -d @"${PAYLOAD_FILE}")"
echo "钉钉响应：${RESPONSE}"

# 钉钉成功返回 {"errcode":0,...}；失败时让这一步失败，免得通知挂了没人知道
if ! printf '%s' "${RESPONSE}" | jq -e '.errcode == 0' >/dev/null 2>&1; then
  echo "钉钉通知发送失败" >&2
  exit 1
fi
