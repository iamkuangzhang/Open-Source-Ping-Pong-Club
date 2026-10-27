-- user_organization_roles.id 原先无默认值，角色授予 SQL 不再显式传入 id，
-- 由数据库在插入新记录时自动生成主键。
ALTER TABLE user_organization_roles ALTER COLUMN id SET DEFAULT gen_random_uuid();
