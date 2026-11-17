#!/usr/bin/env bash
set -euo pipefail

S=${SIMULACION:?falta SIMULACION}
printf '%s\n' "$*" >>"$S/llamadas.log"

no_encontrado() {
  printf 'Error from server (NotFound): %s "%s" not found\n' "$1" "$2" >&2
  exit 1
}

obtener_deployment() {
  local nombre=$1 consulta=$2 dir="$S/deploy/$1"
  [[ -d $dir ]] || no_encontrado deployments.apps "$nombre"
  if [[ $consulta == *"containers[*]"* ]]; then
    cat "$dir/contenedores"
  else
    cat "$dir/estado"
  fi
}

obtener_pods() {
  local etiqueta=${1#app.kubernetes.io/name=} dir replicas imagen indice
  dir="$S/deploy/procesos-$etiqueta"
  [[ -d $dir ]] || return 0
  read -r replicas _ <"$dir/estado"
  imagen=$(awk '$1 == "app" {print $3}' "$dir/contenedores")
  for ((indice = 1; indice <= replicas; indice++)); do
    printf 'procesos-%s-%d||%s,\n' "$etiqueta" "$indice" "$imagen"
  done
  if [[ -f $S/fallos/pod-antiguo-procesos-$etiqueta ]]; then
    printf 'procesos-%s-antiguo||%s,\n' "$etiqueta" "$(cat "$S/fallos/pod-antiguo-procesos-$etiqueta")"
  fi
}

obtener() {
  local tipo=$1 nombre=${2:-} consulta=""
  while (($# > 0)); do
    case $1 in
      -o) consulta=$2; shift 2 ;;
      -l) nombre=$2; shift 2 ;;
      *) shift ;;
    esac
  done
  case $tipo in
    namespace)
      [[ -f $S/namespace/$nombre ]] || no_encontrado namespaces "$nombre"
      cat "$S/namespace/$nombre"
      ;;
    deployment) obtener_deployment "$nombre" "$consulta" ;;
    statefulset)
      [[ -f $S/sts/$nombre ]] || no_encontrado statefulsets.apps "$nombre"
      cat "$S/sts/$nombre"
      ;;
    pods) obtener_pods "$nombre" ;;
    *) printf 'tipo no simulado: %s\n' "$tipo" >&2; exit 2 ;;
  esac
}

fijar_imagen() {
  local recurso=$2 nombre dir prueba=false argumento contenedor imagen replicas generacion
  nombre=${recurso#deployment/}
  dir="$S/deploy/$nombre"
  [[ -d $dir ]] || no_encontrado deployments.apps "$nombre"
  shift 2
  local -a asignaciones=()
  for argumento in "$@"; do
    if [[ $argumento == --dry-run=server ]]; then
      prueba=true
    else
      asignaciones+=("$argumento")
    fi
  done
  if [[ -f $S/fallos/set-$nombre ]]; then
    printf 'error: admission webhook denied the request\n' >&2
    exit 1
  fi
  for argumento in "${asignaciones[@]}"; do
    contenedor=${argumento%%=*}
    if ! awk -v c="$contenedor" '$2 == c {e = 1} END {exit !e}' "$dir/contenedores"; then
      printf 'error: unable to find container named "%s"\n' "$contenedor" >&2
      exit 1
    fi
  done
  if [[ $prueba == true ]]; then
    printf 'deployment.apps/%s image updated (server dry run)\n' "$nombre"
    return 0
  fi
  for argumento in "${asignaciones[@]}"; do
    contenedor=${argumento%%=*}
    imagen=${argumento#*=}
    awk -v c="$contenedor" -v i="$imagen" '$2 == c {$3 = i} {print}' "$dir/contenedores" >"$dir/contenedores.nuevo"
    mv "$dir/contenedores.nuevo" "$dir/contenedores"
  done
  read -r replicas _ _ _ generacion _ <"$dir/estado"
  generacion=$((generacion + 1))
  if [[ -f $S/fallos/rollout-$nombre ]]; then
    printf '%s %s %s %s %s %s\n' "$replicas" "$((replicas - 1))" 1 "$((replicas - 1))" "$generacion" "$generacion" >"$dir/estado"
  else
    printf '%s %s %s %s %s %s\n' "$replicas" "$replicas" "$replicas" "$replicas" "$generacion" "$generacion" >"$dir/estado"
  fi
  printf 'deployment.apps/%s image updated\n' "$nombre"
}

estado_rollout() {
  local nombre=${2#deployment/}
  [[ -d $S/deploy/$nombre ]] || no_encontrado deployments.apps "$nombre"
  if [[ -f $S/fallos/rollout-$nombre ]]; then
    printf 'error: timed out waiting for the condition\n' >&2
    exit 1
  fi
  printf 'deployment "%s" successfully rolled out\n' "$nombre"
}

diferencias() {
  cat >/dev/null
  if [[ -f $S/diff ]]; then
    cat "$S/diff"
    exit 1
  fi
}

if [[ ${1:-} == -n ]]; then
  shift 2
fi
verbo=${1:-}
shift || true
case $verbo in
  get) obtener "$@" ;;
  set) fijar_imagen "$@" ;;
  rollout) estado_rollout "$@" ;;
  diff) diferencias ;;
  *) printf 'verbo no simulado: %s\n' "$verbo" >&2; exit 2 ;;
esac
