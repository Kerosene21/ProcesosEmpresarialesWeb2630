#!/usr/bin/env bash
# Crea, solo si no existen, los Secrets del despliegue con valores aleatorios.
# Los valores no se escriben en disco ni se muestran; un Secret existente se conserva, porque
# PostgreSQL ya guardo esa clave al inicializar su volumen.
#   k8s/scripts/crear-secretos.sh [namespace]
# KUBECTL permite usar otro cliente, por ejemplo KUBECTL="docker exec -i k3d-procesos-server-0 kubectl".
set -euo pipefail

NAMESPACE="${1:-procesos-empresariales}"
read -r -a KUBECTL <<< "${KUBECTL:-kubectl}"

if ! "${KUBECTL[@]}" get namespace "$NAMESPACE" >/dev/null 2>&1; then
  echo "No existe el namespace $NAMESPACE; aplica antes k8s/base/namespace.yaml" >&2
  exit 1
fi

# tr quita tambien el retorno de carro que anade openssl en Windows (Git Bash).
aleatorio() { openssl rand "$@" | tr -d '\r\n'; }

crear() {
  local nombre=$1
  shift
  if "${KUBECTL[@]}" -n "$NAMESPACE" get secret "$nombre" >/dev/null 2>&1; then
    echo "Secret $nombre: ya existe, se conserva"
    return
  fi
  {
    printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: %s\n  namespace: %s\n' "$nombre" "$NAMESPACE"
    printf '  labels:\n    app.kubernetes.io/part-of: procesos-empresariales\ntype: Opaque\nstringData:\n'
    local par
    for par in "$@"; do
      printf '  %s: "%s"\n' "${par%%=*}" "${par#*=}"
    done
  } | "${KUBECTL[@]}" create -f - >/dev/null
  echo "Secret $nombre: creado"
}

# create (no apply) para que el valor no quede copiado en la anotacion last-applied-configuration.
crear procesos-postgres-admin "POSTGRES_PASSWORD=$(aleatorio -hex 24)"
crear procesos-backend "DB_PASSWORD=$(aleatorio -hex 24)" "JWT_SECRET=$(aleatorio -base64 48)"
