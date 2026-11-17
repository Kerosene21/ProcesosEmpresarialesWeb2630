#!/usr/bin/env bash
set -euo pipefail

D=${GH_SIMULADO:?falta GH_SIMULADO}
printf '%s\n' "$*" >>"$D/llamadas.log"
if [[ ${1:-} != api || $# -ne 2 ]]; then
  printf 'invocacion no simulada: %s\n' "$*" >&2
  exit 2
fi
if [[ -f $D/error ]]; then
  printf 'gh: Internal Server Error (HTTP 500)\n' >&2
  exit 1
fi
case $2 in
  */deployment-branch-policies) archivo="$D/ramas.json" ;;
  */environments/*) archivo="$D/entorno.json" ;;
  *) archivo="" ;;
esac
if [[ -z $archivo || ! -f $archivo ]]; then
  printf '{"message":"Not Found","status":"404"}gh: Not Found (HTTP 404)\n' >&2
  exit 1
fi
cat "$archivo"
