#!/usr/bin/env bash
# Comprobaciones de solo lectura en la VM antes de desplegar el overlay vm. No crea, modifica ni borra nada,
# no usa sudo y no imprime Secrets, kubeconfig ni credenciales.
#   bash revisar-vm.sh > revision-vm.txt
set -u
read -r -a K <<< "${KUBECTL:-microk8s kubectl}"
seccion() { printf '\n== %s\n' "$1"; }
# Oculta el valor de cualquier argumento cuyo nombre sugiera un secreto o un dato personal.
ocultar() { sed -E 's/^([[:space:]]*-{0,2}[^=[:space:]]*(token|pass|secret|key|credential|auth|email)[^=[:space:]]*=).*/\1<oculto>/I'; }

seccion "Nodo, arquitectura y nombre para el PV local (kubernetes.io/hostname)"
"${K[@]}" get nodes -o wide
"${K[@]}" get nodes -o jsonpath='{range .items[*]}{.metadata.labels.kubernetes\.io/hostname} arquitectura={.status.nodeInfo.architecture}{"\n"}{end}'
uname -m

seccion "Addons habilitados"
microk8s status --format short 2>/dev/null | grep -E ': enabled' || microk8s status 2>/dev/null | sed -n '/enabled:/,/disabled:/p'

seccion "IngressClass"
"${K[@]}" get ingressclass -o wide

seccion "Traefik: controladores, pods y etiquetas"
"${K[@]}" -n ingress get deploy,daemonset -o wide
"${K[@]}" -n ingress get pods -o wide --show-labels

seccion "Traefik: hostNetwork, puertos, imagen y argumentos (valores sensibles ocultos)"
"${K[@]}" -n ingress get pods -o jsonpath='{range .items[*]}{.metadata.name} hostNetwork={.spec.hostNetwork} containerPorts={.spec.containers[0].ports[*].containerPort} hostPorts={.spec.containers[0].ports[*].hostPort}{"\n"}imagen={.spec.containers[0].image}{"\n"}{range .spec.containers[0].args[*]}{"  "}{@}{"\n"}{end}{end}' | ocultar

seccion "Traefik: confianza en X-Forwarded-*, PROXY protocol y namespaces vigilados"
argumentos=$("${K[@]}" -n ingress get pods -o jsonpath='{range .items[*]}{range .spec.containers[0].args[*]}{@}{"\n"}{end}{end}' 2>/dev/null)
if [ -z "$argumentos" ]; then
  echo "no se encontraron pods de Traefik con argumentos en el namespace ingress"
else
  relevantes=$(printf '%s\n' "$argumentos" \
    | grep -iE 'forwardedheaders|proxyprotocol|providers\.kubernetes(crd|ingress)\.(namespaces|ingressclass|allowcrossnamespace)' | ocultar)
  printf '%s\n' "${relevantes:-sin forwardedHeaders, PROXY protocol ni restricciones de namespace: Traefik no confia en X-Forwarded-* de los clientes}"
fi

seccion "Traefik: Services y externalTrafficPolicy"
"${K[@]}" -n ingress get svc -o wide
"${K[@]}" -n ingress get svc -o jsonpath='{range .items[*]}{.metadata.name} type={.spec.type} externalTrafficPolicy={.spec.externalTrafficPolicy}{"\n"}{end}'

seccion "CRD de Traefik (Middleware para el limite de peticiones)"
"${K[@]}" get crd middlewares.traefik.io -o jsonpath='{.metadata.name} versiones={.spec.versions[*].name}{"\n"}' 2>&1

seccion "Calico: red de pods"
"${K[@]}" get ippools.crd.projectcalico.org -o jsonpath='{range .items[*]}{.metadata.name} cidr={.spec.cidr} natOutgoing={.spec.natOutgoing}{"\n"}{end}' 2>/dev/null
grep -h -- '--cluster-cidr' /var/snap/microk8s/current/args/kube-proxy 2>/dev/null

seccion "CoreDNS"
"${K[@]}" -n kube-system get pods -l k8s-app=kube-dns -o wide --show-labels
"${K[@]}" -n kube-system get svc -l k8s-app=kube-dns -o wide

seccion "Admision del API server (PodSecurity)"
grep -h -E -- '--(enable|disable)-admission-plugins' /var/snap/microk8s/current/args/kube-apiserver 2>/dev/null || echo "sin cambios en los plugins de admision (PodSecurity activo por defecto)"

seccion "Almacenamiento"
"${K[@]}" get storageclass 2>&1
"${K[@]}" get pv 2>&1
"${K[@]}" get pvc -A 2>&1

seccion "Namespaces, Ingress y NetworkPolicies existentes"
"${K[@]}" get ns
"${K[@]}" get ingress -A 2>&1
"${K[@]}" get networkpolicy -A 2>&1

seccion "Disco"
df -h / /var/lib /var/snap/microk8s/common 2>/dev/null

seccion "UID y GID 70 en el host"
getent passwd 70 || echo "uid 70 libre"
getent group 70 || echo "gid 70 libre"
ls -ld /var/lib/procesos-empresariales 2>&1

seccion "Salida HTTPS a los registros de imagenes"
for url in https://ghcr.io/v2/ https://registry-1.docker.io/v2/; do
  printf '%s -> HTTP %s\n' "$url" "$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$url")"
done

seccion "Proxy HTTP (solo si existe; nunca se muestran los valores)"
# Solo se imprimen nombres de variables; el contenido puede incluir usuarios, claves o tokens.
proxy_definido() { grep -oiE '^[[:space:]]*(export[[:space:]]+)?(https?_proxy|no_proxy|all_proxy)[[:space:]]*=' "$1" 2>/dev/null \
  | sed -E 's/^[[:space:]]*(export[[:space:]]+)?//; s/[[:space:]]*=$//' | sort -u | tr '\n' ' '; }
for archivo in /var/snap/microk8s/current/args/containerd-env /etc/environment; do
  if [ -r "$archivo" ]; then
    nombres=$(proxy_definido "$archivo")
    printf '%s: %s\n' "$archivo" "${nombres:-sin variables de proxy}"
  else
    printf '%s: no legible sin privilegios\n' "$archivo"
  fi
done
nombres=$(env | cut -d= -f1 | grep -iE '^(https?_proxy|no_proxy|all_proxy)$' | sort -u | tr '\n' ' ')
printf 'sesion actual: %s\n' "${nombres:-sin variables de proxy}"

seccion "Puertos en escucha (sin procesos)"
ss -ltnH | awk '{print $4}' | sort -u

seccion "Reenvio de puertos por SSH"
grep -hiE '^\s*(AllowTcpForwarding|GatewayPorts|PermitOpen)' /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null || echo "sin directivas: AllowTcpForwarding toma su valor por defecto (yes)"

seccion "Cortafuegos (solo estado visible sin privilegios)"
# is-active devuelve un codigo distinto de cero si ufw esta inactivo; aqui solo se informa del estado.
estado_ufw=$(systemctl is-active ufw 2>/dev/null) || true
printf 'ufw: %s\n' "${estado_ufw:-desconocido (systemctl no disponible)}"
