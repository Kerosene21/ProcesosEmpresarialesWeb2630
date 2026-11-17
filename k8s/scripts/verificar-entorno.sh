#!/usr/bin/env bash
set -euo pipefail

readonly ENTORNO_ESPERADO="vm-universitaria"
read -r -a GH <<<"${GH:-gh}"
ENTORNO=${ENTORNO_DESPLIEGUE:-}
REPOSITORIO=${GITHUB_REPOSITORY:?falta GITHUB_REPOSITORY}
ERRORES=0
ERR=$(mktemp)
trap 'rm -f "$ERR"' EXIT

error() {
  printf '::error title=Environment de despliegue::%s\n' "$1"
  ERRORES=$((ERRORES + 1))
}

aviso() {
  printf '::warning title=Environment de despliegue::%s\n' "$1"
}

if [[ -z $ENTORNO ]]; then
  error "La variable de repositorio ENTORNO_DESPLIEGUE no existe o esta vacia. Configure el environment $ENTORNO_ESPERADO y despues la variable ENTORNO_DESPLIEGUE=$ENTORNO_ESPERADO."
  exit 1
fi
if [[ $ENTORNO != "$ENTORNO_ESPERADO" ]]; then
  error "ENTORNO_DESPLIEGUE vale '$ENTORNO'; el unico valor admitido es $ENTORNO_ESPERADO."
  exit 1
fi
if ! datos=$("${GH[@]}" api "repos/$REPOSITORIO/environments/$ENTORNO" 2>"$ERR"); then
  if grep -q 'HTTP 404' "$ERR"; then
    error "El environment $ENTORNO no existe. Usarlo lo crearia sin reglas de proteccion; creelo antes en Settings > Environments."
  else
    error "No se pudo leer el environment $ENTORNO: $(head -n 1 "$ERR")"
  fi
  exit 1
fi
if ! jq -e 'type == "object" and .name != null' >/dev/null 2>&1 <<<"$datos"; then
  error "La API de GitHub devolvio una respuesta no valida para el environment $ENTORNO."
  exit 1
fi
revisores=$(jq '[.protection_rules[]? | select(.type == "required_reviewers") | .reviewers[]?] | length' <<<"$datos")
autorevision=$(jq -r '[.protection_rules[]? | select(.type == "required_reviewers") | .prevent_self_review][0] // false' <<<"$datos")
omision=$(jq -r 'if has("can_admins_bypass") then .can_admins_bypass else true end' <<<"$datos")
politica=$(jq -r 'if .deployment_branch_policy == null then "ninguna" elif .deployment_branch_policy.custom_branch_policies == true then "personalizada" else "protegidas" end' <<<"$datos")
if ((revisores < 1)); then
  error "$ENTORNO no exige revisores: active Required reviewers con al menos una persona."
fi
if [[ $omision != false ]]; then
  error "$ENTORNO permite que los administradores omitan las protecciones: desactive Allow administrators to bypass configured protection rules."
fi
case $politica in
  ninguna) error "$ENTORNO admite despliegues desde cualquier rama: limitelo a main con Selected branches and tags." ;;
  protegidas) error "$ENTORNO admite cualquier rama protegida: use Selected branches and tags con una unica regla para main." ;;
  personalizada)
    if ! ramas=$("${GH[@]}" api "repos/$REPOSITORIO/environments/$ENTORNO/deployment-branch-policies" 2>"$ERR"); then
      error "No se pudieron leer las ramas permitidas de $ENTORNO: $(head -n 1 "$ERR")"
    else
      ramas=$(jq -r '[.branch_policies[]? | "\(.type // "branch"):\(.name)"] | sort | join(",")' <<<"$ramas")
      if [[ $ramas != "branch:main" ]]; then
        error "$ENTORNO admite despliegues desde '${ramas:-ninguna regla}'; solo debe permitir la rama main."
      fi
    fi
    ;;
esac
if ((ERRORES > 0)); then
  exit 1
fi
if [[ $autorevision != true ]]; then
  aviso "$ENTORNO permite que quien lanza la preparacion la apruebe (Prevent self-review desactivado)."
fi
if [[ -n ${GITHUB_OUTPUT:-} ]]; then
  printf 'entorno=%s\naprobacion=environment:%s\n' "$ENTORNO" "$ENTORNO" >>"$GITHUB_OUTPUT"
fi
printf 'Environment %s verificado: %d revisores, solo la rama main y sin omision de administradores.\n' "$ENTORNO" "$revisores"
