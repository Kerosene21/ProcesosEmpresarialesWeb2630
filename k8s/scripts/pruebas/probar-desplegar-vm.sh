#!/usr/bin/env bash
set -uo pipefail

DIR_PRUEBAS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$DIR_PRUEBAS/../desplegar-vm.sh"
TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT
export KUBECTL="bash $DIR_PRUEBAS/kubectl-simulado.sh"
export CURL="bash $DIR_PRUEBAS/curl-simulado.sh"
export ESPERA_HTTP=0
TOTAL=0
CORRECTOS=0
FALLIDOS=()
FILAS=()

digest() { printf 'sha256:%064x' "$1"; }
readonly REPO_BACKEND="ghcr.io/kerosene21/procesos-empresariales-backend"
readonly REPO_FRONTEND="ghcr.io/kerosene21/procesos-empresariales-frontend"
BACKEND_ACTUAL="$REPO_BACKEND@$(digest 1)"
FRONTEND_ACTUAL="$REPO_FRONTEND@$(digest 2)"
BACKEND_NUEVO="$REPO_BACKEND@$(digest 3)"
FRONTEND_NUEVO="$REPO_FRONTEND@$(digest 4)"
COMMIT=$(printf '%040x' 5)

nueva_simulacion() {
  SIMULACION="$TEMPORAL/sim-$TOTAL"
  export SIMULACION
  rm -rf "$SIMULACION"
  mkdir -p "$SIMULACION/namespace" "$SIMULACION/deploy/procesos-backend" "$SIMULACION/deploy/procesos-frontend" "$SIMULACION/sts" "$SIMULACION/fallos" "$SIMULACION/http"
  printf 'restricted' >"$SIMULACION/namespace/procesos-empresariales"
  printf 'init esperar-postgres %s\napp backend %s\n' "$BACKEND_ACTUAL" "$BACKEND_ACTUAL" >"$SIMULACION/deploy/procesos-backend/contenedores"
  printf 'app frontend %s\n' "$FRONTEND_ACTUAL" >"$SIMULACION/deploy/procesos-frontend/contenedores"
  printf '2 2 2 2 4 4\n' >"$SIMULACION/deploy/procesos-backend/estado"
  printf '2 2 2 2 7 7\n' >"$SIMULACION/deploy/procesos-frontend/estado"
  printf '1 1' >"$SIMULACION/sts/procesos-postgres"
  : >"$SIMULACION/llamadas.log"
  REGISTRO_PRUEBA="$TEMPORAL/registro-$TOTAL"
}

metadatos_base() {
  cat <<TEXTO
formato=1
repositorio=Kerosene21/ProcesosEmpresarialesWeb2630
commit=$COMMIT
commit_fecha=2026-11-17T12:00:00Z
namespace=procesos-empresariales
backend_imagen=$BACKEND_NUEVO
backend_revision=$COMMIT
frontend_imagen=$FRONTEND_NUEVO
frontend_revision=$COMMIT
backend_anterior=$BACKEND_ACTUAL
migraciones_nuevas=0
verificacion_entorno=environment:vm-universitaria
TEXTO
}

deployments_yaml() {
  local backend=$1 frontend=$2
  cat <<TEXTO
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app.kubernetes.io/name: backend
  name: procesos-backend
  namespace: procesos-empresariales
spec:
  template:
    spec:
      containers:
      - image: $backend
        name: backend
      initContainers:
      - image: $backend
        name: esperar-postgres
---
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app.kubernetes.io/name: frontend
  name: procesos-frontend
  namespace: procesos-empresariales
spec:
  template:
    spec:
      containers:
      - image: $frontend
        name: frontend
TEXTO
}

construir_paquete() {
  local destino=$1 manipular=${2:-} dir="$TEMPORAL/construccion-$TOTAL"
  rm -rf "$dir"
  mkdir -p "$dir/paquete-despliegue"
  metadatos_base | sed -e "${SED_METADATOS:-}" >"$dir/paquete-despliegue/metadatos.txt"
  deployments_yaml "${YAML_BACKEND:-$BACKEND_NUEVO}" "${YAML_FRONTEND:-$FRONTEND_NUEVO}" >"$dir/paquete-despliegue/deployments.yaml"
  printf '%s  V1__esquema_inicial.sql  existente\n' "$(printf '%064x' 9)" >"$dir/paquete-despliegue/migraciones.txt"
  printf '# Paquete de prueba\n' >"$dir/paquete-despliegue/RESUMEN.md"
  (cd "$dir/paquete-despliegue" && sha256sum RESUMEN.md deployments.yaml metadatos.txt migraciones.txt >SHA256SUMS)
  if [[ -n $manipular ]]; then
    "$manipular" "$dir"
  fi
  tar --sort=name -czf "$destino" -C "$dir" paquete-despliegue
  sha256sum "$destino" | cut -d' ' -f1
}

ejecutar() {
  SALIDA="$TEMPORAL/salida-$TOTAL.txt"
  bash "$SCRIPT" --registro "$REGISTRO_PRUEBA" "$@" >"$SALIDA" 2>&1
  CODIGO=$?
}

mutaciones() {
  grep -E 'set image' "$SIMULACION/llamadas.log" | grep -vc -- '--dry-run=server' || true
}

imagen_simulada() {
  awk -v c="$2" '$2 == c {print $3}' "$SIMULACION/deploy/$1/contenedores"
}

comprobar() {
  local nombre=$1 resultado=$2
  TOTAL=$((TOTAL + 1))
  if [[ $resultado == SI ]]; then
    CORRECTOS=$((CORRECTOS + 1))
  else
    FALLIDOS+=("$nombre")
    printf -- '--- salida de %s ---\n' "$nombre"
    cat "$SALIDA" 2>/dev/null
  fi
  FILAS+=("| $TOTAL | $nombre | ${CODIGO:-} | $resultado |")
  printf '%-3s %-3s codigo=%-2s %s\n' "$TOTAL" "$resultado" "${CODIGO:-}" "$nombre"
}

esperar() {
  local codigo=$1 patron=$2
  if [[ $CODIGO == "$codigo" ]] && grep -qF -- "$patron" "$SALIDA"; then
    printf 'SI'
  else
    printf 'NO'
  fi
}

prueba_comprobacion_correcta() {
  nueva_simulacion
  local suma
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --url https://127.0.0.1:8443
  local r
  r=$(esperar 0 "Comprobacion correcta. No se modifico ningun recurso")
  [[ $(mutaciones) == 0 && $(imagen_simulada procesos-backend backend) == "$BACKEND_ACTUAL" ]] || r=NO
  grep -q 'set image deployment/procesos-backend esperar-postgres=.* backend=.* --dry-run=server' "$SIMULACION/llamadas.log" || r=NO
  [[ ! -d $REGISTRO_PRUEBA ]] || r=NO
  comprobar "Modo comprobacion por defecto: sin cambios, con dry-run del servidor" "$r"
}

prueba_aplicacion_correcta() {
  nueva_simulacion
  local suma r registro
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --url https://127.0.0.1:8443 --aplicar
  r=$(esperar 0 "Resultado: CORRECTO")
  [[ $(imagen_simulada procesos-backend backend) == "$BACKEND_NUEVO" && $(imagen_simulada procesos-backend esperar-postgres) == "$BACKEND_NUEVO" ]] || r=NO
  [[ $(imagen_simulada procesos-frontend frontend) == "$FRONTEND_NUEVO" ]] || r=NO
  registro=$(find "$REGISTRO_PRUEBA" -mindepth 1 -maxdepth 1 -type d -name "*-${COMMIT:0:12}" | head -n 1)
  grep -q "procesos-backend app backend $BACKEND_ACTUAL" "$registro/imagenes-anteriores.txt" 2>/dev/null || r=NO
  [[ -f $registro/informe.txt && -f $registro/metadatos.txt && ! -d $REGISTRO_PRUEBA/.bloqueo ]] || r=NO
  grep -q 'rollout status deployment/procesos-backend --timeout=420s' "$SIMULACION/llamadas.log" || r=NO
  ! grep -qE 'statefulset.*set|delete|apply|patch' "$SIMULACION/llamadas.log" || r=NO
  ULTIMO_REGISTRO=$registro
  comprobar "Aplicacion correcta: solo imagenes, rollout, salud, registro e informe" "$r"
}

prueba_revertir_tras_aplicar() {
  local registro=$ULTIMO_REGISTRO r
  REGISTRO_PRUEBA=$(dirname "$registro")
  ejecutar --revertir "$registro" --sin-http
  r=$(esperar 0 "revertir las imagenes no revierte las migraciones de Flyway")
  grep -qF "solicitada: $BACKEND_ACTUAL" "$SALIDA" || r=NO
  [[ $(imagen_simulada procesos-backend backend) == "$BACKEND_NUEVO" ]] || r=NO
  comprobar "Preparacion del rollback en modo comprobacion" "$r"
  ejecutar --revertir "$registro" --sin-http --aplicar
  r=$(esperar 0 "Resultado: CORRECTO")
  [[ $(imagen_simulada procesos-backend backend) == "$BACKEND_ACTUAL" && $(imagen_simulada procesos-frontend frontend) == "$FRONTEND_ACTUAL" ]] || r=NO
  comprobar "Rollback aplicado a los digests anteriores" "$r"
}

prueba_rollout_fallido_y_rollback() {
  nueva_simulacion
  local suma r registro
  touch "$SIMULACION/fallos/rollout-procesos-backend"
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --sin-http --aplicar
  r=$(esperar 3 "rollout de procesos-backend no completado")
  grep -qF -- "--revertir $REGISTRO_PRUEBA/" "$SALIDA" || r=NO
  registro=$(find "$REGISTRO_PRUEBA" -mindepth 1 -maxdepth 1 -type d -name "*-${COMMIT:0:12}" | head -n 1)
  grep -q 'Resultado: FALLIDO' "$registro/informe.txt" 2>/dev/null || r=NO
  comprobar "Rollout simulado fallido: codigo 3 e instrucciones de rollback" "$r"
  rm -f "$SIMULACION/fallos/rollout-procesos-backend"
  ejecutar --revertir "$registro" --sin-http --aplicar
  r=$(esperar 0 "Resultado: CORRECTO")
  grep -qF "procesos-backend no esta estable" "$SALIDA" || r=NO
  [[ $(imagen_simulada procesos-backend backend) == "$BACKEND_ACTUAL" ]] || r=NO
  comprobar "Rollback despues de un rollout fallido" "$r"
}

prueba_fallo_salud() {
  nueva_simulacion
  local suma r
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  mkdir -p "$SIMULACION/http-despues"
  printf '502' >"$SIMULACION/http-despues/_api_auth_csrf"
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --url https://127.0.0.1:8443 --aplicar
  r=$(esperar 3 "GET /api/auth/csrf responde '502'")
  grep -qF "GET /api/auth/csrf responde 204" "$SALIDA" || r=NO
  comprobar "Fallo de salud HTTP tras actualizar: codigo 3" "$r"
}

prueba_salud_previa_no_bloquea() {
  nueva_simulacion
  local suma r
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  printf '502' >"$SIMULACION/http/_"
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --url https://127.0.0.1:8443
  r=$(esperar 0 "GET / responde '502' antes del cambio")
  comprobar "Aplicacion caida antes del cambio: aviso, no bloquea un rollback" "$r"
}

prueba_sin_aprobacion_humana() {
  nueva_simulacion
  local suma r registro
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --sin-http --aplicar
  r=$(esperar 0 "paquete preparado con el environment vm-universitaria verificado")
  registro=$(find "$REGISTRO_PRUEBA" -mindepth 1 -maxdepth 1 -type d -name "*-${COMMIT:0:12}" | head -n 1)
  [[ -n $registro && -f $registro/informe.txt ]] || r=NO
  ! grep -qi 'aprob' "$SALIDA" || r=NO
  ! grep -rqi 'aprob' "$registro" || r=NO
  comprobar "Aplicacion sin anunciar ni registrar una aprobacion humana" "$r"
}

prueba_paquete_rechazado() {
  local nombre=$1 codigo=$2 patron=$3 manipular=${4:-}
  shift 4
  nueva_simulacion
  local suma r
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz" "$manipular")
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --sin-http --aplicar "$@"
  r=$(esperar "$codigo" "$patron")
  [[ $(mutaciones) == 0 ]] || r=NO
  comprobar "$nombre" "$r"
  SED_METADATOS=""
  YAML_BACKEND=""
}

manipular_deployments() { printf '# cambio\n' >>"$1/paquete-despliegue/deployments.yaml"; }
anadir_entrada() { printf 'echo hola\n' >"$1/paquete-despliegue/extra.sh"; }
quitar_resumen() { rm "$1/paquete-despliegue/RESUMEN.md"; }
anadir_enlace() { ln -s /etc/passwd "$1/paquete-despliegue/enlace"; }
recalcular() { (cd "$1/paquete-despliegue" && sha256sum RESUMEN.md deployments.yaml metadatos.txt migraciones.txt >SHA256SUMS); }
anadir_crlf() { sed -i 's/$/\r/' "$1/paquete-despliegue/metadatos.txt"; recalcular "$1"; }
anadir_secreto_yaml() { printf -- '---\napiVersion: v1\nkind: Secret\nmetadata:\n  name: procesos-backend\n  namespace: procesos-empresariales\n' >>"$1/paquete-despliegue/deployments.yaml"; recalcular "$1"; }

prueba_ruta_que_escapa() {
  nueva_simulacion
  local suma r dir="$TEMPORAL/escape-$TOTAL"
  mkdir -p "$dir/paquete-despliegue"
  construir_paquete "$TEMPORAL/base.tar.gz" >/dev/null
  tar -xzf "$TEMPORAL/base.tar.gz" -C "$dir"
  printf 'x\n' >"$TEMPORAL/fuera.txt"
  tar --sort=name -czf "$TEMPORAL/p.tar.gz" -C "$dir" paquete-despliegue --transform 's|^paquete-despliegue/RESUMEN.md$|paquete-despliegue/../../fuera.txt|' 2>/dev/null
  suma=$(sha256sum "$TEMPORAL/p.tar.gz" | cut -d' ' -f1)
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" --sin-http --aplicar
  r=$(esperar 1 "entrada no permitida en el paquete: paquete-despliegue/../../fuera.txt")
  [[ $(mutaciones) == 0 ]] || r=NO
  comprobar "Ruta que escapa del paquete (..)" "$r"
}

prueba_cluster() {
  local nombre=$1 codigo=$2 patron=$3 preparar=$4
  shift 4
  nueva_simulacion
  local suma r
  suma=$(construir_paquete "$TEMPORAL/p.tar.gz")
  "$preparar"
  ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$suma" "$@"
  r=$(esperar "$codigo" "$patron")
  [[ $codigo != 1 || $(mutaciones) == 0 ]] || r=NO
  comprobar "$nombre" "$r"
}

sin_namespace() { rm "$SIMULACION/namespace/procesos-empresariales"; }
namespace_baseline() { printf 'baseline' >"$SIMULACION/namespace/procesos-empresariales"; }
sin_deployment() { rm -r "$SIMULACION/deploy/procesos-frontend"; }
contenedor_extra() { printf 'app espia %s\n' "$FRONTEND_ACTUAL" >>"$SIMULACION/deploy/procesos-frontend/contenedores"; }
postgres_caido() { printf '1 0' >"$SIMULACION/sts/procesos-postgres"; }
rechazo_servidor() { touch "$SIMULACION/fallos/set-procesos-backend"; }
operacion_en_curso() { mkdir -p "$REGISTRO_PRUEBA/.bloqueo"; }
mismas_imagenes() {
  printf 'init esperar-postgres %s\napp backend %s\n' "$BACKEND_NUEVO" "$BACKEND_NUEVO" >"$SIMULACION/deploy/procesos-backend/contenedores"
  printf 'app frontend %s\n' "$FRONTEND_NUEVO" >"$SIMULACION/deploy/procesos-frontend/contenedores"
}
backend_distinto() { printf 'init esperar-postgres %s\napp backend %s\n' "$REPO_BACKEND@$(digest 8)" "$REPO_BACKEND@$(digest 8)" >"$SIMULACION/deploy/procesos-backend/contenedores"; }
ninguna() { :; }

prueba_uso() {
  local nombre=$1 patron=$2
  shift 2
  nueva_simulacion
  ejecutar "$@"
  comprobar "$nombre" "$(esperar 2 "$patron")"
}

prueba_registro_manipulado() {
  nueva_simulacion
  local dir="$REGISTRO_PRUEBA/20261117T000000Z-revertir" r
  mkdir -p "$dir"
  printf 'procesos-backend init esperar-postgres %s\nprocesos-backend app backend %s:latest\nprocesos-frontend app frontend %s\n' "$BACKEND_ACTUAL" "$REPO_BACKEND" "$FRONTEND_ACTUAL" >"$dir/imagenes-anteriores.txt"
  ejecutar --revertir "$dir" --sin-http --aplicar
  r=$(esperar 1 "imagen no autorizada o sin digest en el registro")
  [[ $(mutaciones) == 0 ]] || r=NO
  comprobar "Registro de rollback con imagen sin digest" "$r"
  nueva_simulacion
  dir="$REGISTRO_PRUEBA/20261117T000000Z-revertir"
  mkdir -p "$dir"
  printf 'procesos-backend init esperar-postgres %s\nprocesos-backend app backend %s\nprocesos-postgres app postgres %s\n' "$BACKEND_ACTUAL" "$BACKEND_ACTUAL" "$FRONTEND_ACTUAL" >"$dir/imagenes-anteriores.txt"
  ejecutar --revertir "$dir" --sin-http
  r=$(esperar 1 "linea no valida en el registro")
  comprobar "Registro de rollback con recurso no autorizado (PostgreSQL)" "$r"
  nueva_simulacion
  mkdir -p "$TEMPORAL/externo"
  cp "$dir/imagenes-anteriores.txt" "$TEMPORAL/externo/" 2>/dev/null || true
  ejecutar --revertir "$TEMPORAL/externo" --sin-http
  comprobar "Registro de rollback fuera del directorio de registros" "$(esperar 1 "el registro debe estar dentro de")"
}

printf 'Pruebas de desplegar-vm.sh con kubectl y curl simulados\n\n'
prueba_comprobacion_correcta
prueba_aplicacion_correcta
prueba_revertir_tras_aplicar
prueba_rollout_fallido_y_rollback
prueba_fallo_salud
prueba_salud_previa_no_bloquea

SED_METADATOS="s|^backend_imagen=.*|backend_imagen=$REPO_BACKEND@sha256:$(printf '%063x' 3)|"
prueba_paquete_rechazado "Digest invalido en los metadatos" 1 "valor no valido para backend_imagen" ""
SED_METADATOS="s|^backend_imagen=.*|backend_imagen=$REPO_BACKEND:sha-abc1234|"
prueba_paquete_rechazado "Imagen por etiqueta sin digest" 1 "valor no valido para backend_imagen" ""
SED_METADATOS="s|^frontend_imagen=.*|frontend_imagen=docker.io/atacante/frontend@$(digest 4)|"
prueba_paquete_rechazado "Imagen de un registro no autorizado" 1 "valor no valido para frontend_imagen" ""
SED_METADATOS="s|^namespace=.*|namespace=kube-system|"
prueba_paquete_rechazado "Namespace inesperado en los metadatos" 1 "valor no valido para namespace" ""
SED_METADATOS="\$a comando=rm -rf /"
prueba_paquete_rechazado "Instruccion arbitraria en los metadatos" 1 "linea no valida en metadatos.txt" ""
SED_METADATOS="\$a ejecutar=curl"
prueba_paquete_rechazado "Clave desconocida en los metadatos" 1 "clave no admitida en metadatos.txt: ejecutar" ""
SED_METADATOS="\$a formato=1"
prueba_paquete_rechazado "Clave duplicada en los metadatos" 1 "clave duplicada en metadatos.txt" ""
SED_METADATOS='/^verificacion_entorno=/d'
prueba_paquete_rechazado "Metadatos incompletos" 1 "falta verificacion_entorno en metadatos.txt" ""
prueba_paquete_rechazado "Paquete manipulado tras calcular SHA256SUMS" 1 "SHA-256 de los archivos del paquete" manipular_deployments
prueba_paquete_rechazado "Entrada adicional en el paquete" 1 "entrada no permitida en el paquete: paquete-despliegue/extra.sh" anadir_entrada
prueba_paquete_rechazado "Paquete incompleto" 1 "paquete incompleto: falta paquete-despliegue/RESUMEN.md" quitar_resumen
prueba_paquete_rechazado "Enlace simbolico en el paquete" 1 "entrada no permitida en el paquete: paquete-despliegue/enlace" anadir_enlace
prueba_paquete_rechazado "Metadatos con CRLF" 1 "metadatos.txt tiene finales de linea CRLF" anadir_crlf
prueba_paquete_rechazado "Secret dentro de deployments.yaml" 1 "deployments.yaml solo puede contener los dos Deployments" anadir_secreto_yaml
YAML_BACKEND="$REPO_BACKEND@$(digest 6)"
prueba_paquete_rechazado "deployments.yaml con una imagen distinta de los metadatos" 1 "usa una imagen que no figura en los metadatos" ""
prueba_ruta_que_escapa

nueva_simulacion
construir_paquete "$TEMPORAL/p.tar.gz" >/dev/null
ejecutar --paquete "$TEMPORAL/p.tar.gz" --sha256 "$(printf '%064x' 0)" --sin-http --aplicar
comprobar "SHA-256 del paquete distinto del publicado" "$(esperar 1 "distinto del esperado")"

nueva_simulacion
printf 'no es un tar' >"$TEMPORAL/roto.tar.gz"
ejecutar --paquete "$TEMPORAL/roto.tar.gz"
comprobar "Archivo que no es un tar.gz" "$(esperar 1 "el paquete no es un tar.gz legible")"

prueba_cluster "Namespace inexistente en el cluster" 1 "no se puede leer el namespace procesos-empresariales" sin_namespace --sin-http --aplicar
prueba_cluster "Namespace sin Pod Security restricted" 1 "no tiene pod-security.kubernetes.io/enforce=restricted" namespace_baseline --sin-http --aplicar
prueba_cluster "Deployment ausente" 1 "no existe el Deployment procesos-frontend" sin_deployment --sin-http --aplicar
prueba_cluster "Contenedor inesperado en un Deployment" 1 "procesos-frontend tiene contenedores inesperados" contenedor_extra --sin-http --aplicar
prueba_cluster "PostgreSQL no listo" 1 "PostgreSQL no esta listo" postgres_caido --sin-http --aplicar
prueba_cluster "El servidor rechaza la imagen en el dry-run" 1 "el servidor rechaza la nueva imagen de procesos-backend" rechazo_servidor --sin-http --aplicar
prueba_cluster "Otra operacion en curso" 1 "hay otra operacion en curso" operacion_en_curso --sin-http --aplicar
prueba_cluster "Imagenes ya desplegadas: nada que actualizar" 0 "No hay imagenes que actualizar." mismas_imagenes --sin-http --aplicar
prueba_cluster "Version desplegada distinta de la anterior en Git sin revision de migraciones" 1 "repita con --migraciones-revisadas" backend_distinto --sin-http --aplicar
prueba_cluster "Version desplegada distinta con --migraciones-revisadas" 0 "Resultado: CORRECTO" backend_distinto --sin-http --aplicar --migraciones-revisadas

SED_METADATOS="s|^migraciones_nuevas=.*|migraciones_nuevas=2|"
prueba_paquete_rechazado "Migraciones nuevas sin revision confirmada" 1 "incluye 2 migraciones de Flyway nuevas" ""
SED_METADATOS="s|^verificacion_entorno=.*|verificacion_entorno=ninguna|"
prueba_paquete_rechazado "Paquete preparado sin verificar el environment" 1 "se preparo sin verificar el environment vm-universitaria" ""
SED_METADATOS="s|^verificacion_entorno=.*|verificacion_entorno=ninguna|"
prueba_cluster "Paquete sin environment verificado aceptado de forma explicita" 0 "Resultado: CORRECTO" ninguna --sin-http --aplicar --sin-entorno-github
SED_METADATOS="s|^verificacion_entorno=|aprobacion=|"
prueba_cluster "Metadatos anteriores con aprobacion=environment:vm-universitaria" 0 "paquete preparado con el environment vm-universitaria verificado" ninguna --sin-http --aplicar
SED_METADATOS="s|^verificacion_entorno=.*|aprobacion=ninguna|"
prueba_cluster "Metadatos y opcion anteriores sin environment verificado" 0 "(--sin-entorno-github)" ninguna --sin-http --aplicar --sin-aprobacion-github
SED_METADATOS="\$a aprobacion=environment:vm-universitaria"
prueba_paquete_rechazado "Clave nueva y anterior del environment a la vez" 1 "clave duplicada en metadatos.txt: verificacion_entorno" ""
SED_METADATOS=""
prueba_sin_aprobacion_humana

prueba_registro_manipulado

prueba_uso "Sin argumentos" "indique --paquete o --revertir"
prueba_uso "Opcion desconocida" "opcion desconocida: --forzar" --paquete x.tar.gz --forzar
prueba_uso "Aplicar sin --sha256" "--aplicar exige --sha256" --paquete x.tar.gz --sin-http --aplicar
prueba_uso "Aplicar sin --url ni --sin-http" "--aplicar exige --url" --paquete x.tar.gz --sha256 "$(printf '%064x' 1)" --aplicar
prueba_uso "URL de salud no local" "--url solo admite" --paquete x.tar.gz --url https://10.43.99.73
prueba_uso "Tiempo maximo fuera de rango" "--tiempo-maximo debe estar entre" --paquete x.tar.gz --tiempo-maximo 5
prueba_uso "Paquete y revertir a la vez" "indique --paquete o --revertir" --paquete x.tar.gz --revertir y

printf '\n%d de %d pruebas correctas\n' "$CORRECTOS" "$TOTAL"
if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  {
    printf '### Pruebas de desplegar-vm.sh: %d de %d correctas\n\n' "$CORRECTOS" "$TOTAL"
    printf '| # | Prueba | Codigo | Correcta |\n| ---: | --- | ---: | --- |\n'
    printf '%s\n' "${FILAS[@]}"
    printf '\n'
  } >>"$GITHUB_STEP_SUMMARY"
fi
if ((${#FALLIDOS[@]} > 0)); then
  printf 'Fallidas:\n'
  printf '  - %s\n' "${FALLIDOS[@]}"
  exit 1
fi
