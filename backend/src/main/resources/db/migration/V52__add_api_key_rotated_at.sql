-- Rotation replaces a key's secret while keeping the key itself: same row, same project, same role,
-- same service user — so created_by on everything it has written, and its MCP activity log, stay
-- attached. Only the hash and prefix change.
--
-- rotated_at exists so an admin can see at a glance which keys have never been rotated. A key with
-- a NULL here is on its original secret, whenever that was issued.
ALTER TABLE api_keys ADD COLUMN rotated_at TIMESTAMP NULL;
