#!/bin/bash
# 向真实 `rea mcp` 做 initialize + tools/list, 把工具清单落盘给 token_cost.py 用。
# 用法: ./probe_rea.sh [out.json]
set -euo pipefail
OUT=${1:-/tmp/rea_tools.json}
RAW=$(mktemp)
ERR=$(mktemp)
trap 'rm -f "$RAW" "$ERR"' EXIT

{
  printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cost-probe","version":"1"}}}'
  printf '%s\n' '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
  [ "${REA_PROBE_NO_HOLD:-0}" = "1" ] || sleep 12
} | timeout 90 rea mcp 2>"$ERR" > "$RAW"

python3 - "$RAW" "$OUT" <<'PY'
import json, sys
raw, out = sys.argv[1], sys.argv[2]
tools = None
for line in open(raw):
    line = line.strip()
    if not line:
        continue
    try:
        msg = json.loads(line)
    except ValueError:
        continue
    if msg.get("id") == 2 and "result" in msg:
        tools = msg["result"].get("tools", [])
if tools is None:
    sys.exit("no tools/list result on stdout (see server stderr)")
json.dump(tools, open(out, "w"))
print("tools=%d -> %s" % (len(tools), out))
PY