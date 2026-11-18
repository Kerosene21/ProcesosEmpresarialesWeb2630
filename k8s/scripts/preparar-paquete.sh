#!/usr/bin/env bash
set -euo pipefail
umask 022

readonly REPOSITORIO="Kerosene21/ProcesosEmpresarialesWeb2630"
readonly URL_REPOSITORIO="https://github.com/$REPOSITORIO"
readonly NAMESPACE="procesos-empresariales"
readonly KUSTOMIZATION="k8s/overlays/vm/kustomization.yaml"
readonly MIGRACIONES="src/main/resources/db/migration"
readonly RAIZ_PAQUETE="paquete-despliegue"
readonly -a COMPONENTES=(backend frontend)
readonly REGEX_DIGEST='^sha256:[0-9a-f]{64}$'
readonly REGEX_FLUJO='^Kerosene21/ProcesosEmpresarialesWeb2630/\.github/workflows/imagenes\.yml@refs/(heads/main|tags/v[0-9]+\.[0-9]+\.[0-9]+)$'
declare -rA REPO_IMAGEN=([backend]=ghcr.io/kerosene21/procesos-empresariales-backend [frontend]=ghcr.io/kerosene21/procesos-empresariales-frontend)
declare -rA NOMBRE_KUSTOMIZE=([backend]=procesos-empresariales-backend [frontend]=procesos-empresariales-frontend)
declare -rA CONTEXTO=([backend]="pom.xml mvnw .mvn src Dockerfile .dockerignore" [frontend]="frontend")
read -r -a DOCKER <<<"${DOCKER:-docker}"

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMMIT=""
VERIFICACION=""
SALIDA=""
TRABAJO=""
ERRORES=0
declare -A IMAGEN=()
declare -A REVISION=()
declare -A PLATAFORMAS=()
declare -A EJECUCION=()
declare -A DERIVA=()
declare -A RECIENTE=()
BACKEND_ANTERIOR="ninguna"
REVISION_ANTERIOR=""
MIGRACIONES_NUEVAS="desconocido"
declare -A ESTADO_MIGRACION=()

uso() {
  printf 'Uso: preparar-paquete.sh --commit SHA --verificacion-entorno (ninguna|environment:vm-universitaria) --salida DIRECTORIO\n'
  printf 'La opcion anterior --aprobacion se admite como sinonimo de --verificacion-entorno.\n'
}

info() { printf '  %s\n' "$*"; }
correcto() { printf '  OK     %s\n' "$*"; }
aviso() { printf '  AVISO  %s\n' "$*"; }
fallo() {
  printf '  ERROR  %s\n' "$*"
  ERRORES=$((ERRORES + 1))
}

terminar_si_hay_errores() {
  if ((ERRORES > 0)); then
    printf '\nPreparacion rechazada: %d errores. No se genero ningun paquete.\n' "$ERRORES" >&2
    exit 1
  fi
}

leer_argumentos() {
  while (($# > 0)); do
    case $1 in
      --commit) COMMIT=${2:-}; shift 2 ;;
      --verificacion-entorno | --aprobacion) VERIFICACION=${2:-}; shift 2 ;;
      --salida) SALIDA=${2:-}; shift 2 ;;
      -h | --ayuda) uso; exit 0 ;;
      *) uso >&2; exit 2 ;;
    esac
  done
  if [[ ! $COMMIT =~ ^[0-9a-f]{40}$ || ! $VERIFICACION =~ ^(ninguna|environment:vm-universitaria)$ || -z $SALIDA ]]; then
    uso >&2
    exit 2
  fi
}

requerir_herramientas() {
  local herramienta faltan=()
  for herramienta in git yq jq kubectl sha256sum tar gzip "${DOCKER[0]}"; do
    command -v "$herramienta" >/dev/null 2>&1 || faltan+=("$herramienta")
  done
  if ((${#faltan[@]} > 0)); then
    printf 'Faltan herramientas: %s\n' "${faltan[*]}" >&2
    exit 2
  fi
  if ! yq --version 2>&1 | grep -q mikefarah; then
    printf 'Se necesita yq de mikefarah\n' >&2
    exit 2
  fi
}

g() {
  git -C "$RAIZ" "$@"
}

comprobar_commit() {
  printf '\n== commit ==\n'
  if [[ $(g rev-parse HEAD) != "$COMMIT" ]]; then
    fallo "el directorio de trabajo esta en $(g rev-parse HEAD), no en el commit solicitado $COMMIT"
  elif [[ -n $(g status --porcelain --untracked-files=no) ]]; then
    fallo "el directorio de trabajo tiene cambios sin confirmar; el paquete debe reflejar exactamente el commit"
  else
    correcto "codigo en el commit $COMMIT sin cambios locales"
  fi
  terminar_si_hay_errores
}

imagen_en_revision() {
  local revision=$1 componente=$2 contenido
  contenido=$(g show "$revision:$KUSTOMIZATION" 2>/dev/null) || return 1
  yq -o=json '.images // []' <<<"$contenido" | jq -r --arg nombre "${NOMBRE_KUSTOMIZE[$componente]}" --arg repo "${REPO_IMAGEN[$componente]}" '
    [.[] | select(.name == $nombre)] as $e
    | if ($e | length) != 1 then "error:debe haber exactamente una entrada images para \($nombre)"
      elif ($e[0] | has("newTag")) then "sin-digest"
      elif $e[0].newName != $repo then "error:newName de \($nombre) debe ser \($repo)"
      elif (($e[0].digest // "") | test("^sha256:[0-9a-f]{64}$") | not) then "error:digest no valido para \($nombre)"
      else "\($repo)@\($e[0].digest)" end'
}

leer_overlay() {
  local componente valor
  printf '\n== version deseada en Git (%s) ==\n' "$KUSTOMIZATION"
  for componente in "${COMPONENTES[@]}"; do
    valor=$(imagen_en_revision "$COMMIT" "$componente")
    case $valor in
      error:*) fallo "${valor#error:}" ;;
      sin-digest) fallo "${NOMBRE_KUSTOMIZE[$componente]} usa newTag; el overlay vm debe fijar las imagenes por digest" ;;
      *)
        IMAGEN[$componente]=$valor
        correcto "$componente: $valor"
        ;;
    esac
  done
  terminar_si_hay_errores
}

inspeccionar() {
  "${DOCKER[@]}" buildx imagetools inspect "$1" --format "{{json .$2}}"
}

por_plataforma() {
  jq -c 'if type != "object" then {} elif has("architecture") then {(.os + "/" + .architecture): .} elif has("SLSA") then {"linux/amd64": .} else . end'
}

comprobar_imagen() {
  local componente=$1 referencia=${IMAGEN[$1]} manifiesto configuracion procedencia etiquetas parametros revision
  local -a rutas
  printf '\n== version publicada en GHCR: %s ==\n' "$componente"
  if ! manifiesto=$(inspeccionar "$referencia" Manifest 2>"$TRABAJO/inspect.err"); then
    fallo "$referencia no existe en GHCR o no es accesible: $(head -n 1 "$TRABAJO/inspect.err")"
    return
  fi
  if [[ $(jq -r '.digest // ""' <<<"$manifiesto") != "${referencia#*@}" ]]; then
    fallo "GHCR devolvio un digest distinto del solicitado para $referencia"
    return
  fi
  PLATAFORMAS[$componente]=$(jq -r '[.manifests[]? | select(.platform.os != "unknown") | "\(.platform.os)/\(.platform.architecture)"] | join(", ")' <<<"$manifiesto")
  configuracion=$(inspeccionar "$referencia" Image | por_plataforma)
  if [[ $(jq -r 'has("linux/amd64")' <<<"$configuracion") != true ]]; then
    fallo "$referencia no tiene imagen linux/amd64 (plataformas: ${PLATAFORMAS[$componente]:-ninguna})"
    return
  fi
  correcto "existe en GHCR con linux/amd64 (plataformas: ${PLATAFORMAS[$componente]})"
  etiquetas=$(jq -c '.["linux/amd64"].config.Labels // {}' <<<"$configuracion")
  revision=$(jq -r '.["org.opencontainers.image.revision"] // ""' <<<"$etiquetas")
  if [[ ! $revision =~ ^[0-9a-f]{40}$ ]]; then
    fallo "$referencia no tiene la etiqueta org.opencontainers.image.revision"
    return
  fi
  REVISION[$componente]=$revision
  if [[ $(jq -r '.["org.opencontainers.image.source"] // ""' <<<"$etiquetas") != "$URL_REPOSITORIO" ]]; then
    fallo "org.opencontainers.image.source de $referencia no es $URL_REPOSITORIO"
  fi
  if [[ $(jq -r '.["org.opencontainers.image.title"] // ""' <<<"$etiquetas") != "procesos-empresariales-$componente" ]]; then
    fallo "org.opencontainers.image.title de $referencia no corresponde a $componente"
  fi
  procedencia=$(inspeccionar "$referencia" Provenance 2>/dev/null | por_plataforma) || procedencia=""
  [[ -n $procedencia ]] || procedencia='{}'
  parametros=$(jq -c '.["linux/amd64"].SLSA.buildDefinition.internalParameters // null' <<<"$procedencia")
  if [[ $parametros == null ]]; then
    fallo "$referencia no publica procedencia SLSA; no se puede confirmar que la construyo imagenes.yml"
    return
  fi
  if [[ $(jq -r '.github_repository // ""' <<<"$parametros") != "$REPOSITORIO" ]]; then
    fallo "la procedencia de $referencia no corresponde al repositorio $REPOSITORIO"
  fi
  if [[ ! $(jq -r '.github_workflow_ref // ""' <<<"$parametros") =~ $REGEX_FLUJO ]]; then
    fallo "la procedencia de $referencia no proviene de imagenes.yml en main o en una etiqueta de version"
  fi
  if [[ $(jq -r '.github_workflow_sha // ""' <<<"$parametros") != "$revision" ]]; then
    fallo "la procedencia de $referencia no coincide con su revision $revision"
  fi
  if [[ $(jq -r '.github_runner_environment // ""' <<<"$parametros") != github-hosted ]]; then
    fallo "la procedencia de $referencia no indica un runner alojado por GitHub"
  fi
  EJECUCION[$componente]=$(jq -r '.github_run_id // ""' <<<"$parametros")
  [[ ${EJECUCION[$componente]} =~ ^[0-9]+$ ]] || fallo "la procedencia de $referencia no identifica la ejecucion de GitHub Actions"
  if ! g cat-file -e "$revision^{commit}" 2>/dev/null || ! g merge-base --is-ancestor "$revision" "$COMMIT"; then
    fallo "la revision $revision de $referencia no pertenece a la historia del commit $COMMIT"
    return
  fi
  correcto "revision $revision construida por imagenes.yml (ejecucion ${EJECUCION[$componente]}) e incluida en la historia del commit"
  read -r -a rutas <<<"${CONTEXTO[$componente]}"
  DERIVA[$componente]=$(g diff --name-only "$revision" "$COMMIT" -- "${rutas[@]}" | grep -c '' || true)
  if [[ ${DERIVA[$componente]} == 0 ]]; then
    correcto "el codigo de $componente no cambio entre la revision de la imagen y el commit"
  else
    aviso "${DERIVA[$componente]} archivos de $componente cambiaron despues de la revision de la imagen; se mantiene el digest fijado en Git"
  fi
  comprobar_reciente "$componente"
}

comprobar_reciente() {
  local componente=$1 etiqueta digest
  etiqueta="${REPO_IMAGEN[$componente]}:sha-${COMMIT:0:7}"
  if digest=$(inspeccionar "$etiqueta" Manifest 2>/dev/null | jq -r '.digest // ""') && [[ $digest =~ $REGEX_DIGEST ]]; then
    if [[ "${REPO_IMAGEN[$componente]}@$digest" == "${IMAGEN[$componente]}" ]]; then
      RECIENTE[$componente]="coincide con $etiqueta"
    else
      RECIENTE[$componente]="$etiqueta apunta a $digest; no se usa porque Git fija otro digest"
      aviso "existe $etiqueta ($digest); el paquete conserva el digest de Git"
    fi
  else
    RECIENTE[$componente]="no hay imagen publicada con la etiqueta $etiqueta"
  fi
}

buscar_version_anterior() {
  local revision valor
  while IFS= read -r revision; do
    valor=$(imagen_en_revision "$revision" backend || printf 'sin-digest')
    [[ $valor == "${IMAGEN[backend]}" ]] && continue
    if [[ $valor == "${REPO_IMAGEN[backend]}"@sha256:* ]]; then
      BACKEND_ANTERIOR=$valor
    fi
    return 0
  done < <(g log --format=%H "$COMMIT" -- "$KUSTOMIZATION")
}

comprobar_migraciones() {
  local estado archivo actuales revision_actual=${REVISION[backend]}
  printf '\n== migraciones de Flyway ==\n'
  buscar_version_anterior
  if [[ $BACKEND_ANTERIOR == ninguna ]]; then
    aviso "Git no registra una version anterior del backend con digest; las migraciones se marcan sin comparar"
  elif REVISION_ANTERIOR=$(inspeccionar "$BACKEND_ANTERIOR" Image 2>/dev/null | por_plataforma | jq -r '.["linux/amd64"].config.Labels["org.opencontainers.image.revision"] // ""') &&
    [[ $REVISION_ANTERIOR =~ ^[0-9a-f]{40}$ ]] && g cat-file -e "$REVISION_ANTERIOR^{commit}" 2>/dev/null; then
    MIGRACIONES_NUEVAS=0
    while IFS=$'\t' read -r estado archivo; do
      [[ -n $estado ]] || continue
      case $estado in
        A)
          ESTADO_MIGRACION[$archivo]=nueva
          MIGRACIONES_NUEVAS=$((MIGRACIONES_NUEVAS + 1))
          ;;
        D)
          aviso "$archivo existia en la version anterior y no en la nueva: la base puede estar por delante de la imagen"
          MIGRACIONES_NUEVAS=desconocido
          ;;
        *) fallo "la migracion aplicada $archivo cambio entre ${REVISION_ANTERIOR:0:12} y ${revision_actual:0:12} ($estado); Flyway rechazaria el checksum" ;;
      esac
    done < <(g diff --no-renames --name-status "$REVISION_ANTERIOR" "$revision_actual" -- "$MIGRACIONES")
    correcto "version anterior en Git: $BACKEND_ANTERIOR (revision ${REVISION_ANTERIOR:0:12}); migraciones nuevas: $MIGRACIONES_NUEVAS"
  else
    aviso "no se pudo leer la revision de la version anterior $BACKEND_ANTERIOR; las migraciones se marcan sin comparar"
  fi
  terminar_si_hay_errores
  actuales=$(g ls-tree -r --name-only "$revision_actual" -- "$MIGRACIONES")
  : >"$TRABAJO/$RAIZ_PAQUETE/migraciones.txt"
  while IFS= read -r archivo; do
    [[ -n $archivo ]] || continue
    if [[ $MIGRACIONES_NUEVAS == desconocido ]]; then
      estado=sin-comparar
    else
      estado=${ESTADO_MIGRACION[$archivo]:-existente}
    fi
    printf '%s  %s  %s\n' "$(g show "$revision_actual:$archivo" | sha256sum | cut -d' ' -f1)" "${archivo##*/}" "$estado" >>"$TRABAJO/$RAIZ_PAQUETE/migraciones.txt"
  done <<<"$actuales"
  info "$(grep -c '' "$TRABAJO/$RAIZ_PAQUETE/migraciones.txt") migraciones en la revision ${revision_actual:0:12} del backend"
}

renderizar_deployments() {
  local destino="$TRABAJO/$RAIZ_PAQUETE/deployments.yaml" resultado
  printf '\n== Deployments autorizados ==\n'
  kubectl kustomize "$RAIZ/k8s/overlays/vm" |
    yq 'select(.kind == "Deployment" and (.metadata.name == "procesos-backend" or .metadata.name == "procesos-frontend"))' >"$destino"
  resultado=$(yq -o=json -I=0 '.' "$destino" | jq -rs --arg ns "$NAMESPACE" --arg backend "${IMAGEN[backend]}" --arg frontend "${IMAGEN[frontend]}" '
    (map(.metadata.name) | sort) as $nombres
    | ([.[] | select(.metadata.name == "procesos-backend") | .spec.template.spec | ((.initContainers // []) + .containers)[] | .image] | unique) as $b
    | ([.[] | select(.metadata.name == "procesos-frontend") | .spec.template.spec | ((.initContainers // []) + .containers)[] | .image] | unique) as $f
    | if $nombres != ["procesos-backend", "procesos-frontend"] then "nombres inesperados: \($nombres | join(", "))"
      elif any(.[]; .metadata.namespace != $ns) then "namespace distinto de \($ns)"
      elif $b != [$backend] or $f != [$frontend] then "las imagenes renderizadas no coinciden con el overlay"
      else "ok" end')
  if [[ $resultado == ok ]]; then
    correcto "procesos-backend y procesos-frontend renderizados desde k8s/overlays/vm; sin PostgreSQL, PVC, PV, StorageClass, Secrets ni recursos de cluster"
  else
    fallo "deployments.yaml: $resultado"
  fi
  terminar_si_hay_errores
}

escribir_metadatos() {
  local fecha
  fecha=$(TZ=UTC g show -s --format=%cd --date=format-local:%Y-%m-%dT%H:%M:%SZ "$COMMIT")
  cat >"$TRABAJO/$RAIZ_PAQUETE/metadatos.txt" <<TEXTO
formato=1
repositorio=$REPOSITORIO
commit=$COMMIT
commit_fecha=$fecha
namespace=$NAMESPACE
backend_imagen=${IMAGEN[backend]}
backend_revision=${REVISION[backend]}
frontend_imagen=${IMAGEN[frontend]}
frontend_revision=${REVISION[frontend]}
backend_anterior=$BACKEND_ANTERIOR
migraciones_nuevas=$MIGRACIONES_NUEVAS
verificacion_entorno=$VERIFICACION
TEXTO
  COMMIT_FECHA=$fecha
}

escribir_resumen() {
  local componente
  {
    printf '## Paquete de despliegue %s\n\n' "${COMMIT:0:12}"
    printf '| Version | Origen | Valor |\n| --- | --- | --- |\n'
    printf "| Deseada en Git | \`%s\` en \`%s\` | commit \`%s\` (%s) |\n" "$KUSTOMIZATION" main "$COMMIT" "$COMMIT_FECHA"
    for componente in "${COMPONENTES[@]}"; do
      printf "| Publicada en GHCR (%s) | \`%s\` | revision \`%s\`, %s, ejecucion %s de imagenes.yml |\n" \
        "$componente" "${IMAGEN[$componente]}" "${REVISION[$componente]}" "${PLATAFORMAS[$componente]}" "${EJECUCION[$componente]}"
    done
    printf "| Desplegada en Kubernetes | MicroK8s de la VM | no se consulta desde GitHub; \`desplegar-vm.sh\` la compara en modo comprobacion |\n\n"
    printf '### Coherencia entre Git y GHCR\n\n'
    for componente in "${COMPONENTES[@]}"; do
      printf -- '- %s: %s archivos del contexto de la imagen cambiaron desde su revision; %s.\n' "$componente" "${DERIVA[$componente]}" "${RECIENTE[$componente]}"
    done
    printf '\n### Migraciones de Flyway\n\n'
    printf -- "- Version anterior del backend registrada en Git: \`%s\`.\n" "$BACKEND_ANTERIOR"
    printf -- '- Migraciones nuevas respecto a esa version: %s.\n' "$MIGRACIONES_NUEVAS"
    if [[ $MIGRACIONES_NUEVAS != 0 ]]; then
      printf -- "- Antes de aplicar: revisar la compatibilidad (expandir y contraer), hacer un respaldo de PostgreSQL, verificarlo y usar \`--migraciones-revisadas\`.\n"
    fi
    printf "\n\`\`\`text\n"
    cat "$TRABAJO/$RAIZ_PAQUETE/migraciones.txt"
    printf "\`\`\`\n\n### Environment de GitHub\n\n"
    if [[ $VERIFICACION == ninguna ]]; then
      printf "Preparado **sin** verificar el environment \`vm-universitaria\`. Aplicarlo exige \`--sin-entorno-github\`.\n"
    else
      printf "Environment \`vm-universitaria\` verificado antes de preparar el paquete: solo admite la rama \`main\` y los administradores no pueden omitir sus reglas.\n"
    fi
    printf "\nEl paquete no registra ninguna aprobacion humana. Actualizar MicroK8s es una operacion aparte: el operador la realiza en la VM con \`desplegar-vm.sh --aplicar\`.\n"
    printf "\nEl paquete solo contiene los Deployments \`procesos-backend\` y \`procesos-frontend\`. "
    printf "\`desplegar-vm.sh\` actualiza unicamente sus imagenes y nunca aplica estos manifiestos.\n"
  } >"$TRABAJO/$RAIZ_PAQUETE/RESUMEN.md"
}

empaquetar() {
  local epoch nombre suma
  printf '\n== paquete ==\n'
  (cd "$TRABAJO/$RAIZ_PAQUETE" && sha256sum RESUMEN.md deployments.yaml metadatos.txt migraciones.txt >SHA256SUMS)
  epoch=$(g show -s --format=%ct "$COMMIT")
  nombre="paquete-despliegue-${COMMIT:0:12}.tar.gz"
  mkdir -p "$SALIDA"
  tar --sort=name --owner=0 --group=0 --numeric-owner --mtime="@$epoch" --mode='u=rwX,go=rX' \
    -cf - -C "$TRABAJO" "$RAIZ_PAQUETE" | gzip -n -9 >"$SALIDA/$nombre"
  suma=$(sha256sum "$SALIDA/$nombre" | cut -d' ' -f1)
  printf '%s  %s\n' "$suma" "$nombre" >"$SALIDA/$nombre.sha256"
  install -m 0644 "$RAIZ/k8s/scripts/desplegar-vm.sh" "$SALIDA/desplegar-vm.sh"
  cp "$TRABAJO/$RAIZ_PAQUETE/RESUMEN.md" "$SALIDA/RESUMEN.md"
  correcto "$nombre"
  correcto "SHA-256 $suma"
  if [[ -n ${GITHUB_OUTPUT:-} ]]; then
    printf 'paquete=%s\nsha256=%s\ncommit_corto=%s\n' "$nombre" "$suma" "${COMMIT:0:12}" >>"$GITHUB_OUTPUT"
  fi
}

principal() {
  local componente
  leer_argumentos "$@"
  requerir_herramientas
  TRABAJO=$(mktemp -d)
  trap 'rm -rf "$TRABAJO"' EXIT
  mkdir -p "$TRABAJO/$RAIZ_PAQUETE"
  printf 'Preparacion del paquete de despliegue de %s\n' "$REPOSITORIO"
  comprobar_commit
  leer_overlay
  for componente in "${COMPONENTES[@]}"; do
    comprobar_imagen "$componente"
  done
  terminar_si_hay_errores
  comprobar_migraciones
  renderizar_deployments
  escribir_metadatos
  escribir_resumen
  empaquetar
}

principal "$@"
