-- Migración manual HU-21 a HU-24 para bases PostgreSQL existentes (creadas con el esquema de HU-01 a HU-20).
-- Ejecutar una sola vez, con respaldo previo, antes de arrancar la aplicación con este código.
-- Las bases nuevas y el perfil de pruebas (ddl-auto=create-drop) no la necesitan.
-- Las columnas nuevas con valor por defecto, las tablas nuevas y las llaves foráneas las agrega
-- Hibernate (ddl-auto=update) al arrancar después de esta migración.

BEGIN;

-- 1. La relación proceso -> pool se invierte: ahora cada pool apunta a su proceso.
ALTER TABLE pool ADD COLUMN IF NOT EXISTS proceso_id bigint;

UPDATE pool p
SET proceso_id = pr.id
FROM proceso pr
WHERE pr.pool_id = p.id
  AND p.proceso_id IS NULL;

-- 2. Cada gateway pertenece a un pool: se asigna el pool propietario del proceso,
--    que antes de HU-21 era el único pool de cada proceso.
ALTER TABLE gateway ADD COLUMN IF NOT EXISTS pool_id bigint;

UPDATE gateway g
SET pool_id = pr.pool_id
FROM proceso pr
WHERE pr.id = g.proceso_id
  AND g.pool_id IS NULL;

ALTER TABLE pool ALTER COLUMN proceso_id SET NOT NULL;
ALTER TABLE gateway ALTER COLUMN pool_id SET NOT NULL;

-- 3. Se elimina la columna anterior proceso.pool_id junto con su llave foránea.
ALTER TABLE proceso DROP COLUMN IF EXISTS pool_id;

-- 4. Las lanes nuevas toman su nombre del rol de proceso, por lo que el nombre deja de ser obligatorio,
--    pero cada lane debe tener nombre o rol de proceso.
ALTER TABLE lane ALTER COLUMN nombre DROP NOT NULL;

ALTER TABLE lane DROP CONSTRAINT IF EXISTS ck_lane_nombre_o_rol_proceso;
ALTER TABLE lane ADD CONSTRAINT ck_lane_nombre_o_rol_proceso
    CHECK (nombre IS NOT NULL OR rol_proceso_id IS NOT NULL);

-- 5. Orden inicial de las lanes dentro de cada pool según su antigüedad.
ALTER TABLE lane ADD COLUMN IF NOT EXISTS orden integer DEFAULT 1 NOT NULL;

UPDATE lane l
SET orden = numeradas.posicion
FROM (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY pool_id ORDER BY id) AS posicion
    FROM lane
) numeradas
WHERE numeradas.id = l.id;

COMMIT;
