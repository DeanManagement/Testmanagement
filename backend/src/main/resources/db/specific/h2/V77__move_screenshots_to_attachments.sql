-- The screenshots table is kept for one release as a backup of the copied rows; nothing reads it.
-- ponytail: drop it in a later migration once production shows the evidence intact.
-- TM05: the H2 twin of db/specific/postgresql/V77, for the tests.
INSERT INTO attachments (id, step_result_id, file_name, content_type, size_bytes, sha256, data,
                         created_at, updated_at, created_by, updated_by)
SELECT id, step_result_id, file_name, content_type, OCTET_LENGTH(data), LOWER(RAWTOHEX(HASH('SHA-256', data))), data,
       created_at, updated_at, created_by, updated_by
FROM screenshots;
