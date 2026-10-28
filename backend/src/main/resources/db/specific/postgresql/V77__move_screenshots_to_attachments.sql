-- The screenshots table is kept for one release as a backup of the copied rows; nothing reads it.
-- ponytail: drop it in a later migration once production shows the evidence intact.
-- TM05: a step result's single screenshot becomes the first file of its evidence list. Same id, so
-- nothing that recorded one loses track of it. db/specific/h2/V77 computes the same columns on H2.
INSERT INTO attachments (id, step_result_id, file_name, content_type, size_bytes, sha256, data,
                         created_at, updated_at, created_by, updated_by)
SELECT id, step_result_id, file_name, content_type, octet_length(data), encode(sha256(data), 'hex'), data,
       created_at, updated_at, created_by, updated_by
FROM screenshots;
