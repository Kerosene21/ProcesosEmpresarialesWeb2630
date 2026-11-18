#!/usr/bin/env bash
set -uo pipefail

DIR_PRUEBAS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$DIR_PRUEBAS/../verificar-entorno.sh"
TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT
export GH="bash $DIR_PRUEBAS/gh-simulado.sh"
export GITHUB_REPOSITORY="Kerosene21/ProcesosEmpresarialesWeb2630"
readonly APROBACION_FALSA='aprobad[oa]|aprobo|con la aprobacion|tras la aprobacion|despues de la aprobacion|aprobacion del environment|self-review'
TOTAL=0
CORRECTOS=0
FALLIDOS=()
FILAS=()

entorno_json() {
  local revisores=${1:-0} autorevision=${2:-true} omision=${3:-false} politica=${4:-'{"protected_branches":false,"custom_branch_policies":true}'}
  local reglas='{"id":2,"node_id":"GA_2","type":"branch_policy"}'
  if ((revisores > 0)); then
    reglas="{\"id\":1,\"node_id\":\"GA_1\",\"type\":\"required_reviewers\",\"prevent_self_review\":$autorevision,\"reviewers\":[{\"type\":\"User\",\"reviewer\":{\"login\":\"revisor\"}}]},$reglas"
  fi
  printf '{"id":1,"name":"vm-universitaria","can_admins_bypass":%s,"protection_rules":[%s],"deployment_branch_policy":%s}' \
    "$omision" "$reglas" "$politica"
}

ramas_json() {
  local elementos="" regla
  for regla in "$@"; do
    elementos+="${elementos:+,}{\"id\":1,\"name\":\"${regla#*:}\",\"type\":\"${regla%%:*}\"}"
  done
  printf '{"total_count":%d,"branch_policies":[%s]}' "$#" "$elementos"
}

preparar() {
  GH_SIMULADO="$TEMPORAL/gh-$TOTAL"
  export GH_SIMULADO
  rm -rf "$GH_SIMULADO"
  mkdir -p "$GH_SIMULADO"
  : >"$GH_SIMULADO/llamadas.log"
  entorno_json >"$GH_SIMULADO/entorno.json"
  ramas_json branch:main >"$GH_SIMULADO/ramas.json"
  export ENTORNO_DESPLIEGUE=vm-universitaria
  GITHUB_OUTPUT="$TEMPORAL/output-$TOTAL"
  export GITHUB_OUTPUT
  : >"$GITHUB_OUTPUT"
}

caso() {
  local nombre=$1 codigo_esperado=$2 patron=$3 revisores=${4:-0} prohibido=${5:-} salida codigo resultado=SI
  salida="$TEMPORAL/salida-$TOTAL.txt"
  bash "$SCRIPT" >"$salida" 2>&1
  codigo=$?
  if [[ $codigo != "$codigo_esperado" ]] || ! grep -qF -- "$patron" "$salida"; then
    resultado=NO
  fi
  if grep -qi '^aprobacion' "$GITHUB_OUTPUT"; then
    resultado=NO
  fi
  if [[ $codigo_esperado != 0 && -s $GITHUB_OUTPUT ]]; then
    resultado=NO
  fi
  if [[ $codigo_esperado == 0 ]]; then
    grep -qx 'entorno=vm-universitaria' "$GITHUB_OUTPUT" || resultado=NO
    grep -qx 'verificacion=environment:vm-universitaria' "$GITHUB_OUTPUT" || resultado=NO
    grep -qx "revisores=$revisores" "$GITHUB_OUTPUT" || resultado=NO
  fi
  if [[ -n $prohibido ]] && grep -qiE -- "$prohibido" "$salida" "$GITHUB_OUTPUT"; then
    resultado=NO
  fi
  TOTAL=$((TOTAL + 1))
  if [[ $resultado == SI ]]; then
    CORRECTOS=$((CORRECTOS + 1))
  else
    FALLIDOS+=("$nombre")
    printf -- '--- salida de %s ---\n' "$nombre"
    cat "$salida" "$GITHUB_OUTPUT"
  fi
  FILAS+=("| $TOTAL | $nombre | $codigo | $resultado |")
  printf '%-3s %-3s codigo=%-2s %s\n' "$TOTAL" "$resultado" "$codigo" "$nombre"
}

printf 'Pruebas de verificar-entorno.sh con la API de GitHub simulada\n\n'

preparar
unset ENTORNO_DESPLIEGUE
caso "Variable ENTORNO_DESPLIEGUE inexistente" 1 "ENTORNO_DESPLIEGUE no existe o esta vacia"

preparar
export ENTORNO_DESPLIEGUE=produccion
caso "Valor no admitido en ENTORNO_DESPLIEGUE" 1 "el unico valor admitido es vm-universitaria"

preparar
rm "$GH_SIMULADO/entorno.json"
caso "Environment inexistente" 1 "El environment vm-universitaria no existe"

preparar
touch "$GH_SIMULADO/error"
caso "Error de la API de GitHub" 1 "No se pudo leer el environment vm-universitaria"

preparar
printf 'no es json' >"$GH_SIMULADO/entorno.json"
caso "Respuesta no valida de la API" 1 "respuesta no valida para el environment"

preparar
entorno_json | jq -c '.name = "produccion"' >"$GH_SIMULADO/entorno.json"
caso "Respuesta de la API para otro environment" 1 "respuesta no valida para el environment"

preparar
rm "$GH_SIMULADO/ramas.json"
caso "Error al leer las ramas permitidas" 1 "No se pudieron leer las ramas permitidas"

preparar
printf '{"total_count":1}' >"$GH_SIMULADO/ramas.json"
caso "Respuesta no valida de las ramas permitidas" 1 "respuesta no valida para las ramas permitidas"

preparar
entorno_json 0 true true >"$GH_SIMULADO/entorno.json"
caso "Environment que permite omitir las protecciones" 1 "permite que los administradores omitan las protecciones"

preparar
entorno_json 0 true false | jq -c 'del(.can_admins_bypass)' >"$GH_SIMULADO/entorno.json"
caso "Environment sin el dato de omision administrativa" 1 "permite que los administradores omitan las protecciones"

preparar
entorno_json 0 true false null >"$GH_SIMULADO/entorno.json"
caso "Environment que admite cualquier rama" 1 "admite despliegues desde cualquier rama"

preparar
entorno_json 0 true false '{"protected_branches":true,"custom_branch_policies":false}' >"$GH_SIMULADO/entorno.json"
caso "Environment limitado a ramas protegidas" 1 "admite cualquier rama protegida"

preparar
ramas_json branch:develop >"$GH_SIMULADO/ramas.json"
caso "Environment limitado a una rama distinta de main" 1 "admite despliegues desde 'branch:develop'"

preparar
ramas_json branch:main branch:develop >"$GH_SIMULADO/ramas.json"
caso "Environment que admite main y otra rama" 1 "admite despliegues desde 'branch:develop,branch:main'"

preparar
ramas_json 'branch:*' >"$GH_SIMULADO/ramas.json"
caso "Environment con un comodin de ramas" 1 "solo debe permitir la rama main"

preparar
ramas_json tag:main >"$GH_SIMULADO/ramas.json"
caso "Environment que admite una etiqueta en lugar de la rama" 1 "admite despliegues desde 'tag:main'"

preparar
ramas_json >"$GH_SIMULADO/ramas.json"
caso "Environment con politica personalizada vacia" 1 "ninguna regla"

preparar
entorno_json 0 true true null >"$GH_SIMULADO/entorno.json"
caso "Varias protecciones ausentes a la vez" 1 "admite despliegues desde cualquier rama"

preparar
caso "Environment sin revisores: configuracion definitiva valida" 0 "Environment vm-universitaria verificado: solo la rama main y sin omision de administradores"

preparar
caso "Sin revisores no se anuncia ni se registra una aprobacion humana" 0 "GitHub no solicita ni registra ninguna aprobacion humana" 0 "$APROBACION_FALSA"

preparar
entorno_json 1 true >"$GH_SIMULADO/entorno.json"
caso "Environment con revisores opcionales tambien valido" 0 "El environment exige 1 revisores" 1

preparar
entorno_json 1 false >"$GH_SIMULADO/entorno.json"
caso "Revisores sin Prevent self-review: aviso" 0 "Prevent self-review desactivado" 1

printf '\n%d de %d pruebas correctas\n' "$CORRECTOS" "$TOTAL"
if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  {
    printf '### Pruebas de verificar-entorno.sh: %d de %d correctas\n\n' "$CORRECTOS" "$TOTAL"
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
