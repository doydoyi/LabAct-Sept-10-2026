#!/usr/bin/env bash
# Part B probing script. Run this yourself against the real LegacySupply
# server - this is the traffic your self-check page and REFLECTION.md
# questions are generated from, so it has to come from you, not from code
# I write for you.
#
# Usage:
#   export LS_API_KEY="LSK-..."
#   export LS_CLIENT_ID="21-1128-503"
#   chmod +x probe.sh
#   ./probe.sh
#
# Everything prints to the terminal - copy/paste whatever you need into
# INTEGRATION.md and REFLECTION.md as you go.

set -uo pipefail
BASE="https://legacysupply.onrender.com/api/v1"

if [[ -z "${LS_API_KEY:-}" || -z "${LS_CLIENT_ID:-}" ]]; then
  echo "Set LS_API_KEY and LS_CLIENT_ID first (export ...), then re-run." >&2
  exit 1
fi

hr() { printf '\n=== %s ===\n' "$1"; }

hr "0. Ping (no auth needed)"
curl -s -w '\nHTTP %{http_code}\n' "$BASE/ping"

hr "1. Authenticate"
AUTH_XML=$(curl -s -X POST "$BASE/auth/token" \
  -H "Content-Type: application/xml" \
  -d "<AuthRequest><ClientId>${LS_CLIENT_ID}</ClientId><ApiKey>${LS_API_KEY}</ApiKey></AuthRequest>")
echo "$AUTH_XML"
SESSION=$(echo "$AUTH_XML" | grep -oP '(?<=<SessionToken>).*?(?=</SessionToken>)')
if [[ -z "$SESSION" ]]; then
  echo "Could not extract a session token - check the response above." >&2
  exit 1
fi
echo "Session token: $SESSION"

hr "2. Catalog - THIS is what you paste into your app.supplier.catalog.* properties"
curl -s -w '\nHTTP %{http_code}\n' "$BASE/catalog" -H "X-LS-Session: $SESSION"

hr "3. Place one real test order (uses the FIRST SupplierSku from the catalog output above)"
read -rp "Paste one SupplierSku from the catalog output above: " SKU
ORDER_XML="<PurchaseOrder><SupplierSku>${SKU}</SupplierSku><Qty>1</Qty><BuyerRef>RO-PROBE-1</BuyerRef></PurchaseOrder>"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/purchase-orders" \
  -H "Content-Type: application/xml" -H "X-LS-Session: $SESSION" \
  -H "X-Request-Id: probe-order-1" \
  -d "$ORDER_XML"

hr "4. Repeat the SAME order with the SAME X-Request-Id - should NOT create a duplicate"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/purchase-orders" \
  -H "Content-Type: application/xml" -H "X-LS-Session: $SESSION" \
  -H "X-Request-Id: probe-order-1" \
  -d "$ORDER_XML"

hr "5. Deliberate errors - for your INTEGRATION.md error table"
echo "-- No session header:"
curl -s -w '\nHTTP %{http_code}\n' "$BASE/catalog"

echo "-- Bad session token:"
curl -s -w '\nHTTP %{http_code}\n' "$BASE/catalog" -H "X-LS-Session: not-a-real-token"

echo "-- Unrecognized SupplierSku:"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/purchase-orders" \
  -H "Content-Type: application/xml" -H "X-LS-Session: $SESSION" \
  -d "<PurchaseOrder><SupplierSku>DOES-NOT-EXIST</SupplierSku><Qty>1</Qty><BuyerRef>RO-PROBE-2</BuyerRef></PurchaseOrder>"

echo "-- Invalid quantity (0):"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/purchase-orders" \
  -H "Content-Type: application/xml" -H "X-LS-Session: $SESSION" \
  -d "<PurchaseOrder><SupplierSku>${SKU}</SupplierSku><Qty>0</Qty><BuyerRef>RO-PROBE-3</BuyerRef></PurchaseOrder>"

echo "-- Missing BuyerRef:"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/purchase-orders" \
  -H "Content-Type: application/xml" -H "X-LS-Session: $SESSION" \
  -d "<PurchaseOrder><SupplierSku>${SKU}</SupplierSku><Qty>1</Qty></PurchaseOrder>"

echo "-- Wrong content type:"
curl -s -w '\nHTTP %{http_code}\n' -X POST "$BASE/purchase-orders" \
  -H "Content-Type: application/json" -H "X-LS-Session: $SESSION" \
  -d '{"not":"xml"}'

hr "6. Measuring session lifetime (this loop can take a while - Ctrl+C once it fails)"
echo "Calling GET /catalog with the SAME session every 30s until it's rejected."
START=$(date +%s)
for i in $(seq 1 30); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/catalog" -H "X-LS-Session: $SESSION")
  ELAPSED=$(( $(date +%s) - START ))
  echo "t+${ELAPSED}s: HTTP $CODE"
  if [[ "$CODE" != "200" ]]; then
    echo "Session rejected after approximately ${ELAPSED} seconds - record this in INTEGRATION.md item 2."
    break
  fi
  sleep 30
done
