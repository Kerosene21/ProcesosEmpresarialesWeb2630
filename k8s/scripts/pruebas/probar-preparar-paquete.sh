#!/usr/bin/env bash
set -uo pipefail

DIR_PRUEBAS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ_REAL="$(cd "$DIR_PRUEBAS/../../.." && pwd)"
TEMPORAL="$(mktemp -d)"
trap 'rm -rf "$TEMPORAL"' EXIT
export DOCKER="bash $DIR_PRUEBAS/docker-simulado.sh"
TOTAL=0
CORRECTOS=0
FALLIDOS=()
FILAS=()
readonly REPO_BACKEND="ghcr.io/kerosene21/procesos-empresariales-backend"
readonly REPO_FRONTEND="ghcr.io/kerosene21/procesos-empresariales-frontend"

for herramienta in git yq jq kubectl sha256sum tar gzip; do
  command -v "$herramienta" >/dev/null 2>&1 || { printf 'Falta %s para estas pruebas\n' "$herramienta" >&2; exit 2; }
done

digest() { printf 'sha256:%064x' "$1"; }

git_prueba() {
  git -C "$REPO" -c user.name=Pruebas -c user.email=pruebas@example.invalid -c commit.gpgsign=false "$@"
}

fijar_overlay() {
  sed -i -E "/name: procesos-empresariales-backend/,/digest:/ s|digest: sha256:[0-9a-f]{64}|digest: $1|" "$REPO/k8s/overlays/vm/kustomization.yaml"
  sed -i -E "/name: procesos-empresariales-frontend/,/digest:/ s|digest: sha256:[0-9a-f]{64}|digest: $2|" "$REPO/k8s/overlays/vm/kustomization.yaml"
}

registrar_imagen() {
  local repo=$1 digest=$2 revision=$3 componente=$4 arquitectura=${5:-amd64} repositorio=${6:-Kerosene21/ProcesosEmpresarialesWeb2630} flujo=${7:-refs/heads/main}
  local clave
  clave="${repo//[\/:@]/_}_${digest//[\/:@]/_}"
  printf '{"digest":"%s","manifests":[{"platform":{"os":"linux","architecture":"%s"}},{"platform":{"os":"unknown","architecture":"unknown"}}]}' "$digest" "$arquitectura" >"$IMAGENES_SIMULADAS/$clave.Manifest.json"
  printf '{"os":"linux","architecture":"%s","config":{"Labels":{"org.opencontainers.image.revision":"%s","org.opencontainers.image.source":"https://github.com/Kerosene21/ProcesosEmpresarialesWeb2630","org.opencontainers.image.title":"procesos-empresariales-%s"}}}' "$arquitectura" "$revision" "$componente" >"$IMAGENES_SIMULADAS/$clave.Image.json"
  if [[ $repositorio != sin-procedencia ]]; then
    printf '{"SLSA":{"buildDefinition":{"internalParameters":{"github_repository":"%s","github_workflow_ref":"%s/.github/workflows/imagenes.yml@%s","github_workflow_sha":"%s","github_runner_environment":"github-hosted","github_run_id":"123456"}}}}' "$repositorio" "$repositorio" "$flujo" "$revision" >"$IMAGENES_SIMULADAS/$clave.Provenance.json"
  fi
}

registrar_etiqueta() {
  local repo=$1 etiqueta=$2 digest=$3
  printf '{"digest":"%s","manifests":[]}' "$digest" >"$IMAGENES_SIMULADAS/${repo//[\/:@]/_}_$etiqueta.Manifest.json"
}

crear_repositorio() {
  local modificar_v1=${1:-no}
  REPO="$TEMPORAL/repo-$TOTAL"
  IMAGENES_SIMULADAS="$TEMPORAL/imagenes-$TOTAL"
  export IMAGENES_SIMULADAS
  rm -rf "$REPO" "$IMAGENES_SIMULADAS"
  mkdir -p "$REPO/src/main/resources/db/migration" "$REPO/src/main/java" "$REPO/frontend" "$IMAGENES_SIMULADAS"
  cp -r "$RAIZ_REAL/k8s" "$REPO/k8s"
  printf '<project/>\n' >"$REPO/pom.xml"
  printf 'CREATE TABLE empresa (id BIGINT);\n' >"$REPO/src/main/resources/db/migration/V1__esquema_inicial.sql"
  printf 'class A {}\n' >"$REPO/src/main/java/A.java"
  printf '{}\n' >"$REPO/frontend/package.json"
  git -C "$REPO" init -q -b main
  fijar_overlay "$(digest 11)" "$(digest 12)"
  git_prueba add -A
  git_prueba commit -q -m "version 1"
  C1=$(git_prueba rev-parse HEAD)
  if [[ $modificar_v1 == si ]]; then
    printf 'CREATE TABLE empresa (id BIGINT, nombre TEXT);\n' >"$REPO/src/main/resources/db/migration/V1__esquema_inicial.sql"
  fi
  printf 'CREATE TABLE proceso (id BIGINT);\n' >"$REPO/src/main/resources/db/migration/V2__procesos.sql"
  printf 'class A { int b; }\n' >"$REPO/src/main/java/A.java"
  git_prueba add -A
  git_prueba commit -q -m "version 2 del codigo"
  C2=$(git_prueba rev-parse HEAD)
  fijar_overlay "$(digest 21)" "$(digest 22)"
  git_prueba add -A
  git_prueba commit -q -m "actualizar digests"
  C3=$(git_prueba rev-parse HEAD)
  registrar_imagen "$REPO_BACKEND" "$(digest 11)" "$C1" backend
  registrar_imagen "$REPO_FRONTEND" "$(digest 12)" "$C1" frontend
  registrar_imagen "$REPO_BACKEND" "$(digest 21)" "$C2" backend
  registrar_imagen "$REPO_FRONTEND" "$(digest 22)" "$C2" frontend
}

preparar() {
  SALIDA_PAQUETE="$TEMPORAL/salida-$TOTAL"
  SALIDA="$TEMPORAL/log-$TOTAL.txt"
  rm -rf "$SALIDA_PAQUETE"
  bash "$REPO/k8s/scripts/preparar-paquete.sh" --salida "$SALIDA_PAQUETE" "$@" >"$SALIDA" 2>&1
  CODIGO=$?
}

comprobar() {
  local nombre=$1 resultado=$2
  TOTAL=$((TOTAL + 1))
  if [[ $resultado == SI ]]; then
    CORRECTOS=$((CORRECTOS + 1))
  else
    FALLIDOS+=("$nombre")
    printf -- '--- salida de %s ---\n' "$nombre"
    cat "$SALIDA" 2>/dev/null
  fi
  FILAS+=("| $TOTAL | $nombre | ${CODIGO:-} | $resultado |")
  printf '%-3s %-3s codigo=%-2s %s\n' "$TOTAL" "$resultado" "${CODIGO:-}" "$nombre"
}

esperar() {
  if [[ $CODIGO == "$1" ]] && grep -qF -- "$2" "$SALIDA"; then
    if [[ $1 != 0 && -n $(find "$SALIDA_PAQUETE" -name '*.tar.gz' 2>/dev/null) ]]; then
      printf 'NO'
    else
      printf 'SI'
    fi
  else
    printf 'NO'
  fi
}

metadato() {
  tar -xzOf "$SALIDA_PAQUETE"/paquete-despliegue-*.tar.gz paquete-despliegue/metadatos.txt | sed -n "s/^$1=//p"
}

prueba_correcta() {
  crear_repositorio
  local r primero segundo
  preparar --commit "$C3" --aprobacion ninguna
  r=$(esperar 0 "SHA-256")
  [[ $(metadato backend_imagen) == "$REPO_BACKEND@$(digest 21)" && $(metadato backend_revision) == "$C2" ]] || r=NO
  [[ $(metadato backend_anterior) == "$REPO_BACKEND@$(digest 11)" && $(metadato migraciones_nuevas) == 1 ]] || r=NO
  [[ $(metadato aprobacion) == ninguna && $(metadato commit) == "$C3" ]] || r=NO
  tar -xzOf "$SALIDA_PAQUETE"/paquete-despliegue-*.tar.gz paquete-despliegue/migraciones.txt | grep -q '  V2__procesos.sql  nueva$' || r=NO
  tar -xzOf "$SALIDA_PAQUETE"/paquete-despliegue-*.tar.gz paquete-despliegue/deployments.yaml | grep -q '^kind: StatefulSet' && r=NO
  [[ -f $SALIDA_PAQUETE/desplegar-vm.sh && -f $SALIDA_PAQUETE/RESUMEN.md ]] || r=NO
  (cd "$SALIDA_PAQUETE" && sha256sum --check --quiet ./*.sha256) || r=NO
  primero=$(cut -d' ' -f1 "$SALIDA_PAQUETE"/*.sha256)
  PAQUETE_CORRECTO=$(ls "$SALIDA_PAQUETE"/paquete-despliegue-*.tar.gz)
  preparar --commit "$C3" --aprobacion ninguna
  segundo=$(cut -d' ' -f1 "$SALIDA_PAQUETE"/*.sha256)
  [[ $primero == "$segundo" ]] || r=NO
  comprobar "Paquete correcto, reproducible y con una migracion nueva detectada" "$r"
  PAQUETE_CORRECTO="$SALIDA_PAQUETE/$(basename "$PAQUETE_CORRECTO")"
}

prueba_integracion_despliegue() {
  local r sim="$TEMPORAL/sim-integracion"
  mkdir -p "$sim/namespace" "$sim/deploy/procesos-backend" "$sim/deploy/procesos-frontend" "$sim/sts" "$sim/fallos" "$sim/http"
  printf 'restricted' >"$sim/namespace/procesos-empresariales"
  printf 'init esperar-postgres %s\napp backend %s\n' "$REPO_BACKEND@$(digest 11)" "$REPO_BACKEND@$(digest 11)" >"$sim/deploy/procesos-backend/contenedores"
  printf 'app frontend %s\n' "$REPO_FRONTEND@$(digest 12)" >"$sim/deploy/procesos-frontend/contenedores"
  printf '2 2 2 2 1 1\n' >"$sim/deploy/procesos-backend/estado"
  printf '2 2 2 2 1 1\n' >"$sim/deploy/procesos-frontend/estado"
  printf '1 1' >"$sim/sts/procesos-postgres"
  : >"$sim/llamadas.log"
  SALIDA="$TEMPORAL/log-integracion.txt"
  SIMULACION=$sim KUBECTL="bash $DIR_PRUEBAS/kubectl-simulado.sh" CURL="bash $DIR_PRUEBAS/curl-simulado.sh" \
    bash "$REPO/k8s/scripts/desplegar-vm.sh" --registro "$TEMPORAL/registro-integracion" --paquete "$PAQUETE_CORRECTO" \
    --sha256 "$(sha256sum "$PAQUETE_CORRECTO" | cut -d' ' -f1)" --sin-http >"$SALIDA" 2>&1
  CODIGO=$?
  r=NO
  if [[ $CODIGO == 0 ]] && grep -qF "la nueva version incluye 1 migraciones de Flyway nuevas" "$SALIDA" && grep -qF "Comprobacion correcta" "$SALIDA"; then
    r=SI
  fi
  SALIDA_PAQUETE="$TEMPORAL/vacio"
  comprobar "desplegar-vm.sh acepta en modo comprobacion el paquete generado" "$r"
}

prueba_rechazo() {
  local nombre=$1 codigo=$2 patron=$3 preparacion=$4
  shift 4
  crear_repositorio "${MODIFICAR_V1:-no}"
  "$preparacion"
  preparar --commit "${COMMIT_PRUEBA:-$C3}" --aprobacion ninguna "$@"
  comprobar "$nombre" "$(esperar "$codigo" "$patron")"
  COMMIT_PRUEBA=""
  MODIFICAR_V1=""
}

ninguna() { :; }
imagen_ausente() { rm "$IMAGENES_SIMULADAS"/*"$(printf '%064x' 21)".*.json; }
sin_amd64() { registrar_imagen "$REPO_BACKEND" "$(digest 21)" "$C2" backend arm64; }
revision_ajena() { registrar_imagen "$REPO_BACKEND" "$(digest 21)" "$(printf '%040x' 99)" backend; }
otro_repositorio() { registrar_imagen "$REPO_BACKEND" "$(digest 21)" "$C2" backend amd64 atacante/procesos; }
sin_procedencia() { rm "$IMAGENES_SIMULADAS"/*"$(printf '%064x' 21)".Provenance.json; }
rama_no_autorizada() { registrar_imagen "$REPO_BACKEND" "$(digest 21)" "$C2" backend amd64 Kerosene21/ProcesosEmpresarialesWeb2630 refs/heads/feature/x; }
digest_distinto() { sed -i "s/$(printf '%064x' 21)/$(printf '%064x' 23)/" "$IMAGENES_SIMULADAS"/*"$(printf '%064x' 21)".Manifest.json; }
cambio_local() { printf '# local\n' >>"$REPO/k8s/base/namespace.yaml"; }
usar_etiqueta() {
  sed -i -E '/name: procesos-empresariales-backend/,/digest:/ s|digest: sha256:[0-9a-f]{64}|newTag: sha-abc1234|' "$REPO/k8s/overlays/vm/kustomization.yaml"
  git_prueba commit -q -am "etiqueta mutable"
  C3=$(git_prueba rev-parse HEAD)
}
reciente_distinta() { registrar_etiqueta "$REPO_BACKEND" "sha-${C3:0:7}" "$(digest 31)"; }
sin_anterior() {
  git_prueba reset -q --hard "$C1"
  C3=$C1
}

printf 'Pruebas de preparar-paquete.sh con GHCR simulado\n\n'
prueba_correcta
prueba_integracion_despliegue
prueba_rechazo "Imagen ausente en GHCR" 1 "no existe en GHCR o no es accesible" imagen_ausente
prueba_rechazo "Imagen sin linux/amd64" 1 "no tiene imagen linux/amd64" sin_amd64
prueba_rechazo "Revision de la imagen fuera de la historia de main" 1 "no pertenece a la historia del commit" revision_ajena
prueba_rechazo "Procedencia de otro repositorio" 1 "no corresponde al repositorio" otro_repositorio
prueba_rechazo "Imagen sin procedencia SLSA" 1 "no publica procedencia SLSA" sin_procedencia
prueba_rechazo "Imagen construida desde una rama no autorizada" 1 "no proviene de imagenes.yml en main" rama_no_autorizada
prueba_rechazo "GHCR devuelve otro digest" 1 "GHCR devolvio un digest distinto" digest_distinto
prueba_rechazo "Overlay con etiqueta mutable" 1 "usa newTag" usar_etiqueta
prueba_rechazo "Cambios locales sin confirmar" 1 "tiene cambios sin confirmar" cambio_local
COMMIT_PRUEBA=$(printf '%040x' 7)
prueba_rechazo "Commit solicitado distinto del codigo" 1 "no en el commit solicitado" ninguna
MODIFICAR_V1=si
prueba_rechazo "Migracion aplicada modificada" 1 "Flyway rechazaria el checksum" ninguna
prueba_rechazo "Imagen mas reciente publicada: se conserva el digest de Git" 0 "el paquete conserva el digest de Git" reciente_distinta
prueba_rechazo "Sin version anterior registrada en Git" 0 "las migraciones se marcan sin comparar" sin_anterior
prueba_rechazo "Argumentos no validos" 2 "Uso: preparar-paquete.sh" ninguna --aprobacion cualquiera

printf '\n%d de %d pruebas correctas\n' "$CORRECTOS" "$TOTAL"
if [[ -n ${GITHUB_STEP_SUMMARY:-} ]]; then
  {
    printf '### Pruebas de preparar-paquete.sh: %d de %d correctas\n\n' "$CORRECTOS" "$TOTAL"
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
