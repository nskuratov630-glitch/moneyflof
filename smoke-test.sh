#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="moneyflow-smoke-$(date +%s)@example.test"
PASSWORD="Smoke-test-Password-123!"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }

request() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-sS -X "$method" -H 'Content-Type: application/json')
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$body" ]] && args+=(-d "$body")
  curl "${args[@]}" -w $'\nHTTP_STATUS:%{http_code}' "$BASE_URL$path"
}

response="$(request POST /api/auth/register '' "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")"
status="${response##*HTTP_STATUS:}"
body="${response%$'\n'HTTP_STATUS:*}"
if [[ "$status" != "200" && "$status" != "201" ]]; then
  echo "Registration failed (HTTP $status): $body" >&2; exit 1
fi
token="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])' <<<"$body")"

wallets="$(request GET /api/wallets "$token")"
wallet_body="${wallets%$'\n'HTTP_STATUS:*}"
wallet_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])' <<<"$wallet_body")"

response="$(request POST /api/transactions "$token" "{\"walletId\":$wallet_id,\"type\":\"INCOME\",\"amountMinor\":25000,\"category\":\"Smoke test\",\"description\":\"Test deposit\"}")"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "200" || "$status" == "201" ]] || { echo "Income transaction failed: $response" >&2; exit 1; }

response="$(request POST /api/calculator/price "$token" '{"unitPriceMinor":10000,"quantity":2,"discountPercent":10,"fixedDiscountMinor":0,"taxPercent":20,"feeMinor":100}')"
body="${response%$'\n'HTTP_STATUS:*}"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "200" ]] || { echo "Calculator failed: $response" >&2; exit 1; }
python3 - "$body" <<'PY'
import json, sys
x = json.loads(sys.argv[1])
assert x['subtotalMinor'] == 20000, x
assert x['discountMinor'] == 2000, x
assert x['taxMinor'] == 3600, x
assert x['totalMinor'] == 21700, x
print('Calculator result validated.')
PY

# A manual transaction deletion must reverse its effect on the wallet.
response="$(request POST /api/transactions "$token" "{\"walletId\":$wallet_id,\"type\":\"EXPENSE\",\"amountMinor\":500,\"category\":\"Delete test\",\"description\":\"Reversal check\"}")"
status="${response##*HTTP_STATUS:}"
body="${response%$'\n'HTTP_STATUS:*}"
[[ "$status" == "200" || "$status" == "201" ]] || { echo "Test expense failed: $response" >&2; exit 1; }
tx_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$body")"
response="$(request DELETE "/api/transactions/$tx_id" "$token")"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "200" ]] || { echo "Transaction reversal failed: $response" >&2; exit 1; }

# Create and fund a savings goal (this debits the wallet atomically).
response="$(request POST /api/goals "$token" '{"name":"Emergency fund","currency":"EUR","targetMinor":10000}')"
status="${response##*HTTP_STATUS:}"
body="${response%$'\n'HTTP_STATUS:*}"
[[ "$status" == "200" || "$status" == "201" ]] || { echo "Goal creation failed: $response" >&2; exit 1; }
goal_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$body")"
response="$(request POST "/api/goals/$goal_id/contributions" "$token" "{\"walletId\":$wallet_id,\"amountMinor\":1000,\"note\":\"smoke test\"}")"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "200" || "$status" == "201" ]] || { echo "Goal contribution failed: $response" >&2; exit 1; }

# A past-due recurring income can be executed exactly once for its scheduled date.
response="$(request POST /api/recurring "$token" '{"walletId":'"$wallet_id"',"type":"INCOME","amountMinor":100,"category":"Recurring smoke","description":"Automated test","frequency":"MONTHLY","nextRunAt":"2000-01-31"}')"
status="${response##*HTTP_STATUS:}"
body="${response%$'\n'HTTP_STATUS:*}"
[[ "$status" == "200" || "$status" == "201" ]] || { echo "Recurring rule creation failed: $response" >&2; exit 1; }
rule_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])' <<<"$body")"
response="$(request POST "/api/recurring/$rule_id/run" "$token")"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "200" ]] || { echo "Recurring execution failed: $response" >&2; exit 1; }
response="$(request PATCH "/api/recurring/$rule_id" "$token" '{"active":false}')"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "200" ]] || { echo "Recurring pause failed: $response" >&2; exit 1; }
response="$(request POST "/api/recurring/$rule_id/run" "$token")"
status="${response##*HTTP_STATUS:}"
[[ "$status" == "409" ]] || { echo "Paused recurring rule should not execute (HTTP $status): $response" >&2; exit 1; }

for path in /api/dashboard /api/transactions /api/budgets /api/categories /api/goals /api/recurring /api/transfers /api/reports/monthly /api/export/transactions.csv; do
  status="$(curl -sS -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $token" "$BASE_URL$path")"
  [[ "$status" == "200" ]] || { echo "$path returned HTTP $status" >&2; exit 1; }
done

echo "MoneyFlow smoke tests passed against $BASE_URL"
