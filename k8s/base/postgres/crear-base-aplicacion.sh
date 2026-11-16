#!/bin/sh
# Crea el rol sin privilegios de la aplicacion y su base de datos.
# La imagen de PostgreSQL solo ejecuta este script cuando el volumen de datos esta vacio.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
\getenv usuario APP_DB_USERNAME
\getenv clave APP_DB_PASSWORD
\getenv base APP_DB_NAME
CREATE ROLE :"usuario" LOGIN PASSWORD :'clave';
CREATE DATABASE :"base" OWNER :"usuario";
REVOKE ALL ON DATABASE :"base" FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
SQL
