import json, os, sys


def send(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


for raw in sys.stdin:
    raw = raw.strip()
    if not raw:
        continue
    msg = json.loads(raw)
    if "id" not in msg:
        continue
    mid, method = msg["id"], msg.get("method")
    params = msg.get("params") or {}
    if method == "initialize":
        send({"jsonrpc": "2.0", "id": mid, "result": {
            "protocolVersion": params.get("protocolVersion", "2024-11-05"),
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "fake-stdio", "version": "1.0"}}})
    elif method == "tools/list":
        send({"jsonrpc": "2.0", "id": mid, "result": {"tools": [{
            "name": "env_probe", "description": "read an env var",
            "inputSchema": {"type": "object", "properties": {}, "required": []}}]}})
    elif method == "tools/call":
        val = os.environ.get("MCP_TEST_SECRET", "<unset>")
        send({"jsonrpc": "2.0", "id": mid, "result": {
            "content": [{"type": "text", "text": "secret=" + val}], "isError": False}})
    else:
        send({"jsonrpc": "2.0", "id": mid,
              "error": {"code": -32601, "message": "unhandled: " + str(method)}})
