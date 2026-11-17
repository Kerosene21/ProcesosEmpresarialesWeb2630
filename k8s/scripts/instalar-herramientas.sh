#!/usr/bin/env bash
set -euo pipefail

DESTINO=${1:?Uso: instalar-herramientas.sh DIRECTORIO_DESTINO}
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FLUJO="$RAIZ/.github/workflows/kubernetes.yml"

valor() {
  local clave=$1 patron=$2 valores
  valores=$(sed -n -E "s/^ +$clave: *([^ ]+) *$/\\1/p" "$FLUJO" | tr -d '\r')
  if [[ $(grep -c '' <<<"$valores") != 1 || ! $valores =~ $patron ]]; then
    printf 'No se pudo leer un valor valido de %s en %s\n' "$clave" "$FLUJO" >&2
    exit 1
  fi
  printf '%s' "$valores"
}

descargar() {
  local nombre=$1 url=$2 suma=$3
  curl --proto '=https' --tlsv1.2 -fsSL --retry 3 --connect-timeout 20 --max-time 300 -o "$TRABAJO/$nombre" "$url"
  printf '%s  %s\n' "$suma" "$TRABAJO/$nombre" | sha256sum --check --strict
}

VERSION='^v[0-9]+\.[0-9]+\.[0-9]+$'
SUMA='^[0-9a-f]{64}$'
KUBECTL_VERSION=$(valor KUBECTL_VERSION "$VERSION")
KUBECTL_SHA256=$(valor KUBECTL_SHA256 "$SUMA")
KUBECONFORM_VERSION=$(valor KUBECONFORM_VERSION "$VERSION")
KUBECONFORM_SHA256=$(valor KUBECONFORM_SHA256 "$SUMA")
YQ_VERSION=$(valor YQ_VERSION "$VERSION")
YQ_SHA256=$(valor YQ_SHA256 "$SUMA")

TRABAJO=$(mktemp -d)
trap 'rm -rf "$TRABAJO"' EXIT
mkdir -p "$DESTINO"
descargar kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" "$KUBECTL_SHA256"
descargar kubeconform.tar.gz "https://github.com/yannh/kubeconform/releases/download/${KUBECONFORM_VERSION}/kubeconform-linux-amd64.tar.gz" "$KUBECONFORM_SHA256"
descargar yq "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64" "$YQ_SHA256"
tar -xzf "$TRABAJO/kubeconform.tar.gz" -C "$TRABAJO" kubeconform
install -m 0755 "$TRABAJO/kubectl" "$TRABAJO/kubeconform" "$TRABAJO/yq" "$DESTINO/"
printf 'kubectl %s, kubeconform %s y yq %s instalados en %s con las versiones de kubernetes.yml\n' \
  "$KUBECTL_VERSION" "$KUBECONFORM_VERSION" "$YQ_VERSION" "$DESTINO"
