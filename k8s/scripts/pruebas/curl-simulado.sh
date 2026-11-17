#!/usr/bin/env bash
set -euo pipefail

S=${SIMULACION:?falta SIMULACION}
url=${!#}
printf 'curl %s\n' "$url" >>"$S/llamadas.log"
resto=${url#*://}
ruta=/${resto#*/}
[[ $resto == */* ]] || ruta=/
clave=${ruta//\//_}
if [[ -f $S/http-despues/$clave ]] && grep 'set image' "$S/llamadas.log" | grep -vq -- '--dry-run=server'; then
  codigo=$(cat "$S/http-despues/$clave")
elif [[ -f $S/http/$clave ]]; then
  codigo=$(cat "$S/http/$clave")
elif [[ $ruta == /api/auth/csrf ]]; then
  codigo=204
else
  codigo=200
fi
printf '%s' "$codigo"
[[ $codigo != 000 ]] || exit 7
