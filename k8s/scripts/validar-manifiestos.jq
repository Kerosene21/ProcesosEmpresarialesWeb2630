def tipos_aplicacion: [
  "v1 Namespace", "v1 ConfigMap", "v1 Service", "apps/v1 Deployment", "apps/v1 StatefulSet",
  "networking.k8s.io/v1 Ingress", "networking.k8s.io/v1 NetworkPolicy", "traefik.io/v1alpha1 Middleware"
];
def tipos_almacenamiento: ["storage.k8s.io/v1 StorageClass", "v1 PersistentVolume"];
def flujos_esperados: [
  "app.kubernetes.io/name=backend egress dns 53/TCP",
  "app.kubernetes.io/name=backend egress dns 53/UDP",
  "app.kubernetes.io/name=backend egress pods:app.kubernetes.io/name=postgres 5432/TCP",
  "app.kubernetes.io/name=backend ingress traefik 8080/TCP",
  "app.kubernetes.io/name=frontend ingress traefik 8080/TCP",
  "app.kubernetes.io/name=postgres ingress pods:app.kubernetes.io/name=backend 5432/TCP"
];
def traefik_k3s: {namespace: "kube-system", etiquetas: {"app.kubernetes.io/name": "traefik"}};
def traefik_microk8s: {namespace: "ingress", etiquetas: {"app.kubernetes.io/name": "traefik", "app.kubernetes.io/instance": "traefik-ingress"}};
def almacenamiento_vm: {clase: "procesos-postgres-local", tamano: "5Gi"};
def configuracion_comun: {SPRING_PROFILES_ACTIVE: "prod", SWAGGER_HABILITADO: "false"};
def expectativas:
  {
    "local": {
      imagenes: "local", entrada: "web", tls: false, middleware: false, traefik: traefik_k3s, almacenamiento: null,
      configuracion: (configuracion_comun + {COOKIE_SEGURA: "false", SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES: "10\\.42\\.\\d{1,3}\\.\\d{1,3}"})
    },
    "vm": {
      imagenes: "digest", entrada: "websecure", tls: true, middleware: true, traefik: traefik_microk8s, almacenamiento: almacenamiento_vm,
      configuracion: (configuracion_comun + {COOKIE_SEGURA: "true", SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES: "10\\.1\\.\\d{1,3}\\.\\d{1,3}"})
    },
    "vm-http": {
      imagenes: "digest", entrada: "web", tls: false, middleware: true, traefik: traefik_microk8s, almacenamiento: almacenamiento_vm,
      configuracion: (configuracion_comun + {COOKIE_SEGURA: "false", SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES: "10\\.1\\.\\d{1,3}\\.\\d{1,3}"})
    }
  } | .[$overlay] // error("overlay sin expectativas: \($overlay)");

def ref: "\(.kind)/\(.metadata.name)";
def cargas: .[] | select(.kind == "Deployment" or .kind == "StatefulSet");
def plantilla: .spec.template.spec;
def iniciales: (plantilla | .initContainers // [])[];
def principales: (plantilla | .containers // [])[];
def contenedores: iniciales, principales;
def efectivo($contenedor; $pod; $campo): if ($contenedor | has($campo)) then $contenedor[$campo] else $pod[$campo] end;
def coincide($etiquetas): . as $m | ($m | type) == "object" and ($m | length) > 0 and all($m | to_entries[]; $etiquetas[.key] == .value);
def etiquetas_texto: to_entries | sort_by(.key) | map("\(.key)=\(.value)") | join(",");
def cantidad_valida: ((type == "string") or (type == "number")) and (tostring | test("^[0-9]+(\\.[0-9]+)?(m|k|M|G|T|Ki|Mi|Gi|Ti)?$"));
def cantidad:
  tostring | capture("^(?<n>[0-9]+(\\.[0-9]+)?)(?<u>[A-Za-z]*)$")
  | (.n | tonumber) * ({"": 1, "m": 0.001, "k": 1000, "M": 1000000, "G": 1000000000, "T": 1000000000000,
                         "Ki": 1024, "Mi": 1048576, "Gi": 1073741824, "Ti": 1099511627776}[.u]);
def etiqueta_imagen: split("@")[0] | split("/") | last | split(":") | if length > 1 then last else null end;
def par($traefik):
  if .ipBlock != null then "ipBlock:\(.ipBlock.cidr)"
  elif .namespaceSelector != null then
    (if .namespaceSelector == {"matchLabels": {"kubernetes.io/metadata.name": $traefik.namespace}} and .podSelector == {"matchLabels": $traefik.etiquetas} then "traefik"
     elif .namespaceSelector == {"matchLabels": {"kubernetes.io/metadata.name": "kube-system"}} and .podSelector == {"matchLabels": {"k8s-app": "kube-dns"}} then "dns"
     else "namespace:\(.namespaceSelector | tojson),pods:\(.podSelector | tojson)" end)
  elif ((.podSelector.matchLabels // {}) | length) > 0 and ((.podSelector | keys) == ["matchLabels"]) then "pods:\(.podSelector.matchLabels | etiquetas_texto)"
  else "cualquiera:\(tojson)" end;

def estructura:
  (if length == 0 then "el renderizado no contiene objetos" else empty end),
  (.[] | select((.apiVersion | type) != "string" or (.kind | type) != "string" or (.metadata.name | type) != "string") | "objeto sin apiVersion, kind o metadata.name"),
  (group_by([.kind, (.metadata.namespace // ""), .metadata.name])[] | select(length > 1) | "objeto duplicado: \(.[0] | ref)"),
  (.[] | select(.metadata.labels["app.kubernetes.io/part-of"] != "procesos-empresariales") | "\(ref): falta la etiqueta app.kubernetes.io/part-of=procesos-empresariales");

def tipos($autorizados):
  .[] | (.apiVersion + " " + .kind) as $tipo
  | if .kind == "Secret" then "\(ref): los Secrets no se versionan; los crea k8s/scripts/crear-secretos.sh"
    elif ((.apiVersion // "") | startswith("traefik.containo.us/")) then "\(ref): traefik.containo.us no existe en Traefik v3; usa traefik.io/v1alpha1"
    elif ($tipo | IN($autorizados[]) | not) then "\(ref): \($tipo) no esta autorizado en este renderizado"
    else empty end;

def namespace_y_pod_security:
  ([.[] | select(.kind == "Namespace")] as $ns
   | if ($ns | length) != 1 or $ns[0].metadata.name != $namespace then "debe existir exactamente un Namespace llamado \($namespace)"
     else ($ns[0].metadata.labels // {}) as $l | ("enforce", "warn", "audit") as $modo
       | select($l["pod-security.kubernetes.io/\($modo)"] != "restricted")
       | "Namespace/\($namespace): pod-security.kubernetes.io/\($modo) debe ser restricted" end),
  (.[] | select(.kind != "Namespace" and .metadata.namespace != $namespace) | "\(ref): namespace \(.metadata.namespace // "sin definir"), se esperaba \($namespace)");

def imagenes:
  expectativas as $e
  | cargas | ref as $r | (.metadata.name | ltrimstr("procesos-")) as $componente
  | contenedores | .name as $c | .image as $i
  | if ($i | type) != "string" or ($i | test("^[a-z0-9]+([._-][a-z0-9]+)*(:[0-9]+)?(/[a-z0-9]+([._-][a-z0-9]+)*)*(:[A-Za-z0-9_][A-Za-z0-9_.-]{0,127})?(@sha256:[0-9a-f]{64})?$") | not) then "\($r) \($c): referencia de imagen no valida: \($i)"
    elif ($i | etiqueta_imagen) == "sha-pendiente" then "\($r) \($c): imagen sin publicar (sha-pendiente)"
    elif ($i | etiqueta_imagen) == "latest" then "\($r) \($c): la etiqueta latest no esta permitida: \($i)"
    elif ($i | contains("@sha256:") | not) and ($i | etiqueta_imagen) == null then "\($r) \($c): imagen sin etiqueta ni digest, equivale a latest: \($i)"
    elif ($componente | IN("backend", "frontend")) and ($i | test("(^|/)procesos-empresariales-\($componente)[:@]") | not) then "\($r) \($c): debe usar la imagen procesos-empresariales-\($componente): \($i)"
    elif $e.imagenes == "digest" and ($i | contains("@sha256:") | not) then "\($r) \($c): la imagen debe fijarse por digest: \($i)"
    elif $e.imagenes == "digest" and ($i | contains("procesos-empresariales-")) and ($i | test("^ghcr\\.io/kerosene21/procesos-empresariales-(backend|frontend)@sha256:[0-9a-f]{64}$") | not) then "\($r) \($c): debe ser ghcr.io/kerosene21/procesos-empresariales-*@sha256:<digest> sin etiqueta: \($i)"
    elif $e.imagenes == "local" and ($i | contains("@sha256:") | not) and ($i | test("^procesos-empresariales-(backend|frontend):local$") | not) then "\($r) \($c): solo las imagenes locales del proyecto pueden ir sin digest: \($i)"
    else empty end;

def seguridad_pods:
  cargas | ref as $r | plantilla as $p | ($p.securityContext // {}) as $ps
  | (if $p.automountServiceAccountToken != false then "\($r): automountServiceAccountToken debe ser false" else empty end),
    (if $p.enableServiceLinks != false then "\($r): enableServiceLinks debe ser false" else empty end),
    (("hostNetwork", "hostPID", "hostIPC") as $h | select($p[$h] == true) | "\($r): \($h) no esta permitido"),
    (if (($ps.sysctls // []) | length) > 0 then "\($r): no se permiten sysctls" else empty end),
    (($p.volumes // [])[] | .name as $v | (keys - ["name"])[]
      | select(IN("configMap", "csi", "downwardAPI", "emptyDir", "ephemeral", "persistentVolumeClaim", "projected", "secret") | not)
      | "\($r): el volumen \($v) de tipo \(.) no cumple Pod Security restricted"),
    (($p.volumes // [])[] | select(.emptyDir != null and .emptyDir.sizeLimit == null) | "\($r): el emptyDir \(.name) necesita sizeLimit"),
    (contenedores | . as $k | ($k.securityContext // {}) as $cs
      | (if efectivo($cs; $ps; "runAsNonRoot") != true then "\($r) \($k.name): runAsNonRoot debe ser true" else empty end),
        (if efectivo($cs; $ps; "runAsUser") == 0 then "\($r) \($k.name): runAsUser 0 no esta permitido" else empty end),
        (if ((efectivo($cs; $ps; "seccompProfile") // {}) | .type // "" | IN("RuntimeDefault", "Localhost")) | not then "\($r) \($k.name): seccompProfile debe ser RuntimeDefault" else empty end),
        (if $cs.allowPrivilegeEscalation != false then "\($r) \($k.name): allowPrivilegeEscalation debe ser false" else empty end),
        (if $cs.privileged == true then "\($r) \($k.name): privileged no esta permitido" else empty end),
        (if (($cs.capabilities.drop // []) | index("ALL")) == null then "\($r) \($k.name): debe eliminar todas las capacidades (drop ALL)" else empty end),
        (($cs.capabilities.add // [])[] | select(. != "NET_BIND_SERVICE") | "\($r) \($k.name): la capacidad \(.) no esta permitida"),
        (if $cs.readOnlyRootFilesystem != true then "\($r) \($k.name): readOnlyRootFilesystem debe ser true" else empty end),
        (if ($cs.procMount // "Default") != "Default" then "\($r) \($k.name): procMount debe ser Default" else empty end),
        (($k.ports // [])[] | select(.hostPort != null) | "\($r) \($k.name): hostPort no esta permitido"));

def sondas:
  cargas | ref as $r
  | (principales | . as $k | [($k.ports // [])[].name] as $puertos
      | (("startupProbe", "readinessProbe", "livenessProbe") as $s | select($k[$s] == null) | "\($r) \($k.name): falta \($s)"),
        (("startupProbe", "readinessProbe", "livenessProbe") as $s | $k[$s] | select(. != null)
          | (.httpGet.port // .tcpSocket.port // .grpc.port // empty) | select(type == "string" and (IN($puertos[]) | not))
          | "\($r) \($k.name): \($s) usa el puerto \(.) que el contenedor no declara")),
    (iniciales | . as $k | ("startupProbe", "readinessProbe", "livenessProbe") as $s | select($k[$s] != null) | "\($r) \($k.name): un initContainer no admite \($s)");

def recursos:
  cargas | ref as $r | contenedores | . as $k | ("cpu", "memory") as $m
  | ($k.resources.requests[$m]) as $req | ($k.resources.limits[$m]) as $lim
  | (if $req == null then "\($r) \($k.name): falta resources.requests.\($m)" else empty end),
    (if $lim == null then "\($r) \($k.name): falta resources.limits.\($m)" else empty end),
    (($req, $lim) | select(. != null and (cantidad_valida | not)) | "\($r) \($k.name): cantidad no valida en \($m): \(.)"),
    (if $req != null and $lim != null and ($req | cantidad_valida) and ($lim | cantidad_valida) and (($req | cantidad) > ($lim | cantidad)) then "\($r) \($k.name): requests.\($m) supera limits.\($m)" else empty end);

def credenciales:
  cargas | ref as $r | plantilla as $p
  | (contenedores | . as $k
      | (($k.env // [])[] | select(.name | test("PASSWORD|SECRET|TOKEN|PRIVATE|CREDENTIAL|API_KEY"; "i")) | select(.valueFrom.secretKeyRef == null)
          | "\($r) \($k.name): \(.name) debe leerse de un Secret con secretKeyRef"),
        (($k.env // [])[] | .valueFrom.secretKeyRef // empty | . as $s
          | if ($secretos[$s.name] // null) == null then "\($r) \($k.name): el Secret \($s.name) no lo crea k8s/scripts/crear-secretos.sh"
            elif ($secretos[$s.name] | index($s.key)) == null then "\($r) \($k.name): crear-secretos.sh no crea la clave \($s.key) en el Secret \($s.name)"
            elif $s.optional == true then "\($r) \($k.name): la referencia al Secret \($s.name) no puede ser opcional"
            else empty end),
        (($k.envFrom // [])[] | .secretRef // empty | select(($secretos[.name] // null) == null) | "\($r) \($k.name): el Secret \(.name) no lo crea k8s/scripts/crear-secretos.sh")),
    (($p.volumes // [])[] | .secret // empty | select(($secretos[.secretName] // null) == null) | "\($r): el Secret \(.secretName) no lo crea k8s/scripts/crear-secretos.sh");

def configmaps:
  expectativas as $e
  | ([.[] | select(.kind == "ConfigMap") | {key: .metadata.name, value: (((.data // {}) + (.binaryData // {})) | keys)}] | from_entries) as $cm
  | (cargas | ref as $r | plantilla as $p
      | (contenedores | . as $k
          | (($k.envFrom // [])[] | .configMapRef // empty | select($cm[.name] == null) | "\($r) \($k.name): el ConfigMap \(.name) no existe en el renderizado"),
            (($k.env // [])[] | .valueFrom.configMapKeyRef // empty | . as $c | select((($cm[$c.name] // []) | index($c.key)) == null)
              | "\($r) \($k.name): el ConfigMap \($c.name) no tiene la clave \($c.key)")),
        (($p.volumes // [])[] | .configMap // empty | select($cm[.name] == null) | "\($r): el ConfigMap \(.name) del volumen no existe")),
    (.[] | select(.kind == "ConfigMap") | ref as $r | (.data // {} | keys[]) | select(test("PASSWORD|SECRET|TOKEN|PRIVATE|CREDENTIAL|API_KEY"; "i"))
      | "\($r): la clave \(.) parece un secreto y no puede ir en un ConfigMap"),
    ([.[] | select(.kind == "ConfigMap" and (.metadata.name | startswith("procesos-configuracion-")))] as $conf
      | if ($conf | length) != 1 then "debe existir un unico ConfigMap procesos-configuracion"
        else ($e.configuracion | to_entries[] | select($conf[0].data[.key] != .value)
          | "ConfigMap procesos-configuracion: \(.key)=\($conf[0].data[.key] // "sin definir"), se esperaba \(.value)") end);

def services:
  [cargas | {etiquetas: (.spec.template.metadata.labels // {}), puertos: [(plantilla | .containers // [])[] | (.ports // [])[] | .name]}] as $cargas
  | .[] | select(.kind == "Service") | ref as $r | . as $s
  | (if (.spec.type // "ClusterIP") != "ClusterIP" then "\($r): el tipo \(.spec.type) no esta permitido; la entrada publica es el Ingress" else empty end),
    (if ((.spec.externalIPs // []) | length) > 0 then "\($r): externalIPs no esta permitido" else empty end),
    ([$cargas[] | . as $c | select(($s.spec.selector // {}) | coincide($c.etiquetas))] as $destinos
      | (if ($destinos | length) == 0 then "\($r): el selector no coincide con ningun Deployment ni StatefulSet" else empty end),
        ($s.spec.ports[]? | .targetPort | select(type == "string") | . as $tp | select(any($destinos[]; (.puertos | index($tp)) != null) | not)
          | "\($r): el targetPort \($tp) no existe en los contenedores seleccionados"));

def deployments:
  (.[] | select(.kind == "Deployment") | ref as $r
    | (if (.spec.replicas // 1) < 2 then "\($r): necesita al menos 2 replicas" else empty end),
      (if .spec.strategy.type != "RollingUpdate" then "\($r): la estrategia debe ser RollingUpdate" else empty end),
      (if (.spec.strategy.rollingUpdate.maxUnavailable | tostring) != "0" then "\($r): maxUnavailable debe ser 0" else empty end),
      (if (.spec.revisionHistoryLimit // 10) < 1 then "\($r): revisionHistoryLimit debe conservar revisiones para el rollback" else empty end)),
  (cargas | ref as $r | (.spec.template.metadata.labels // {}) as $l | select(((.spec.selector.matchLabels // {}) | coincide($l)) | not)
    | "\($r): spec.selector no coincide con las etiquetas de la plantilla");

def postgresql:
  expectativas as $e
  | . as $todo
  | .[] | select(.kind == "StatefulSet") | ref as $r | . as $sts
  | (if .spec.replicas != 1 then "\($r): PostgreSQL debe tener exactamente 1 replica" else empty end),
    (if ([$todo[] | select(.kind == "Service" and .metadata.name == $sts.spec.serviceName and .spec.clusterIP == "None")] | length) == 0 then "\($r): serviceName debe apuntar a un Service headless" else empty end),
    (if ((.spec.volumeClaimTemplates // []) | length) == 0 then "\($r): sin volumeClaimTemplates los datos no persisten" else empty end),
    ((.spec.volumeClaimTemplates // [])[] | . as $v
      | (if $v.spec.accessModes != ["ReadWriteOnce"] then "\($r): el volumen \($v.metadata.name) debe ser ReadWriteOnce" else empty end),
        (if ([$sts.spec.template.spec.containers[] | (.volumeMounts // [])[] | .name] | index($v.metadata.name)) == null then "\($r): el volumen \($v.metadata.name) no esta montado" else empty end),
        (if $e.almacenamiento != null and $v.spec.storageClassName != $e.almacenamiento.clase then "\($r): storageClassName debe ser \($e.almacenamiento.clase)" else empty end),
        (if $e.almacenamiento != null and $v.spec.resources.requests.storage != $e.almacenamiento.tamano then "\($r): el volumen debe pedir \($e.almacenamiento.tamano)" else empty end)),
    (if $e.almacenamiento != null and .spec.persistentVolumeClaimRetentionPolicy != {"whenDeleted": "Retain", "whenScaled": "Retain"} then "\($r): persistentVolumeClaimRetentionPolicy debe conservar el PVC" else empty end),
    (if $e.almacenamiento != null and .spec.template.spec.securityContext.fsGroup != null then "\($r): con el PV local no se usa fsGroup" else empty end);

def network_policies:
  expectativas as $e
  | [.[] | select(.kind == "NetworkPolicy")] as $pol
  | [cargas | (.spec.template.metadata.labels // {})] as $etiquetas
  | (if (any($pol[]; .metadata.name == "denegar-todo" and .spec.podSelector == {} and ((.spec.policyTypes // []) | index("Ingress") != null and index("Egress") != null) and .spec.ingress == null and .spec.egress == null) | not) then "falta la NetworkPolicy denegar-todo con podSelector vacio y policyTypes Ingress y Egress" else empty end),
    ($pol[] | select(.spec.podSelector != {}) | ref as $r | . as $n
      | (if (any($etiquetas[]; . as $l | ($n.spec.podSelector.matchLabels // {}) | coincide($l)) | not) then "\($r): el podSelector no selecciona ningun pod" else empty end),
        (if .spec.ingress != null and ((.spec.policyTypes // []) | index("Ingress")) == null then "\($r): tiene reglas ingress sin Ingress en policyTypes" else empty end),
        (if .spec.egress != null and ((.spec.policyTypes // []) | index("Egress")) == null then "\($r): tiene reglas egress sin Egress en policyTypes" else empty end)),
    ($etiquetas[] | . as $l | select(any($pol[]; .spec.podSelector != {} and ((.spec.podSelector.matchLabels // {}) | coincide($l))) | not)
      | "los pods \($l | etiquetas_texto) no tienen una NetworkPolicy propia"),
    (([$pol[] | select(.spec.podSelector != {}) | ((.spec.podSelector.matchLabels // {}) | etiquetas_texto) as $sel
        | (((.spec.ingress // [])[] | {d: "ingress", pares: (.from // [{}]), puertos: (.ports // [{}])}),
           ((.spec.egress // [])[] | {d: "egress", pares: (.to // [{}]), puertos: (.ports // [{}])}))
        | .d as $d | .puertos[] as $p | .pares[]
        | "\($sel) \($d) \(par($e.traefik)) \($p.port // "todos")/\($p.protocol // "TCP")"] | unique) as $flujos
      | (($flujos - flujos_esperados)[] | "flujo no autorizado: \(.)"),
        ((flujos_esperados - $flujos)[] | "falta el flujo: \(.)"));

def ingress:
  expectativas as $e
  | ([.[] | select(.kind == "Service") | {key: .metadata.name, value: [(.spec.ports // [])[] | .name]}] | from_entries) as $svc
  | ([.[] | select(.kind == "Middleware") | "\(.metadata.namespace)-\(.metadata.name)@kubernetescrd"]) as $mw
  | ([.[] | select(.kind == "Ingress") | (.metadata.annotations // {})["traefik.ingress.kubernetes.io/router.middlewares"] // ""
      | split(",")[] | gsub("^\\s+|\\s+$"; "") | select(length > 0)] | unique) as $usados
  | (if $e.middleware and ($mw | length) == 0 then "falta el Middleware de limite de autenticacion" else empty end),
    (if ($e.middleware | not) and ($mw | length) > 0 then "este overlay no incluye Middlewares" else empty end),
    (($mw - $usados)[] | "el Middleware \(.) no lo usa ningun Ingress"),
    (($usados - $mw)[] | "un Ingress usa el Middleware \(.) que no existe en el renderizado"),
    (.[] | select(.kind == "Middleware" and .spec.rateLimit != null) | ref as $r
      | select((.spec.rateLimit.average // 0) <= 0 or (.spec.rateLimit.burst // 0) <= 0) | "\($r): rateLimit necesita average y burst positivos"),
    (.[] | select(.kind == "Ingress") | ref as $r | (.metadata.annotations // {}) as $a
      | (if .spec.ingressClassName != "traefik" then "\($r): ingressClassName debe ser traefik" else empty end),
        (if $a["traefik.ingress.kubernetes.io/router.entrypoints"] != $e.entrada then "\($r): entrypoint \($a["traefik.ingress.kubernetes.io/router.entrypoints"] // "sin definir"), se esperaba \($e.entrada)" else empty end),
        (if $e.tls and $a["traefik.ingress.kubernetes.io/router.tls"] != "true" then "\($r): falta router.tls=true"
         elif ($e.tls | not) and $a["traefik.ingress.kubernetes.io/router.tls"] != null then "\($r): router.tls no corresponde a una entrada HTTP"
         else empty end),
        (if .spec.defaultBackend != null then "\($r): defaultBackend no esta permitido" else empty end),
        (.spec.rules[]?.http.paths[]? | . as $ruta
          | (if ($svc[$ruta.backend.service.name] // null) == null then "\($r): el Service \($ruta.backend.service.name) no existe"
             elif $ruta.backend.service.port.name != null and ($svc[$ruta.backend.service.name] | index($ruta.backend.service.port.name)) == null then "\($r): el Service \($ruta.backend.service.name) no tiene el puerto \($ruta.backend.service.port.name)"
             else empty end),
            (if (($ruta.path // "/") | test("^/(actuator|swagger|v3/api-docs|h2-console)")) then "\($r): la ruta \($ruta.path) no debe publicarse" else empty end)));

def almacenamiento:
  [.[] | select(.kind == "StorageClass")] as $sc | [.[] | select(.kind == "PersistentVolume")] as $pv
  | (if ($sc | length) != 1 or ($pv | length) != 1 then "se esperaba exactamente una StorageClass y un PersistentVolume" else empty end),
    ($sc[] | ref as $r
      | (if .provisioner != "kubernetes.io/no-provisioner" then "\($r): el aprovisionador debe ser kubernetes.io/no-provisioner" else empty end),
        (if .reclaimPolicy != "Retain" then "\($r): reclaimPolicy debe ser Retain" else empty end),
        (if .volumeBindingMode != "WaitForFirstConsumer" then "\($r): volumeBindingMode debe ser WaitForFirstConsumer" else empty end),
        (if (.metadata.annotations // {})["storageclass.kubernetes.io/is-default-class"] == "true" then "\($r): no debe ser la StorageClass por defecto" else empty end)),
    ($pv[] | ref as $r
      | (if .spec.persistentVolumeReclaimPolicy != "Retain" then "\($r): persistentVolumeReclaimPolicy debe ser Retain" else empty end),
        (if .spec.storageClassName != ($sc[0].metadata.name // null) then "\($r): storageClassName debe ser la StorageClass del overlay" else empty end),
        (if .spec.accessModes != ["ReadWriteOnce"] then "\($r): accessModes debe ser ReadWriteOnce" else empty end),
        (if .spec.hostPath != null then "\($r): hostPath no esta permitido; usa local con nodeAffinity" else empty end),
        (if (((.spec.local.path // "") | startswith("/var/lib/procesos-empresariales/")) | not) then "\($r): local.path debe estar bajo /var/lib/procesos-empresariales/" else empty end),
        (if ([.spec.nodeAffinity.required.nodeSelectorTerms[]?.matchExpressions[]? | select(.key == "kubernetes.io/hostname" and .operator == "In" and (.values | length) == 1)] | length) != 1 then "\($r): nodeAffinity debe fijar un unico nodo por kubernetes.io/hostname" else empty end),
        (if .spec.claimRef.kind != "PersistentVolumeClaim" or .spec.claimRef.namespace != $namespace or (.spec.claimRef.name // "") == "" then "\($r): claimRef debe reservar el volumen para un PVC de \($namespace)" else empty end));

def volumen:
  ([.[0][] | select(.kind == "StatefulSet")][0]) as $sts
  | ([.[1][] | select(.kind == "PersistentVolume")][0]) as $pv
  | if $sts == null or $pv == null then "faltan el StatefulSet de PostgreSQL o el PersistentVolume"
    else ($sts.spec.volumeClaimTemplates[0]) as $v | "\($v.metadata.name)-\($sts.metadata.name)-0" as $pvc
      | (if $pv.spec.claimRef.name != $pvc or $pv.spec.claimRef.namespace != $sts.metadata.namespace then "el PV reserva \($pv.spec.claimRef.namespace)/\($pv.spec.claimRef.name), pero el StatefulSet crea \($sts.metadata.namespace)/\($pvc)" else empty end),
        (if $pv.spec.storageClassName != $v.spec.storageClassName then "el PV usa la clase \($pv.spec.storageClassName) y el PVC \($v.spec.storageClassName)" else empty end),
        (if ($pv.spec.capacity.storage | cantidad) < ($v.spec.resources.requests.storage | cantidad) then "el PV ofrece \($pv.spec.capacity.storage) y el PVC pide \($v.spec.resources.requests.storage)" else empty end),
        (if (($v.spec.accessModes - $pv.spec.accessModes) | length) > 0 then "los accessModes del PVC no los ofrece el PV" else empty end)
    end;

def normalizar_vm:
  map(if .kind == "Ingress" then .metadata.annotations |= del(.["traefik.ingress.kubernetes.io/router.entrypoints"], .["traefik.ingress.kubernetes.io/router.tls"])
      elif .kind == "ConfigMap" and (.metadata.name | startswith("procesos-configuracion-")) then .data |= del(.COOKIE_SEGURA)
      else . end)
  | walk(if type == "string" then sub("^(?<base>procesos-configuracion|procesos-postgres-inicio)-[a-z0-9]{10}$"; "\(.base)") else . end)
  | map({key: "\(.kind)/\(.metadata.name)", value: .}) | from_entries;

def vm_y_vm_http:
  (.[0] | normalizar_vm) as $x | (.[1] | normalizar_vm) as $y
  | ((($x | keys) - ($y | keys))[] | "\(.) solo existe en vm"),
    ((($y | keys) - ($x | keys))[] | "\(.) solo existe en vm-http"),
    (($x | keys)[] as $k | select($y[$k] != null and $x[$k] != $y[$k]) | "\($k) difiere entre vm y vm-http fuera del entrypoint, TLS y COOKIE_SEGURA");

def nombres_base: [.[] | "\(.kind)/\(if .kind == "ConfigMap" then (.metadata.name | sub("-[a-z0-9]{10}$"; "")) else .metadata.name end)"];

def local_en_vm:
  ((.[0] | nombres_base) - (.[1] | nombres_base))[] | "\(.) existe en local pero no en vm";

if $comprobacion == "estructura" then estructura
elif $comprobacion == "tipos-aplicacion" then tipos(tipos_aplicacion)
elif $comprobacion == "tipos-almacenamiento" then tipos(tipos_almacenamiento)
elif $comprobacion == "namespace" then namespace_y_pod_security
elif $comprobacion == "imagenes" then imagenes
elif $comprobacion == "seguridad" then seguridad_pods
elif $comprobacion == "sondas" then sondas
elif $comprobacion == "recursos" then recursos
elif $comprobacion == "credenciales" then credenciales
elif $comprobacion == "configmaps" then configmaps
elif $comprobacion == "services" then services
elif $comprobacion == "deployments" then deployments
elif $comprobacion == "postgresql" then postgresql
elif $comprobacion == "network-policies" then network_policies
elif $comprobacion == "ingress" then ingress
elif $comprobacion == "almacenamiento" then almacenamiento
elif $comprobacion == "volumen" then volumen
elif $comprobacion == "vm-y-vm-http" then vm_y_vm_http
elif $comprobacion == "local-en-vm" then local_en_vm
else error("comprobacion desconocida: \($comprobacion)") end
