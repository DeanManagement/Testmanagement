-- PRD-027 §9: which groups of MCP tools a key may see and call, as comma-separated McpToolGroup
-- names. NULL means every group, which is what every key issued before this column held — so
-- nothing changes for them. A restricted key both loses the tools from tools/list (the point:
-- their descriptions no longer ride along on every agent turn) and is refused if it calls one.
ALTER TABLE api_keys ADD COLUMN mcp_tool_groups VARCHAR(255) NULL;
