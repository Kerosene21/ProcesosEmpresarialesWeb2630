#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly NAMESPACE="procesos-empresariales"
readonly REPOSITORIO="Kerosene21/ProcesosEmpresarialesWeb2630"
readonly RAIZ_PAQUETE="paquete-despliegue"
readonly -a ARCHIVOS_PAQUETE=(RESUMEN.md deployments.yaml metadatos.txt migraciones.txt)
readonly -a DEPLOYMENTS=(procesos-backend procesos-frontend)
readonly TAMANO_MAXIMO_PAQUETE=1048576
readonly TAMANO_MAXIMO_ARCHIVO=262144
readonly APROBACION_GITHUB="environment:vm-universitaria"
readonly REGEX_DIGEST='^[0-9a-f]{64}$'
readonly REGEX_URL='^https?://(127\.0\.0\.1|localhost|\[::1\])(:[0-9]{1,5})?$'
declare -rA REGEX_IMAGEN=(
  [backend]='^ghcr\.io/kerosene21/procesos-empresariales-backend@sha256:[0-9a-f]{64}$'
  [frontend]='^ghcr\.io/kerosene21/procesos-empresariales-frontend@sha256:[0-9a-f]{64}$'
)
declare -rA COMPONENTE=([procesos-backend]=backend [procesos-frontend]=frontend)
declare -rA CONTENEDORES=([procesos-backend]="init/esperar-postgres app/backend" [procesos-frontend]="app/frontend")
declare -rA TIEMPO_ROLLOUT=([procesos-backend]=420 [procesos-frontend]=180)
declare -rA REGEX_METADATOS=(
  [formato]='^1$'
  [repositorio]='^Kerosene21/ProcesosEmpresarialesWeb2630$'
  [commit]='^[0-9a-f]{40}$'
  [commit_fecha]='^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$'
  [namespace]='^procesos-empresariales$'
  [backend_imagen]='^ghcr\.io/kerosene21/procesos-empresariales-backend@sha256:[0-9a-f]{64}$'
  [backend_revision]='^[0-9a-f]{40}$'
  [frontend_imagen]='^ghcr\.io/kerosene21/procesos-empresariales-frontend@sha256:[0-9a-f]{64}$'
  [frontend_revision]='^[0-9a-f]{40}$'
  [backend_anterior]='^(ninguna|ghcr\.io/kerosene21/procesos-empresariales-backend@sha256:[0-9a-f]{64})$'
  [migraciones_nuevas]='^([0-9]{1,3}|desconocido)$'
  [aprobacion]='^(ninguna|environment:vm-universitaria)$'
)
readonly JSONPATH_CONTENEDORES='{range .spec.template.spec.initContainers[*]}init {.name} {.image}{"\n"}{end}{range .spec.template.spec.containers[*]}app {.name} {.image}{"\n"}{end}'
readonly JSONPATH_ESTADO='{.spec.replicas} {.status.readyReplicas} {.status.updatedReplicas} {.status.availableReplicas} {.metadata.generation} {.status.observedGeneration}'
readonly JSONPATH_PODS='{range .items[*]}{.metadata.name}|{.metadata.deletionTimestamp}|{range .spec.containers[*]}{.image},{end}{"\n"}{end}'

read -r -a KUBECTL <<<"${KUBECTL:-microk8s kubectl}"
read -r -a CURL <<<"${CURL:-curl}"

PAQUETE=""
REVERTIR=""
SHA_ESPERADO=""
URL=""
SIN_HTTP=false
APLICAR=false
MIGRACIONES_REVISADAS=false
SIN_APROBACION=false
REGISTRO_BASE="${HOME:-.}/despliegues-procesos-empresariales"
TIEMPO_MAXIMO=""
TRABAJO=""
BLOQUEO=""
REGISTRO=""
ERRORES=0
CAMBIOS=()
declare -A META=()
declare -A ACTUAL=()
declare -A SOLICITADA=()

uso() {
  cat <<'TEXTO'
Uso:
  desplegar-vm.sh --paquete ARCHIVO.tar.gz [--sha256 HASH] [opciones] [--aplicar]
  desplegar-vm.sh --revertir DIRECTORIO_DE_REGISTRO [opciones] [--aplicar]

Sin --aplicar solo se comprueba: no se modifica ningun recurso.

Opciones:
  --sha256 HASH              SHA-256 del paquete que muestra el resumen de GitHub Actions (obligatorio con --aplicar)
  --url URL                  Direccion local de Traefik para comprobar la salud, por ejemplo https://127.0.0.1:PUERTO
  --sin-http                 Omite las comprobaciones HTTP
  --registro DIRECTORIO      Directorio de registros (por defecto ~/despliegues-procesos-empresariales)
  --tiempo-maximo SEGUNDOS   Tiempo maximo de cada rollout (60-1800)
  --migraciones-revisadas    Confirma la revision de compatibilidad y el respaldo de PostgreSQL
  --sin-aprobacion-github    Acepta un paquete preparado sin el environment vm-universitaria
  --aplicar                  Actualiza las imagenes del backend y del frontend

Codigos de salida: 0 correcto, 1 comprobacion fallida sin cambios, 2 uso incorrecto,
3 fallo despues de modificar el cluster (revertir con --revertir).
TEXTO
}

bitacora() {
  printf '%s\n' "$@"
  if [[ -n $TRABAJO ]]; then
    printf '%s\n' "$@" >>"$TRABAJO/bitacora.txt"
  fi
}

seccion() { bitacora "" "== $1 =="; }
correcto() { bitacora "  OK     $*"; }
aviso() { bitacora "  AVISO  $*"; }
fallo() {
  bitacora "  ERROR  $*"
  ERRORES=$((ERRORES + 1))
}

error_uso() {
  printf 'Error: %s\n\n' "$1" >&2
  uso >&2
  exit 2
}

terminar_si_hay_errores() {
  if ((ERRORES > 0)); then
    bitacora "" "Comprobacion fallida: $ERRORES errores. No se modifico ningun recurso."
    exit 1
  fi
}

limpiar() {
  if [[ -n $BLOQUEO && -d $BLOQUEO ]]; then
    rmdir "$BLOQUEO" 2>/dev/null || true
  fi
  if [[ -n $TRABAJO && -d $TRABAJO ]]; then
    rm -rf "$TRABAJO"
  fi
}

k() {
  "${KUBECTL[@]}" "$@"
}

contenedores_de() {
  tr ' ' '\n' <<<"${CONTENEDORES[$1]}"
}

opcion_salud() {
  if [[ -n $URL ]]; then
    printf -- '--url %s' "$URL"
  else
    printf -- '--sin-http'
  fi
}

valor_opcion() {
  if [[ $# -lt 2 || -z $2 ]]; then
    error_uso "$1 necesita un valor"
  fi
}

leer_argumentos() {
  while (($# > 0)); do
    case $1 in
      --paquete) valor_opcion "$@"; PAQUETE=$2; shift 2 ;;
      --revertir) valor_opcion "$@"; REVERTIR=$2; shift 2 ;;
      --sha256) valor_opcion "$@"; SHA_ESPERADO=$2; shift 2 ;;
      --url) valor_opcion "$@"; URL=${2%/}; shift 2 ;;
      --registro) valor_opcion "$@"; REGISTRO_BASE=$2; shift 2 ;;
      --tiempo-maximo) valor_opcion "$@"; TIEMPO_MAXIMO=$2; shift 2 ;;
      --sin-http) SIN_HTTP=true; shift ;;
      --aplicar) APLICAR=true; shift ;;
      --migraciones-revisadas) MIGRACIONES_REVISADAS=true; shift ;;
      --sin-aprobacion-github) SIN_APROBACION=true; shift ;;
      -h | --ayuda) uso; exit 0 ;;
      *) error_uso "opcion desconocida: $1" ;;
    esac
  done
  if [[ -n $PAQUETE && -n $REVERTIR ]] || [[ -z $PAQUETE && -z $REVERTIR ]]; then
    error_uso "indique --paquete o --revertir, pero no ambos"
  fi
  if [[ -n $SHA_ESPERADO && ! $SHA_ESPERADO =~ $REGEX_DIGEST ]]; then
    error_uso "--sha256 debe tener 64 caracteres hexadecimales en minuscula"
  fi
  if [[ -n $URL && ! $URL =~ $REGEX_URL ]]; then
    error_uso "--url solo admite 127.0.0.1, localhost o [::1], con puerto opcional"
  fi
  if [[ -n $URL && $SIN_HTTP == true ]]; then
    error_uso "--url y --sin-http son incompatibles"
  fi
  if [[ -n $TIEMPO_MAXIMO ]] && { [[ ! $TIEMPO_MAXIMO =~ ^[0-9]{2,4}$ ]] || ((TIEMPO_MAXIMO < 60 || TIEMPO_MAXIMO > 1800)); }; then
    error_uso "--tiempo-maximo debe estar entre 60 y 1800 segundos"
  fi
  if [[ $APLICAR == true && -z $URL && $SIN_HTTP == false ]]; then
    error_uso "--aplicar exige --url para comprobar la salud o --sin-http para omitirla de forma explicita"
  fi
  if [[ $APLICAR == true && -n $PAQUETE && -z $SHA_ESPERADO ]]; then
    error_uso "--aplicar exige --sha256 con el valor publicado en el resumen de GitHub Actions"
  fi
  if [[ -n $REVERTIR ]] && [[ $MIGRACIONES_REVISADAS == true || $SIN_APROBACION == true || -n $SHA_ESPERADO ]]; then
    error_uso "--migraciones-revisadas, --sin-aprobacion-github y --sha256 solo se usan con --paquete"
  fi
}

requerir_herramientas() {
  local herramienta faltan=()
  for herramienta in tar sha256sum stat mktemp realpath grep sed awk date; do
    command -v "$herramienta" >/dev/null 2>&1 || faltan+=("$herramienta")
  done
  if [[ $SIN_HTTP == false && -n $URL ]] && ! command -v "${CURL[0]}" >/dev/null 2>&1; then
    faltan+=("${CURL[0]}")
  fi
  if ! command -v "${KUBECTL[0]}" >/dev/null 2>&1; then
    faltan+=("${KUBECTL[0]}")
  fi
  if ((${#faltan[@]} > 0)); then
    printf 'Faltan herramientas: %s\n' "${faltan[*]}" >&2
    exit 2
  fi
}

es_entrada_permitida() {
  local entrada=$1 archivo
  [[ $entrada == "$RAIZ_PAQUETE/" || $entrada == "$RAIZ_PAQUETE/SHA256SUMS" ]] && return 0
  for archivo in "${ARCHIVOS_PAQUETE[@]}"; do
    [[ $entrada == "$RAIZ_PAQUETE/$archivo" ]] && return 0
  done
  return 1
}

validar_entradas_tar() {
  local entrada linea total=0 tipos=0
  declare -A vistas=()
  if ! tar -tzf "$PAQUETE" >"$TRABAJO/entradas.txt" 2>"$TRABAJO/tar.err"; then
    fallo "el paquete no es un tar.gz legible: $(head -n 1 "$TRABAJO/tar.err")"
    return
  fi
  while IFS= read -r entrada; do
    total=$((total + 1))
    if ! es_entrada_permitida "$entrada"; then
      fallo "entrada no permitida en el paquete: $entrada"
    elif [[ -n ${vistas[$entrada]:-} ]]; then
      fallo "entrada duplicada en el paquete: $entrada"
    fi
    vistas[$entrada]=1
  done <"$TRABAJO/entradas.txt"
  for entrada in "$RAIZ_PAQUETE/SHA256SUMS" "${ARCHIVOS_PAQUETE[@]/#/$RAIZ_PAQUETE/}"; do
    [[ -n ${vistas[$entrada]:-} ]] || fallo "paquete incompleto: falta $entrada"
  done
  tar -tvzf "$PAQUETE" >"$TRABAJO/tipos.txt" 2>/dev/null || true
  while IFS= read -r linea; do
    tipos=$((tipos + 1))
    [[ $linea == -* || $linea == d* ]] || fallo "el paquete contiene enlaces o archivos especiales: ${linea:0:60}"
  done <"$TRABAJO/tipos.txt"
  ((tipos == total)) || fallo "no se pudo comprobar el tipo de todas las entradas del paquete"
}

validar_paquete() {
  local tamano suma linea nombre archivo
  seccion "paquete"
  if [[ ! -f $PAQUETE || -L $PAQUETE || $PAQUETE != *.tar.gz ]]; then
    fallo "el paquete debe ser un archivo .tar.gz regular: $PAQUETE"
    terminar_si_hay_errores
  fi
  tamano=$(stat -c %s "$PAQUETE")
  if ((tamano > TAMANO_MAXIMO_PAQUETE)); then
    fallo "el paquete ocupa $tamano bytes; el maximo es $TAMANO_MAXIMO_PAQUETE"
    terminar_si_hay_errores
  fi
  suma=$(sha256sum "$PAQUETE" | cut -d' ' -f1)
  if [[ -z $SHA_ESPERADO ]]; then
    aviso "SHA-256 del paquete $suma sin comparar: indique --sha256 con el valor del resumen de GitHub Actions"
  elif [[ $suma == "$SHA_ESPERADO" ]]; then
    correcto "SHA-256 del paquete coincide con el publicado en GitHub Actions"
  else
    fallo "SHA-256 del paquete $suma distinto del esperado $SHA_ESPERADO"
  fi
  validar_entradas_tar
  terminar_si_hay_errores
  mkdir "$TRABAJO/paquete"
  tar -xzf "$PAQUETE" -C "$TRABAJO/paquete" --no-same-owner --no-same-permissions
  for archivo in SHA256SUMS "${ARCHIVOS_PAQUETE[@]}"; do
    nombre="$TRABAJO/paquete/$RAIZ_PAQUETE/$archivo"
    if [[ ! -f $nombre || -L $nombre ]] || (($(stat -c %s "$nombre") > TAMANO_MAXIMO_ARCHIVO)); then
      fallo "$archivo no es un archivo regular de tamano razonable"
    fi
  done
  terminar_si_hay_errores
  while IFS= read -r linea || [[ -n $linea ]]; do
    if [[ ! $linea =~ ^[0-9a-f]{64}\ \ (RESUMEN\.md|deployments\.yaml|metadatos\.txt|migraciones\.txt)$ ]]; then
      fallo "linea no valida en SHA256SUMS: ${linea:0:80}"
    fi
  done <"$TRABAJO/paquete/$RAIZ_PAQUETE/SHA256SUMS"
  if (($(grep -c '' "$TRABAJO/paquete/$RAIZ_PAQUETE/SHA256SUMS") != ${#ARCHIVOS_PAQUETE[@]})) ||
    (($(cut -c67- "$TRABAJO/paquete/$RAIZ_PAQUETE/SHA256SUMS" | sort -u | grep -c '') != ${#ARCHIVOS_PAQUETE[@]})); then
    fallo "SHA256SUMS debe listar una vez cada archivo del paquete"
  fi
  terminar_si_hay_errores
  if (cd "$TRABAJO/paquete/$RAIZ_PAQUETE" && sha256sum --check --strict --quiet SHA256SUMS) >"$TRABAJO/sumas.txt" 2>&1; then
    correcto "estructura y SHA-256 de los ${#ARCHIVOS_PAQUETE[@]} archivos del paquete"
  else
    fallo "SHA-256 de los archivos del paquete: $(head -n 2 "$TRABAJO/sumas.txt" | tr '\n' ' ')"
  fi
  terminar_si_hay_errores
}

leer_metadatos() {
  local archivo="$TRABAJO/paquete/$RAIZ_PAQUETE/metadatos.txt" linea clave valor
  seccion "metadatos"
  while IFS= read -r linea || [[ -n $linea ]]; do
    if [[ $linea == *$'\r'* ]]; then
      fallo "metadatos.txt tiene finales de linea CRLF"
      break
    fi
    if [[ ! $linea =~ ^([a-z_]+)=([^[:space:]]*)$ ]]; then
      fallo "linea no valida en metadatos.txt: ${linea:0:80}"
      continue
    fi
    clave=${BASH_REMATCH[1]}
    valor=${BASH_REMATCH[2]}
    if [[ -z ${REGEX_METADATOS[$clave]:-} ]]; then
      fallo "clave no admitida en metadatos.txt: $clave"
    elif [[ -n ${META[$clave]+x} ]]; then
      fallo "clave duplicada en metadatos.txt: $clave"
    elif [[ ! $valor =~ ${REGEX_METADATOS[$clave]} ]]; then
      fallo "valor no valido para $clave en metadatos.txt"
    else
      META[$clave]=$valor
    fi
  done <"$archivo"
  for clave in "${!REGEX_METADATOS[@]}"; do
    [[ -n ${META[$clave]+x} ]] || fallo "falta $clave en metadatos.txt"
  done
  terminar_si_hay_errores
  correcto "commit ${META[commit]} de $REPOSITORIO (${META[commit_fecha]})"
  correcto "backend ${META[backend_imagen]} (revision ${META[backend_revision]:0:12})"
  correcto "frontend ${META[frontend_imagen]} (revision ${META[frontend_revision]:0:12})"
  if [[ ${META[aprobacion]} == "$APROBACION_GITHUB" ]]; then
    correcto "preparado con la aprobacion del environment vm-universitaria"
  else
    aviso "preparado sin environment de aprobacion en GitHub"
  fi
  SOLICITADA[procesos-backend/esperar-postgres]=${META[backend_imagen]}
  SOLICITADA[procesos-backend/backend]=${META[backend_imagen]}
  SOLICITADA[procesos-frontend/frontend]=${META[frontend_imagen]}
}

validar_deployments_yaml() {
  local archivo="$TRABAJO/paquete/$RAIZ_PAQUETE/deployments.yaml" imagen backend=0 frontend=0
  if [[ $(grep -c '^kind: ' "$archivo") != 2 || $(grep -c '^kind: Deployment$' "$archivo") != 2 ]]; then
    fallo "deployments.yaml solo puede contener los dos Deployments"
  fi
  if [[ $(grep -E '^  name: ' "$archivo" | sort | tr '\n' ' ') != "  name: procesos-backend   name: procesos-frontend " ]]; then
    fallo "deployments.yaml contiene nombres de recursos inesperados"
  fi
  if [[ $(grep -c '^  namespace: ' "$archivo") != 2 || $(grep -c "^  namespace: $NAMESPACE\$" "$archivo") != 2 ]]; then
    fallo "deployments.yaml contiene un namespace inesperado"
  fi
  while IFS= read -r imagen; do
    if [[ $imagen == "${META[backend_imagen]}" ]]; then
      backend=$((backend + 1))
    elif [[ $imagen == "${META[frontend_imagen]}" ]]; then
      frontend=$((frontend + 1))
    else
      fallo "deployments.yaml usa una imagen que no figura en los metadatos: $imagen"
    fi
  done < <(sed -n -E 's/^ +(- )?image: *//p' "$archivo")
  ((backend == 2 && frontend == 1)) || fallo "deployments.yaml no tiene las imagenes esperadas del backend y del frontend"
  terminar_si_hay_errores
  correcto "deployments.yaml solo contiene procesos-backend y procesos-frontend con las imagenes del paquete"
}

dentro_del_registro() {
  local base real
  base=$(realpath -m "$REGISTRO_BASE")
  real=$(realpath -m "$1")
  [[ $real == "$base"/* ]]
}

leer_registro_anterior() {
  local archivo linea deployment tipo contenedor imagen esperado
  seccion "registro para revertir"
  if ! dentro_del_registro "$REVERTIR"; then
    fallo "el registro debe estar dentro de $REGISTRO_BASE"
    terminar_si_hay_errores
  fi
  archivo="$(realpath -m "$REVERTIR")/imagenes-anteriores.txt"
  if [[ ! -f $archivo || -L $archivo ]] || (($(stat -c %s "$archivo") > 65536)); then
    fallo "no existe un registro valido en $archivo"
    terminar_si_hay_errores
  fi
  while IFS= read -r linea || [[ -n $linea ]]; do
    if [[ ! $linea =~ ^(procesos-backend|procesos-frontend)\ (init|app)\ ([a-z0-9-]+)\ ([^[:space:]]+)$ ]]; then
      fallo "linea no valida en el registro: ${linea:0:80}"
      continue
    fi
    deployment=${BASH_REMATCH[1]}
    tipo=${BASH_REMATCH[2]}
    contenedor=${BASH_REMATCH[3]}
    imagen=${BASH_REMATCH[4]}
    esperado=" ${CONTENEDORES[$deployment]} "
    if [[ $esperado != *" $tipo/$contenedor "* ]]; then
      fallo "contenedor inesperado en el registro: $deployment $tipo/$contenedor"
    elif [[ -n ${SOLICITADA[$deployment/$contenedor]:-} ]]; then
      fallo "contenedor duplicado en el registro: $deployment/$contenedor"
    elif [[ ! $imagen =~ ${REGEX_IMAGEN[${COMPONENTE[$deployment]}]} ]]; then
      fallo "imagen no autorizada o sin digest en el registro: $imagen"
    else
      SOLICITADA[$deployment/$contenedor]=$imagen
    fi
  done <"$archivo"
  for deployment in "${DEPLOYMENTS[@]}"; do
    while IFS= read -r contenedor; do
      [[ -n ${SOLICITADA[$deployment/${contenedor#*/}]:-} ]] || fallo "falta $deployment/${contenedor#*/} en el registro"
    done < <(contenedores_de "$deployment")
  done
  terminar_si_hay_errores
  correcto "registro $archivo"
  aviso "revertir las imagenes no revierte las migraciones de Flyway ni los datos de PostgreSQL"
}

comprobar_namespace() {
  local enforce
  seccion "cluster"
  if ! enforce=$(k get namespace "$NAMESPACE" -o jsonpath='{.metadata.labels.pod-security\.kubernetes\.io/enforce}' 2>"$TRABAJO/k.err"); then
    fallo "no se puede leer el namespace $NAMESPACE: $(head -n 1 "$TRABAJO/k.err")"
    terminar_si_hay_errores
  fi
  if [[ $enforce == restricted ]]; then
    correcto "namespace $NAMESPACE con Pod Security restricted"
  else
    fallo "el namespace $NAMESPACE no tiene pod-security.kubernetes.io/enforce=restricted"
  fi
}

leer_contenedores() {
  local deployment=$1 salida tipo contenedor imagen esperados encontrados=""
  if ! salida=$(k -n "$NAMESPACE" get deployment "$deployment" -o jsonpath="$JSONPATH_CONTENEDORES" 2>"$TRABAJO/k.err"); then
    fallo "no existe el Deployment $deployment: $(head -n 1 "$TRABAJO/k.err")"
    return
  fi
  while read -r tipo contenedor imagen; do
    [[ -n $tipo ]] || continue
    encontrados+=" $tipo/$contenedor"
    ACTUAL[$deployment/$contenedor]=$imagen
  done <<<"$salida"
  esperados=$(contenedores_de "$deployment" | sort | tr '\n' ' ')
  if [[ $(tr ' ' '\n' <<<"${encontrados# }" | sort | tr '\n' ' ') != "$esperados" ]]; then
    fallo "$deployment tiene contenedores inesperados:${encontrados:- ninguno}"
  else
    correcto "$deployment con los contenedores esperados (${CONTENEDORES[$deployment]})"
  fi
}

estado_deployment() {
  local deployment=$1 replicas listos actualizados disponibles generacion observada
  read -r replicas listos actualizados disponibles generacion observada < <(k -n "$NAMESPACE" get deployment "$deployment" -o jsonpath="$JSONPATH_ESTADO" 2>/dev/null; echo) || true
  printf '%s %s %s %s %s %s\n' "${replicas:-0}" "${listos:-0}" "${actualizados:-0}" "${disponibles:-0}" "${generacion:-0}" "${observada:-0}"
}

deployment_sano() {
  local replicas listos actualizados disponibles generacion observada
  read -r replicas listos actualizados disponibles generacion observada <<<"$(estado_deployment "$1")"
  ((replicas > 0 && listos == replicas && actualizados == replicas && disponibles == replicas && generacion == observada))
}

comprobar_postgres() {
  local replicas listos
  read -r replicas listos < <(k -n "$NAMESPACE" get statefulset procesos-postgres -o jsonpath='{.spec.replicas} {.status.readyReplicas}' 2>/dev/null; echo) || true
  if [[ ${replicas:-0} == 1 && ${listos:-0} == 1 ]]; then
    correcto "PostgreSQL (StatefulSet procesos-postgres) listo; no forma parte de la actualizacion"
  else
    fallo "PostgreSQL no esta listo (replicas ${replicas:-?}, listas ${listos:-0}); no se actualiza la aplicacion"
  fi
}

comprobar_cluster() {
  local deployment
  comprobar_namespace
  for deployment in "${DEPLOYMENTS[@]}"; do
    leer_contenedores "$deployment"
  done
  terminar_si_hay_errores
  for deployment in "${DEPLOYMENTS[@]}"; do
    if deployment_sano "$deployment"; then
      correcto "$deployment estable: $(estado_deployment "$deployment" | awk '{print $2 "/" $1}') replicas listas"
    else
      aviso "$deployment no esta estable (replicas listas, actualizadas, disponibles y generacion: $(estado_deployment "$deployment"))"
    fi
  done
  comprobar_postgres
}

planificar() {
  local deployment contenedor nombre cambia
  seccion "cambios"
  CAMBIOS=()
  for deployment in "${DEPLOYMENTS[@]}"; do
    cambia=false
    while IFS= read -r contenedor; do
      nombre=${contenedor#*/}
      if [[ ${ACTUAL[$deployment/$nombre]} == "${SOLICITADA[$deployment/$nombre]}" ]]; then
        bitacora "  igual  $deployment/$nombre ${ACTUAL[$deployment/$nombre]}"
      else
        cambia=true
        bitacora "  cambia $deployment/$nombre" "           desplegada: ${ACTUAL[$deployment/$nombre]}" "           solicitada: ${SOLICITADA[$deployment/$nombre]}"
      fi
    done < <(contenedores_de "$deployment")
    [[ $cambia == false ]] || CAMBIOS+=("$deployment")
  done
  if ((${#CAMBIOS[@]} == 0)); then
    correcto "las imagenes desplegadas ya coinciden con las solicitadas"
  fi
}

argumentos_imagen() {
  local deployment=$1 contenedor
  while IFS= read -r contenedor; do
    printf '%s=%s\n' "${contenedor#*/}" "${SOLICITADA[$deployment/${contenedor#*/}]}"
  done < <(contenedores_de "$deployment")
}

comprobar_migraciones() {
  [[ -n $PAQUETE ]] || return 0
  [[ " ${CAMBIOS[*]} " == *" procesos-backend "* ]] || return 0
  local motivo=""
  if [[ ${META[backend_anterior]} != "${ACTUAL[procesos-backend/backend]}" ]]; then
    motivo="la imagen desplegada del backend no es la version anterior registrada en Git (${META[backend_anterior]})"
  elif [[ ${META[migraciones_nuevas]} != 0 ]]; then
    motivo="la nueva version incluye ${META[migraciones_nuevas]} migraciones de Flyway nuevas"
  fi
  if [[ -z $motivo ]]; then
    correcto "el backend cambia sin migraciones nuevas de Flyway respecto a la version desplegada"
    return 0
  fi
  bitacora "  Migraciones de Flyway incluidas en la imagen solicitada:"
  sed 's/^/           /' "$TRABAJO/paquete/$RAIZ_PAQUETE/migraciones.txt" | tee -a "$TRABAJO/bitacora.txt"
  if [[ $MIGRACIONES_REVISADAS == true ]]; then
    aviso "$motivo; confirmado con --migraciones-revisadas (compatibilidad revisada y respaldo verificado)"
  elif [[ $APLICAR == true ]]; then
    fallo "$motivo; revise la compatibilidad, haga y verifique un respaldo de PostgreSQL y repita con --migraciones-revisadas"
  else
    aviso "$motivo; --aplicar exigira --migraciones-revisadas"
  fi
}

comprobar_aprobacion() {
  [[ -n $PAQUETE && ${META[aprobacion]} != "$APROBACION_GITHUB" ]] || return 0
  if [[ $SIN_APROBACION == true ]]; then
    aviso "se acepta un paquete sin aprobacion del environment vm-universitaria (--sin-aprobacion-github)"
  elif [[ $APLICAR == true ]]; then
    fallo "el paquete no paso por el environment vm-universitaria; use --sin-aprobacion-github solo si la aprobacion se hizo por otro medio"
  else
    aviso "--aplicar exigira --sin-aprobacion-github porque el paquete no paso por el environment vm-universitaria"
  fi
}

simular_cambios() {
  local deployment
  local -a imagenes
  for deployment in "${CAMBIOS[@]}"; do
    mapfile -t imagenes < <(argumentos_imagen "$deployment")
    if k -n "$NAMESPACE" set image "deployment/$deployment" "${imagenes[@]}" --dry-run=server >"$TRABAJO/dry.txt" 2>&1; then
      correcto "validacion del servidor (dry-run) de la nueva imagen de $deployment"
    else
      fallo "el servidor rechaza la nueva imagen de $deployment: $(head -n 2 "$TRABAJO/dry.txt" | tr '\n' ' ')"
    fi
  done
}

mostrar_diferencias_configuracion() {
  [[ -n $PAQUETE ]] || return 0
  local codigo=0
  k diff -f - <"$TRABAJO/paquete/$RAIZ_PAQUETE/deployments.yaml" >"$TRABAJO/diff.txt" 2>&1 || codigo=$?
  case $codigo in
    0) correcto "los Deployments del cluster coinciden con los de Git" ;;
    1)
      aviso "diferencias entre los Deployments de Git y los del cluster; solo se aplicaran las imagenes:"
      head -n 80 "$TRABAJO/diff.txt" | sed 's/^/           /' | tee -a "$TRABAJO/bitacora.txt"
      ;;
    *) aviso "no se pudieron calcular las diferencias con kubectl diff: $(head -n 1 "$TRABAJO/diff.txt")" ;;
  esac
}

comprobar_http() {
  local ruta=$1 esperado=$2 intentos=$3 critica=$4 codigo="" intento
  for ((intento = 1; intento <= intentos; intento++)); do
    codigo=$("${CURL[@]}" -ksS --max-time 10 -o /dev/null -w '%{http_code}' "$URL$ruta" 2>/dev/null) || codigo="sin respuesta"
    if [[ $codigo == "$esperado" ]]; then
      correcto "GET $ruta responde $codigo"
      return 0
    fi
    ((intento == intentos)) || sleep "${ESPERA_HTTP:-5}"
  done
  if [[ $critica == true ]]; then
    fallo "GET $ruta responde '$codigo'; se esperaba $esperado"
  else
    aviso "GET $ruta responde '$codigo' antes del cambio; se esperaba $esperado"
  fi
}

comprobar_salud() {
  local intentos=$1 critica=$2
  if [[ $SIN_HTTP == true ]]; then
    aviso "comprobaciones HTTP omitidas con --sin-http"
    return 0
  fi
  if [[ -z $URL ]]; then
    aviso "sin --url no se comprueba la respuesta HTTP de la aplicacion"
    return 0
  fi
  comprobar_http "/" 200 "$intentos" "$critica"
  comprobar_http "/api/auth/csrf" 204 "$intentos" "$critica"
}

escribir_imagenes() {
  local origen=$1 destino=$2 deployment contenedor nombre imagen
  : >"$destino"
  for deployment in "${DEPLOYMENTS[@]}"; do
    while IFS= read -r contenedor; do
      nombre=${contenedor#*/}
      if [[ $origen == actual ]]; then
        imagen=${ACTUAL[$deployment/$nombre]}
      else
        imagen=${SOLICITADA[$deployment/$nombre]}
      fi
      printf '%s %s %s %s\n' "$deployment" "${contenedor%%/*}" "$nombre" "$imagen" >>"$destino"
    done < <(contenedores_de "$deployment")
  done
}

preparar_registro() {
  local etiqueta=revertir
  [[ -z $PAQUETE ]] || etiqueta="${META[commit]:0:12}"
  if [[ ! -d $REGISTRO_BASE ]]; then
    mkdir -p "$REGISTRO_BASE"
    chmod 700 "$REGISTRO_BASE"
  fi
  BLOQUEO="$REGISTRO_BASE/.bloqueo"
  if ! mkdir "$BLOQUEO" 2>/dev/null; then
    BLOQUEO=""
    fallo "hay otra operacion en curso ($REGISTRO_BASE/.bloqueo); si no es asi, elimine ese directorio"
    terminar_si_hay_errores
  fi
  REGISTRO="$REGISTRO_BASE/$(date -u +%Y%m%dT%H%M%SZ)-$etiqueta"
  mkdir "$REGISTRO"
  escribir_imagenes actual "$REGISTRO/imagenes-anteriores.txt"
  escribir_imagenes solicitada "$REGISTRO/imagenes-solicitadas.txt"
  if [[ -n $PAQUETE ]]; then
    cp "$TRABAJO/paquete/$RAIZ_PAQUETE/metadatos.txt" "$REGISTRO/metadatos.txt"
  fi
  correcto "imagenes anteriores registradas en $REGISTRO/imagenes-anteriores.txt"
}

fallo_tras_modificar() {
  fallo "$1"
  bitacora "" "Fallo despues de modificar el cluster. Las replicas anteriores siguen activas mientras el rollout no termine (maxUnavailable 0)." \
    "Para volver a las imagenes anteriores:" \
    "  bash desplegar-vm.sh --revertir $REGISTRO $(opcion_salud) --aplicar" \
    "Revertir las imagenes no revierte migraciones de Flyway: si la nueva version migro la base, evalue el respaldo antes de revertir."
  cerrar_registro "FALLIDO"
  exit 3
}

cerrar_registro() {
  [[ -n $REGISTRO ]] || return 0
  bitacora "" "Resultado: $1 ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  cp "$TRABAJO/bitacora.txt" "$REGISTRO/informe.txt"
  chmod 600 "$REGISTRO"/*
  printf 'Informe: %s/informe.txt\n' "$REGISTRO"
}

aplicar_cambios() {
  local deployment tiempo
  local -a imagenes
  seccion "aplicacion"
  preparar_registro
  for deployment in "${CAMBIOS[@]}"; do
    mapfile -t imagenes < <(argumentos_imagen "$deployment")
    if k -n "$NAMESPACE" set image "deployment/$deployment" "${imagenes[@]}" >"$TRABAJO/set.txt" 2>&1; then
      correcto "imagen actualizada en $deployment"
    else
      fallo_tras_modificar "no se pudo actualizar $deployment: $(head -n 2 "$TRABAJO/set.txt" | tr '\n' ' ')"
    fi
  done
  for deployment in "${CAMBIOS[@]}"; do
    tiempo=${TIEMPO_MAXIMO:-${TIEMPO_ROLLOUT[$deployment]}}
    bitacora "  ...    esperando el rollout de $deployment (maximo ${tiempo}s)"
    if k -n "$NAMESPACE" rollout status "deployment/$deployment" "--timeout=${tiempo}s" >"$TRABAJO/rollout.txt" 2>&1; then
      correcto "rollout completado en $deployment"
    else
      fallo_tras_modificar "rollout de $deployment no completado: $(tail -n 1 "$TRABAJO/rollout.txt")"
    fi
  done
}

verificar_despliegue() {
  local deployment linea nombre borrado imagenes activos esperada replicas
  seccion "verificacion"
  for deployment in "${DEPLOYMENTS[@]}"; do
    if ! deployment_sano "$deployment"; then
      fallo "$deployment no quedo estable: $(estado_deployment "$deployment")"
      continue
    fi
    replicas=$(estado_deployment "$deployment" | awk '{print $1}')
    esperada=${SOLICITADA[$deployment/${COMPONENTE[$deployment]}]}
    activos=0
    while IFS='|' read -r nombre borrado imagenes; do
      [[ -n $nombre && -z $borrado ]] || continue
      activos=$((activos + 1))
      [[ $imagenes == "$esperada," ]] || fallo "el pod $nombre ejecuta $imagenes en lugar de $esperada"
    done < <(k -n "$NAMESPACE" get pods -l "app.kubernetes.io/name=${COMPONENTE[$deployment]}" -o jsonpath="$JSONPATH_PODS" 2>/dev/null || true)
    if ((activos == replicas)); then
      correcto "$deployment: $activos pods con $esperada"
    else
      fallo "$deployment: $activos pods activos y $replicas replicas esperadas"
    fi
  done
  comprobar_postgres
  comprobar_salud 12 true
  if ((ERRORES > 0)); then
    fallo_tras_modificar "la verificacion posterior detecto $ERRORES errores"
  fi
}

principal() {
  leer_argumentos "$@"
  requerir_herramientas
  TRABAJO=$(mktemp -d)
  trap limpiar EXIT
  : >"$TRABAJO/bitacora.txt"
  bitacora "Despliegue de Procesos Empresariales en $NAMESPACE ($(date -u +%Y-%m-%dT%H:%M:%SZ))"
  if [[ $APLICAR == true ]]; then
    bitacora "Modo: aplicacion"
  else
    bitacora "Modo: comprobacion (no se modifica ningun recurso)"
  fi
  if [[ -n $PAQUETE ]]; then
    bitacora "Paquete: $PAQUETE"
  else
    bitacora "Reversion: $REVERTIR"
  fi
  if [[ -n $PAQUETE ]]; then
    validar_paquete
    leer_metadatos
    validar_deployments_yaml
  else
    leer_registro_anterior
  fi
  comprobar_cluster
  terminar_si_hay_errores
  planificar
  comprobar_migraciones
  comprobar_aprobacion
  simular_cambios
  mostrar_diferencias_configuracion
  comprobar_salud 1 false
  terminar_si_hay_errores
  if [[ $APLICAR == false ]]; then
    bitacora "" "Comprobacion correcta. No se modifico ningun recurso; para aplicar, repita con --aplicar."
    return 0
  fi
  if ((${#CAMBIOS[@]} == 0)); then
    bitacora "" "No hay imagenes que actualizar."
    return 0
  fi
  aplicar_cambios
  verificar_despliegue
  cerrar_registro "CORRECTO"
  bitacora "Para volver a las imagenes anteriores: bash desplegar-vm.sh --revertir $REGISTRO $(opcion_salud) --aplicar"
}

principal "$@"
