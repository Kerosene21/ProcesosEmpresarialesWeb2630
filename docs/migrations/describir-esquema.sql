-- Consulta de solo lectura que describe el esquema public de forma independiente del orden de columnas.
-- Sirve para comparar una base existente con la migracion V1 antes de marcarla con baseline:
--   psql -tA -d <base> -f docs/migrations/describir-esquema.sql > esquema-base.txt
--   diff docs/migrations/esquema-v1.txt esquema-base.txt
-- Si no hay diferencias, la base puede marcarse con baseline en la version 1.
select 'COL ' || c.table_name || '.' || c.column_name || ' ' || c.data_type
       || coalesce('(' || c.character_maximum_length || ')', '')
       || case when c.is_nullable = 'NO' then ' NOT NULL' else '' end
       || coalesce(' DEFAULT ' || c.column_default, '')
       || case when c.is_identity = 'YES' then ' IDENTITY ' || c.identity_generation else '' end
from information_schema.columns c
where c.table_schema = 'public' and c.table_name <> 'flyway_schema_history'
union all
select 'CON ' || t.relname || ' ' || k.conname || ' ' || pg_get_constraintdef(k.oid)
from pg_constraint k join pg_class t on t.oid = k.conrelid join pg_namespace n on n.oid = t.relnamespace
where n.nspname = 'public' and t.relname <> 'flyway_schema_history'
union all
select 'IDX ' || i.indexdef
from pg_indexes i
where i.schemaname = 'public' and i.tablename <> 'flyway_schema_history'
order by 1;
