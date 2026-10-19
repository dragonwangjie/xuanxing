#!/usr/bin/env python3
"""量化 MCP 工具「常驻注入」与「懒加载」的上下文成本, 以及懒加载的检索召回。

语料来自真实服务器, 不造假数据:
    ./probe_rea.sh /tmp/rea_tools.json
    python3 token_cost.py /tmp/rea_tools.json

口径与生产代码逐一对齐(改这些函数时要同步看 Kotlin 侧):
  eager   ChatService 为每个工具注入
          {"type":"function","function":{name,description,parameters}}
  lazy    1 个 search_tools 元工具(McpSearchToolFactory 的 schema +
          McpToolRouter.searchToolDescription 的文本) + 检索命中后展开的工具
  检索     McpToolRouter.score: 每个词必须在 name 或 description 子串命中(AND)

注意: 这里报的是**字节**与 bytes/4 的换算下界。项目没有离线 tokenizer,
真实 token 数取决于各家 tokenizer; JSON schema 的英文压缩率通常差于 4, 所以
绝对值只用于同口径相对比较, 不要当报价。
"""
import json
import os
import re
import sys

SERVER = os.environ.get("MCP_SERVER_NAME", "rea")
EXPAND = int(os.environ.get("MAX_EXPAND", "6"))
PINNED = int(os.environ.get("PINNED", "0"))

# 模型常见的高频词与短语查询。AND 语义下多词查询是懒加载的主要失败模式。
VOCAB = ["decompile", "disasm", "disassemble", "rename", "xref", "manifest",
         "frida", "smali", "strings", "apk", "dex", "native", "hook", "trace",
         "unpack", "packer", "jni", "sqlite", "ssl", "dump", "memory", "permission"]
PAIRS = ["decompile apk method", "manifest permissions", "disasm arm",
         "rename method", "frida hook", "ssl unpin", "jni native", "find strings"]


def function_bytes(name, description, schema):
    obj = {"type": "function", "function": {
        "name": name, "description": description or "", "parameters": schema or {}}}
    return len(json.dumps(obj, ensure_ascii=False, separators=(",", ":")).encode())


def score(tool, terms):
    """McpToolRouter.score 的等价实现。"""
    name, desc = tool["name"].lower(), (tool.get("description") or "").lower()
    total = 0
    for t in terms:
        if name == t:
            total += 12
        elif name.startswith(t):
            total += 8
        elif t in name:
            total += 5
        elif t in desc:
            total += 2
        else:
            return 0
    return total


def search(tools, query, limit):
    terms = [t for t in re.split(r"[\s,|/]+", query.lower()) if len(t) >= 2]
    if not terms:
        return []
    hits = [(score(t, terms), t["name"]) for t in tools if score(t, terms) > 0]
    return [n for _, n in sorted(hits, key=lambda x: (-x[0], x[1]))[:limit]]


def meta_bytes(available, sample_names):
    desc = (
        "Search the MCP server '" + SERVER + "' for tools it provides but that are not "
        "currently exposed as functions (" + str(available) + " available). "
        "Call this BEFORE assuming the server cannot do something; then call the returned "
        "tool directly by its full name in a later turn. "
        "Examples of searchable tools: " + ", ".join(sample_names) + "."
    )
    return function_bytes("mcp__" + SERVER + "__search_tools", desc, {
        "type": "object",
        "properties": {"query": {"type": "string", "description":
            "Keywords describing the capability you need"}},
        "required": ["query"]})


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/rea_tools.json"
    tools = json.load(open(path))
    sizes = {t["name"]: function_bytes("mcp__%s__%s" % (SERVER, t["name"]),
                                        t.get("description"), t.get("inputSchema"))
             for t in tools}
    order = {t["name"]: i for i, t in enumerate(tools)}
    eager = sum(sizes[t["name"]] for t in tools)
    available = len(tools) - PINNED
    meta = meta_bytes(available, [t["name"] for t in tools[:6]])

    tok = lambda b: "%s" % format(b // 4, ",")
    saved = lambda b: 0 if not eager else round((eager - b) * 100 / eager)

    desc_bytes = sum(len(t.get("description") or "") for t in tools)
    print("corpus: %d tools from %s" % (len(tools), path))
    print("eager payload %s B (~%s tok); of that descriptions = %s B (%.0f%%), "
          "schemas+names = %.0f%%"
          % (format(eager, ","), tok(eager), format(desc_bytes, ","),
             desc_bytes * 100 / eager, (eager - desc_bytes) * 100 / eager))
    print("largest single tool = %s B (~%s tok), median = %s B"
          % (format(max(sizes.values()), ","), tok(max(sizes.values())),
             format(sorted(sizes.values())[len(sizes) // 2], ",")))
    print()
    # 尺寸闸门后的可注入上限(对齐 McpToolRouter.MAX_INJECT_TOOL_BYTES)
    gate = int(os.environ.get("MAX_INJECT_TOOL_BYTES", "32000"))
    gated = sum(v for v in sizes.values() if v <= gate)

    rows = [("eager: all %d tools" % len(tools), eager),
            ("eager after %dKB gate (%d tools)" % (gate // 1000,
     sum(1 for v in sizes.values() if v <= gate)), gated),
            ("lazy: meta only", meta)]
    for q in ("decompile", "manifest", "apk"):
        names = search(tools, q, EXPAND)
        rows.append(("lazy: meta +%d expanded ('%s')" % (len(names), q),
                     meta + sum(sizes[n] for n in names)))
    heaviest = sorted(sizes.values(), reverse=True)[:EXPAND]
    rows.append(("lazy: meta +%d heaviest" % EXPAND, meta + sum(heaviest)))
    print("%-40s %12s %10s %8s" % ("config", "payload B", "~tok", "saved"))
    for label, b in rows:
        print("%-40s %12s %10s %7d%%" % (label, format(b, ","), tok(b), saved(b)))

    print("\n检索召回(懒加载的代价; eager 下工具本来就在手边, 没有这一项):")
    zero = [w for w in VOCAB if not search(tools, w, 1)]
    print("  单词: %d/%d 命中, 零命中词 = %s"
          % (len(VOCAB) - len(zero), len(VOCAB), ", ".join(zero) or "-"))
    dead = 0
    for p in PAIRS:
        n = search(tools, p, EXPAND)
        dead += not n
        print("  %-22s -> %d hits" % (p, len(n)))
    print("  多词查询零结果: %d/%d —— 产品侧已改为零结果时直接回全量名字清单兜底"
          % (dead, len(PAIRS)))
    names_bytes = len("\n".join(sorted(t["name"] for t in tools)).encode())
    print("  名字清单成本: %d B (~%d tok) = eager payload 的 %.1f%%"
          % (names_bytes, names_bytes // 4, names_bytes * 100 / eager))


if __name__ == "__main__":
    main()