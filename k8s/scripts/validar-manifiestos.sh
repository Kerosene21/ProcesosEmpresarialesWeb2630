#!/usr/bin/env bash
set -euo pipefail

readonly VERSION_KUBERNETES="1.36.2"
readonly NAMESPACE="procesos-empresariales"
readonly URL_ESQUEMAS_KUBERNETES="https://raw.githubusercontent.com/yannh/kubernetes-json-schema/629fd45755a7eb4992f76a3be8cf040b57db9ce6"
readonly URL_ESQUEMAS_CRD="https://raw.githubusercontent.com/datreeio/CRDs-catalog/afebc073cd8727944413f0147fb8e3238d1a5352"
readonly -a ESQUEMAS=(
  "kubernetes configmap-v1.json e0eaddebd677c08aa092b2da2264d86ac4fc34eed112b9fac2945b3f00c1e9b1"
  "kubernetes deployment-apps-v1.json 3725782fb01e3f27d8be2da565e2d653d7b78bf6debe5440804cea993c87b8f9"
  "kubernetes ingress-networking-v1.json 4e0f63ad84c2bf22565e489d1f4b885ddaa9f6bf7cff1ddd562553760afe4d79"
  "kubernetes namespace-v1.json 324fae677b98d1a6d54340db0c334d053e8ffbafceb3f73326e41de2610d5843"
  "kubernetes networkpolicy-networking-v1.json f6324cc464f62228b0418f438d167208e4f86c7e3677ba30f608e79a8b26ba79"
  "kubernetes persistentvolume-v1.json ddff73572bf0965cecf9c160949a1c416f4f9e8ade7f46a649767589a1e2783c"
  "kubernetes service-v1.json 8bf019854daed511e7c174896a898173fa65d88ec5937c687a37303d4cc9351b"
  "kubernetes statefulset-apps-v1.json cfa6579916fc2a1be0deb6372a3c67c3747a212131d0cb7b342d645f65639987"
  "kubernetes storageclass-storage-v1.json 9704bb88ec73ed37f8d565dbd2d2ee1e8ee61b5ec63c96a5a61926d6971ef3c9"
  "crd traefik.io/middleware_v1alpha1.json 04af8163818b19c745df2992c4a3e82a43bd71fb6bb7042bfb56bfccf4c8dac6"
)
readonly -a COMPROBACIONES_APLICACION=(
  "estructura|Estructura, duplicados y etiqueta app.kubernetes.io/part-of"
  "tipos-aplicacion|Solo tipos de recursos autorizados y sin Secrets"
  "namespace|Namespace con Pod Security restricted y objetos en $NAMESPACE"
  "imagenes|Imagenes validas, sin latest ni sha-pendiente y fijadas por digest"
  "seguridad|Pod Security restricted y contexto de seguridad de cada contenedor"
  "sondas|Sondas startup, readiness y liveness con puertos declarados"
  "recursos|Requests y limits de CPU y memoria en todos los contenedores"
  "credenciales|Credenciales solo desde los Secrets que crea crear-secretos.sh"
  "configmaps|ConfigMaps referenciados, sin secretos y con la configuracion del overlay"
  "services|Services ClusterIP con selectores y puertos existentes"
  "deployments|Deployments con 2 replicas, RollingUpdate sin indisponibilidad y selectores coherentes"
  "postgresql|PostgreSQL con una replica, Service headless y volumen persistente"
  "network-policies|NetworkPolicies: denegacion por defecto y solo los flujos autorizados"
  "ingress|Ingress de Traefik, entrypoint, TLS y Middleware de limite"
)
readonly -a COMPROBACIONES_ALMACENAMIENTO=(
  "estructura|Estructura, duplicados y etiqueta app.kubernetes.io/part-of"
  "tipos-almacenamiento|Solo StorageClass y PersistentVolume"
  "almacenamiento|StorageClass y PV locales con Retain, nodo fijo y claimRef"
)

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly PROGRAMA_JQ="$RAIZ/k8s/scripts/validar-manifiestos.jq"
TRABAJO="$(mktemp -d)"
trap 'rm -rf "$TRABAJO"' EXIT
ESQUEMAS_DIR="${ESQUEMAS_DIR:-$TRABAJO/esquemas}"
SECRETOS='{}'
COMPROBACIONES=0
FALLIDAS=0
ERRORES=0
FILAS=()
DETALLES=()

seccion() {
  printf '\n== %s ==\n' "$1"
}

registrar() {
  local grupo=$1 descripcion=$2 errores=${3:-} linea n=0
  COMPROBACIONES=$((COMPROBACIONES + 1))
  if [[ -z $errores ]]; then
    printf '  OK     %s\n' "$descripcion"
    FILAS+=("| $grupo | $descripcion | OK |")
    return 0
  fi
  printf '  FALLO  %s\n' "$descripcion"
  while IFS= read -r linea; do
    [[ -n $linea ]] || continue
    n=$((n + 1))
    printf '         - %s\n' "$linea"
    DETALLES+=("$grupo: $linea")
    if [[ -n ${GITHUB_ACTIONS:-} ]]; then
      printf '::error title=Kubernetes %s::%s\n' "${grupo//[:,]/ }" "${linea//%/%25}"
    fi
  done <<<"$errores"
  FALLIDAS=$((FALLIDAS + 1))
  ERRORES=$((ERRORES + n))
  FILAS+=("| $grupo | $descripcion | FALLO ($n) |")
}

comprobar() {
  local entrada=$1 grupo=$2 overlay=$3 comprobacion=$4 descripcion=$5 salida
  if ! salida=$(jq -r -f "$PROGRAMA_JQ" \
    --arg comprobacion "$comprobacion" \
    --arg overlay "$overlay" \
    --arg namespace "$NAMESPACE" \
    --argjson secretos "$SECRETOS" \
    "$entrada" 2>&1); then
    salida="no se pudo evaluar la comprobacion $comprobacion: $salida"
  fi
  registrar "$grupo" "$descripcion" "$salida"
}

requerir_herramientas() {
  local herramienta faltan=() menor
  for herramienta in kubectl kubeconform yq jq shellcheck curl sha256sum; do
    command -v "$herramienta" >/dev/null 2>&1 || faltan+=("$herramienta")
  done
  if ((${#faltan[@]} > 0)); then
    printf 'Faltan herramientas: %s\n' "${faltan[*]}" >&2
    exit 2
  fi
  if ! yq --version 2>&1 | grep -q 'mikefarah'; then
    printf 'Se necesita yq de mikefarah (https://github.com/mikefarah/yq)\n' >&2
    exit 2
  fi
  if [[ ! -f $PROGRAMA_JQ ]]; then
    printf 'No se encuentra %s\n' "$PROGRAMA_JQ" >&2
    exit 2
  fi
  seccion "herramientas"
  printf '  kubectl %s, kustomize %s\n' \
    "$(kubectl version --client -o json | jq -r '.clientVersion.gitVersion')" \
    "$(kubectl version --client -o json | jq -r '.kustomizeVersion')"
  printf '  kubeconform %s, %s, %s, shellcheck %s\n' \
    "$(kubeconform -v)" "$(yq --version | awk '{print "yq " $NF}')" "$(jq --version)" \
    "$(shellcheck --version | awk '/^version:/ {print $2}')"
  menor=$(kubectl version --client -o json | jq -r '.clientVersion.minor' | tr -dc '0-9')
  if [[ $menor == "$(cut -d. -f2 <<<"$VERSION_KUBERNETES")" ]]; then
    registrar "herramientas" "kubectl de la misma version menor que MicroK8s ($VERSION_KUBERNETES)" ""
  else
    registrar "herramientas" "kubectl de la misma version menor que MicroK8s ($VERSION_KUBERNETES)" \
      "kubectl 1.$menor puede renderizar distinto que el kubectl de MicroK8s $VERSION_KUBERNETES"
  fi
}

preparar_esquemas() {
  local entrada origen archivo suma destino url errores=""
  for entrada in "${ESQUEMAS[@]}"; do
    read -r origen archivo suma <<<"$entrada"
    if [[ $origen == kubernetes ]]; then
      destino="v$VERSION_KUBERNETES-standalone-strict/$archivo"
      url="$URL_ESQUEMAS_KUBERNETES/$destino"
    else
      destino=$archivo
      url="$URL_ESQUEMAS_CRD/$archivo"
    fi
    mkdir -p "$(dirname "$ESQUEMAS_DIR/$destino")"
    if [[ ! -f $ESQUEMAS_DIR/$destino ]]; then
      if ! curl --proto '=https' --tlsv1.2 -fsSL --retry 3 -o "$ESQUEMAS_DIR/$destino.descarga" "$url"; then
        errores+="no se pudo descargar $url"$'\n'
        continue
      fi
      mv "$ESQUEMAS_DIR/$destino.descarga" "$ESQUEMAS_DIR/$destino"
    fi
    if ! printf '%s  %s\n' "$suma" "$ESQUEMAS_DIR/$destino" | sha256sum --check --status --strict; then
      errores+="$destino no coincide con su SHA-256 fijado; borra $ESQUEMAS_DIR y repite"$'\n'
    fi
  done
  registrar "herramientas" "${#ESQUEMAS[@]} esquemas fijados por commit y verificados con SHA-256" "${errores%$'\n'}"
  if [[ -n $errores ]]; then
    printf 'Sin esquemas verificados no se puede validar la estructura.\n' >&2
    exit 2
  fi
}

primer_error() {
  local detalle
  detalle=$(head -n 1 "$1")
  printf '%s' "${detalle//"$RAIZ/"/}"
}

revisar_yaml() {
  local archivo errores="" total=0
  while IFS= read -r -d '' archivo; do
    total=$((total + 1))
    if ! yq eval '.' "$archivo" >/dev/null 2>"$TRABAJO/yaml.err"; then
      errores+="${archivo#"$RAIZ"/}: $(primer_error "$TRABAJO/yaml.err")"$'\n'
    fi
  done < <(find "$RAIZ/k8s" "$RAIZ/.github" -type f \( -name '*.yaml' -o -name '*.yml' \) -print0 | sort -z)
  registrar "repositorio" "Sintaxis YAML de $total archivos en k8s/ y .github/" "${errores%$'\n'}"
}

revisar_credenciales() {
  local coincidencias
  coincidencias=$(grep -rIinoE \
    -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' \
    -e '(client-key-data|client-certificate-data|certificate-authority-data|token)[[:space:]]*:[[:space:]]*[A-Za-z0-9+/=._-]{20,}' \
    -e '^kind:[[:space:]]*Config[[:space:]]*$' \
    -e 'gh[pousr]_[A-Za-z0-9]{36}' \
    -e 'github_pat_[A-Za-z0-9_]{40,}' \
    -e 'sq[apu]_[0-9a-f]{40}' \
    -e 'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.' \
    "$RAIZ/k8s" "$RAIZ/.github" | cut -d: -f1,2 | sort -u || true)
  coincidencias=$(sed -e "s|^$RAIZ/||" -e 's|$|: posible credencial; el contenido no se muestra|' <<<"$coincidencias" | grep -v '^: ' || true)
  registrar "repositorio" "Sin claves privadas, kubeconfig ni tokens en k8s/ y .github/" "$coincidencias"
}

revisar_scripts() {
  local archivo primera interprete errores="" salida
  local -a scripts=()
  while IFS= read -r -d '' archivo; do
    scripts+=("$archivo")
  done < <(find "$RAIZ/k8s" -type f -name '*.sh' -print0 | sort -z)
  for archivo in "${scripts[@]}"; do
    IFS= read -r primera <"$archivo" || primera=""
    case $primera in
      '#!/usr/bin/env bash' | '#!/bin/bash') interprete="bash" ;;
      '#!/bin/sh' | '#!/usr/bin/env sh') interprete="sh" ;;
      *)
        errores+="${archivo#"$RAIZ"/}: shebang no reconocido"$'\n'
        continue
        ;;
    esac
    if ! "$interprete" -n "$archivo" 2>"$TRABAJO/sintaxis.err"; then
      errores+="${archivo#"$RAIZ"/}: $(primer_error "$TRABAJO/sintaxis.err")"$'\n'
    fi
  done
  registrar "repositorio" "Sintaxis de ${#scripts[@]} scripts con bash -n y sh -n" "${errores%$'\n'}"
  salida=$(shellcheck --format=gcc --severity=style "${scripts[@]}" 2>&1 || true)
  registrar "repositorio" "ShellCheck sin avisos en ${#scripts[@]} scripts" "${salida//"$RAIZ/"/}"
}

leer_secretos_esperados() {
  local archivo="$RAIZ/k8s/scripts/crear-secretos.sh" linea nombre claves
  if [[ -f $archivo ]]; then
    SECRETOS=$(
      grep -E '^crear [a-z0-9-]+ ' "$archivo" | while IFS= read -r linea; do
        nombre=$(cut -d' ' -f2 <<<"$linea")
        claves=$(grep -oE '"[A-Z][A-Z0-9_]*=' <<<"$linea" | tr -d '"=' | sed 's/.*/"&"/' | paste -sd, - || true)
        printf '{"%s": [%s]}\n' "$nombre" "$claves"
      done | jq -cs 'add // {}'
    ) || SECRETOS='{}'
  fi
  if [[ $SECRETOS == '{}' ]]; then
    registrar "repositorio" "Secrets esperados segun k8s/scripts/crear-secretos.sh" \
      "no se encontraron llamadas a crear en k8s/scripts/crear-secretos.sh"
  else
    registrar "repositorio" "Secrets esperados segun crear-secretos.sh: $(jq -r 'to_entries | map("\(.key) (\(.value | join(", ")))") | join("; ")' <<<"$SECRETOS")" ""
  fi
}

renderizar() {
  local nombre=$1 ruta=$2
  if ! kubectl kustomize "$RAIZ/$ruta" >"$TRABAJO/$nombre.yaml" 2>"$TRABAJO/$nombre.err"; then
    registrar "$nombre" "Renderizado de $ruta con kubectl kustomize" "$(cat "$TRABAJO/$nombre.err")"
    return 1
  fi
  if ! yq eval -o=json -I=0 '.' "$TRABAJO/$nombre.yaml" 2>"$TRABAJO/$nombre.err" |
    jq -s 'map(select(. != null))' >"$TRABAJO/$nombre.json" 2>>"$TRABAJO/$nombre.err"; then
    registrar "$nombre" "Renderizado de $ruta con kubectl kustomize" "$(cat "$TRABAJO/$nombre.err")"
    return 1
  fi
  registrar "$nombre" "Renderizado de $ruta con kubectl kustomize ($(jq length "$TRABAJO/$nombre.json") objetos)" ""
}

validar_esquemas() {
  local nombre=$1 salida
  if salida=$(kubeconform -strict -summary -kubernetes-version "$VERSION_KUBERNETES" \
    -schema-location "$ESQUEMAS_DIR/{{.NormalizedKubernetesVersion}}-standalone{{.StrictSuffix}}/{{.ResourceKind}}{{.KindSuffix}}.json" \
    -schema-location "$ESQUEMAS_DIR/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json" \
    "$TRABAJO/$nombre.yaml" 2>&1); then
    registrar "$nombre" "Esquemas estrictos de Kubernetes $VERSION_KUBERNETES y de la CRD de Traefik" ""
  else
    salida=$(grep -v '^Summary:' <<<"$salida" || true)
    registrar "$nombre" "Esquemas estrictos de Kubernetes $VERSION_KUBERNETES y de la CRD de Traefik" "${salida//"$TRABAJO/"/}"
  fi
}

validar_lista() {
  local nombre=$1 overlay=$2 entrada
  shift 2
  for entrada in "$@"; do
    comprobar "$TRABAJO/$nombre.json" "$nombre" "$overlay" "${entrada%%|*}" "${entrada#*|}"
  done
}

combinar() {
  jq -s '.' "$2" "$3" >"$1"
}

principal() {
  local overlay renderizados=" "
  requerir_herramientas
  preparar_esquemas

  seccion "repositorio"
  revisar_yaml
  revisar_credenciales
  revisar_scripts
  leer_secretos_esperados

  for overlay in local vm vm-http; do
    seccion "overlay $overlay"
    if renderizar "$overlay" "k8s/overlays/$overlay"; then
      renderizados+="$overlay "
      validar_esquemas "$overlay"
      validar_lista "$overlay" "$overlay" "${COMPROBACIONES_APLICACION[@]}"
    fi
  done

  seccion "overlay vm/almacenamiento"
  if renderizar almacenamiento "k8s/overlays/vm/almacenamiento"; then
    renderizados+="almacenamiento "
    validar_esquemas almacenamiento
    validar_lista almacenamiento almacenamiento "${COMPROBACIONES_ALMACENAMIENTO[@]}"
  fi

  seccion "compatibilidad entre overlays"
  for overlay in vm vm-http; do
    if [[ $renderizados == *" $overlay "* && $renderizados == *" almacenamiento "* ]]; then
      combinar "$TRABAJO/volumen-$overlay.json" "$TRABAJO/$overlay.json" "$TRABAJO/almacenamiento.json"
      comprobar "$TRABAJO/volumen-$overlay.json" "compatibilidad" "$overlay" volumen "El PV de vm/almacenamiento encaja con el PVC de $overlay"
    fi
  done
  if [[ $renderizados == *" vm "* && $renderizados == *" vm-http "* ]]; then
    combinar "$TRABAJO/vm-y-vm-http.json" "$TRABAJO/vm.json" "$TRABAJO/vm-http.json"
    comprobar "$TRABAJO/vm-y-vm-http.json" "compatibilidad" vm vm-y-vm-http "vm-http solo difiere de vm en entrypoint, TLS y COOKIE_SEGURA"
  fi
  if [[ $renderizados == *" local "* && $renderizados == *" vm "* ]]; then
    combinar "$TRABAJO/local-en-vm.json" "$TRABAJO/local.json" "$TRABAJO/vm.json"
    comprobar "$TRABAJO/local-en-vm.json" "compatibilidad" local local-en-vm "Todos los recursos de local existen tambien en vm"
  fi

  resumen
}

resumen() {
  printf '\n'
  if ((ERRORES == 0)); then
    printf 'Validacion correcta: %d comprobaciones sin errores.\n' "$COMPROBACIONES"
  else
    printf 'Validacion fallida: %d errores en %d de %d comprobaciones.\n' "$ERRORES" "$FALLIDAS" "$COMPROBACIONES" >&2
  fi
  if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
    {
      printf '### Validacion de manifiestos de Kubernetes %s\n\n' "$VERSION_KUBERNETES"
      printf '| Grupo | Comprobacion | Resultado |\n| --- | --- | --- |\n'
      printf '%s\n' "${FILAS[@]}"
      if ((${#DETALLES[@]} > 0)); then
        printf '\n#### Errores\n\n'
        printf -- '- %s\n' "${DETALLES[@]}"
      fi
      printf '\n%d comprobaciones, %d con errores.\n' "$COMPROBACIONES" "$FALLIDAS"
    } >>"$GITHUB_STEP_SUMMARY"
  fi
  ((ERRORES == 0))
}

principal "$@"
