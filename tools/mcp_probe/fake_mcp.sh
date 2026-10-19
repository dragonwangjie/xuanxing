#!/system/bin/sh
# 假 MCP server(toybox sh 实现): 用于端到端验证玄星的 stdio 拉起 + shell 转义链路。
# 不依赖 node/rootfs; 只要 sh -c '<这个脚本路径>' 能被正确执行, 就能完成 JSON-RPC 握手。
# 每读到一行就检查是否是 initialize/tools 请求, 按 MCP 协议回包。
while IFS= read -r line; do
  case "$line" in
    *'"method":"initialize"'*)
      printf '{"jsonrpc":"2.0","id":%s,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"fake-sh-mcp","version":"1.0"}}}\n' \
        "$(echo "$line" | sed 's/.*"id":\([0-9]*\).*/\1/')"
      ;;
    *'"method":"tools/list"'*)
      printf '{"jsonrpc":"2.0","id":%s,"result":{"tools":[{"name":"ping_sh","description":"echo back via android sh","inputSchema":{"type":"object","properties":{"msg":{"type":"string"}}}}]}}\n' \
        "$(echo "$line" | sed 's/.*"id":\([0-9]*\).*/\1/')"
      ;;
    *'"method":"tools/call"'*)
      printf '{"jsonrpc":"2.0","id":%s,"result":{"content":[{"type":"text","text":"pong-from-android-sh"}],"isError":false}}\n' \
        "$(echo "$line" | sed 's/.*"id":\([0-9]*\).*/\1/')"
      ;;
    *'"method":"notifications/initialized"'*) ;;
  esac
done