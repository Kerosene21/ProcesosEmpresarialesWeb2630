#!/usr/bin/env bash
set -euo pipefail

D=${IMAGENES_SIMULADAS:?falta IMAGENES_SIMULADAS}
if [[ ${1:-} != buildx || ${2:-} != imagetools || ${3:-} != inspect || ${5:-} != --format ]]; then
  printf 'invocacion no simulada: %s\n' "$*" >&2
  exit 2
fi
referencia=$4
campo=${6#\{\{json .}
campo=${campo%\}\}}
clave=${referencia//[\/:@]/_}
if [[ ! -f $D/$clave.$campo.json ]]; then
  printf 'ERROR: %s: not found\n' "$referencia" >&2
  exit 1
fi
cat "$D/$clave.$campo.json"
