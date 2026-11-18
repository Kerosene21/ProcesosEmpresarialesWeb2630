# Despliegue continuo asistido en MicroK8s

Arquitectura definitiva: **GitHub Actions + GHCR + descarga del paquete + VPN F5 + SCP/SSH + MicroK8s**.

GitHub Actions prueba, construye, publica y prepara un paquete firmado con la versión fijada en `main`. El operador
lo descarga en su equipo, se conecta a la universidad por la VPN F5 y lo aplica en NBIG132 con
`k8s/scripts/desplegar-vm.sh`. Ningún workflow se conecta a la VM, guarda credenciales de Kubernetes o despliega por
sí solo.

- No se exigen Required reviewers: preparar un paquete no depende de que otro integrante lo apruebe.
- El workflow de preparación solo se lanza a mano (`workflow_dispatch`) desde `main` y solo prepara su último commit.
- El environment `vm-universitaria` restringe la ejecución a la rama `main` y no admite omisiones de administradores.
- GitHub Actions prepara y verifica el paquete: imágenes de GHCR, migraciones, manifiestos, pruebas y firma.
- Actualizar MicroK8s exige que el operador descargue el paquete, conecte la VPN F5, entre por SSH y ejecute
  `desplegar-vm.sh --aplicar` de forma expresa.
- No se usan runners autohospedados.

```text
1. PR a main ──► CI en runners de GitHub: 7 checks obligatorios
   (pruebas, builds, SonarQube Cloud, manifiestos y pruebas del CD)
2. merge ──► imagenes.yml construye y publica las imágenes en GHCR (digest, SBOM y procedencia)
3. PR que fija los digests en k8s/overlays/vm ──► merge: Git registra la versión deseada
4. Preparar despliegue (workflow_dispatch manual con el SHA del último commit de main)
     autorizacion: rama main, commit == main y configuración de vm-universitaria verificada
     preparar: environment vm-universitaria (sin revisores) ─ nueva verificación ─ validación ─ pruebas
               ─ paquete ─ firma ─ artefacto
5. Equipo del operador: gh run download ─ gh attestation verify ─ sha256sum
6. VPN F5 de la universidad (conexión manual del operador)
7. scp del paquete y del script a NBIG132; ssh con la contraseña escrita por el operador
8. desplegar-vm.sh sin cambios ─ desplegar-vm.sh --aplicar ─ rollout, salud e informe
9. Rollback disponible: desplegar-vm.sh --revertir <registro> --aplicar
```

## Tres versiones distintas

| Versión | Dónde | Cómo se consulta |
| --- | --- | --- |
| Deseada | Digests de `k8s/overlays/vm/kustomization.yaml` en `main` | `preparar-paquete.sh` los lee y nunca los sustituye |
| Publicada | Imágenes de `ghcr.io/kerosene21/procesos-empresariales-*` | Existencia, `linux/amd64`, etiqueta `revision` y procedencia SLSA de cada digest |
| Desplegada | Deployments del namespace `procesos-empresariales` en NBIG132 | `desplegar-vm.sh` la muestra y la compara en modo comprobación |

Si `imagenes.yml` publicó imágenes más recientes para el commit (`sha-<commit>`), el resumen lo indica, pero el
paquete conserva los digests de Git: cambiar de versión exige un PR que actualice el overlay.

## Integración continua del CD

El job obligatorio `Validar manifiestos de Kubernetes` (`.github/workflows/kubernetes.yml`) se ejecuta en cada PR
hacia `main`, sin filtros de rutas ni Secrets. Después de las 64 comprobaciones de `validar-manifiestos.sh` ejecuta
las pruebas del CD y publica sus tablas en el resumen de la ejecución:

| Suite | Pruebas | Qué simula |
| --- | ---: | --- |
| `probar-desplegar-vm.sh` | 53 | `kubectl` y `curl` de la VM |
| `probar-preparar-paquete.sh` | 18 | GHCR y un repositorio Git temporal |
| `probar-verificar-entorno.sh` | 22 | La API de environments de GitHub |

Ninguna se conecta a la VM ni a un clúster. Las herramientas son las mismas versiones fijadas y verificadas con
SHA-256 que usa la validación.

## Preparación del paquete

`.github/workflows/preparar-despliegue.yml` se lanza a mano desde `main` con el SHA completo del último commit de
`main`. Rechaza otra rama, un SHA distinto y un `main` que avanzó mientras se ejecutaba.

`k8s/scripts/preparar-paquete.sh` comprueba, sin credenciales:

- que el código es exactamente el commit (sin cambios locales);
- que el overlay fija ambas imágenes por digest y con el repositorio de GHCR esperado;
- que cada digest existe en GHCR, devuelve ese mismo digest y tiene `linux/amd64`;
- las etiquetas OCI `revision`, `source` y `title`;
- la procedencia SLSA de BuildKit: repositorio, `imagenes.yml` en `main` o en una etiqueta `vX.Y.Z`, `github_workflow_sha` igual a la revisión y runner alojado por GitHub;
- que la revisión de la imagen pertenece a la historia del commit, y cuántos archivos de su contexto cambiaron después;
- las migraciones de Flyway frente a la versión anterior del backend registrada en Git. Una migración modificada o eliminada detiene la preparación.

El paquete `paquete-despliegue-<commit>.tar.gz` es reproducible (orden, propietario, permisos y fecha fijos) y solo
contiene:

| Archivo | Contenido |
| --- | --- |
| `metadatos.txt` | Claves fijas: commit, digests, revisiones, versión anterior, migraciones nuevas y `verificacion_entorno` |
| `deployments.yaml` | `procesos-backend` y `procesos-frontend` renderizados desde `k8s/overlays/vm`, solo para comparar |
| `migraciones.txt` | SHA-256, nombre y estado de cada migración |
| `RESUMEN.md` | Las tres versiones, migraciones y el environment verificado |
| `SHA256SUMS` | Suma de los cuatro archivos anteriores |

No incluye Secrets, credenciales, kubeconfig, el StatefulSet de PostgreSQL, PVC, PV, StorageClass ni recursos de
ámbito de clúster. El artefacto añade el `.sha256` del paquete y `desplegar-vm.sh` del mismo commit, ambos con
atestación de procedencia (`actions/attest-build-provenance`).

Los SHA-256 prueban la integridad; la procedencia la prueba `gh attestation verify`, que comprueba la firma del
workflow `preparar-despliegue.yml`. La procedencia de las imágenes en GHCR es metadato de BuildKit sin firma: confirma
coherencia, pero solo es tan fiable como el control de escritura sobre GHCR.

## Environment protegido sin aprobación humana

La preparación usa el environment `vm-universitaria`, configurado **sin Required reviewers**. El environment no
aprueba nada: restringe a `main` la rama desde la que puede ejecutarse el job que lo usa.
`k8s/scripts/verificar-entorno.sh` comprueba su configuración por la API de GitHub en el job `autorizacion`, antes de
que ningún job lo referencie, y otra vez al empezar el job `preparar`:

| Situación | Resultado |
| --- | --- |
| Falta la variable `ENTORNO_DESPLIEGUE` o tiene otro valor | Falla con un mensaje que indica cómo configurarla |
| El environment no existe | Falla; nunca se referencia, así que GitHub no lo crea sin reglas |
| La API falla o devuelve datos no válidos (otro environment, ramas ilegibles) | Falla |
| Permite que los administradores omitan las protecciones, o la respuesta no lo indica | Falla |
| Admite cualquier rama, las ramas protegidas, una rama distinta de `main`, otra rama además de `main`, un comodín, etiquetas o ninguna regla | Falla |
| Sin revisores, solo `main` y sin omisión de administradores | Correcto: el job `preparar` empieza sin esperar a nadie |
| Con revisores (no se exigen) | También correcto: GitHub pide su aprobación; aviso si permite la autoaprobación |

El CD distingue cuatro cosas:

- **Environment verificado**: la configuración anterior se comprobó. El paquete lo registra como
  `verificacion_entorno=environment:vm-universitaria`; no significa que alguien lo aprobara.
- **Preparación manual**: alguien con permiso de escritura lanza el workflow desde `main`. El resumen de la ejecución
  indica quién lo lanzó y el commit preparado.
- **Aprobación humana**: no se exige. Ni el paquete ni los registros de `desplegar-vm.sh` la mencionan, y el resumen
  de la ejecución indica «no exigida» mientras el environment no tenga revisores.
- **Aplicación en MicroK8s**: operación independiente que el operador realiza en NBIG132 por VPN F5 y SSH con
  `--aplicar`.

Sin revisores, cualquier persona con permiso de escritura puede preparar un paquete. Es aceptable porque preparar no
cambia nada en la VM: actualizarla exige la VPN F5, la contraseña SSH de NBIG132 y `--aplicar`.

Configuración manual del environment y de la variable:

1. Settings > Environments: `vm-universitaria`.
2. Required reviewers y Wait timer desactivados (Prevent self-review no aplica).
3. Allow administrators to bypass configured protection rules desactivado.
4. Deployment branches and tags: Selected branches and tags, con una única regla de tipo rama para `main`.
5. Sin secretos ni variables en el environment.
6. Settings > Secrets and variables > Actions > Variables: `ENTORNO_DESPLIEGUE` = `vm-universitaria`.

Cada preparación queda registrada en Actions y en Deployments de GitHub. Eso registra la versión preparada, no la
desplegada: la versión desplegada solo cambia cuando el operador ejecuta `--aplicar`.

## Aplicación en NBIG132

En el equipo del operador, con `gh` autenticado con su propia cuenta:

```bash
gh run download <ejecucion> -R Kerosene21/ProcesosEmpresarialesWeb2630 -n paquete-despliegue-<commit>
```

```bash
gh attestation verify paquete-despliegue-<commit>.tar.gz -R Kerosene21/ProcesosEmpresarialesWeb2630 --signer-workflow Kerosene21/ProcesosEmpresarialesWeb2630/.github/workflows/preparar-despliegue.yml
```

```bash
gh attestation verify desplegar-vm.sh -R Kerosene21/ProcesosEmpresarialesWeb2630 --signer-workflow Kerosene21/ProcesosEmpresarialesWeb2630/.github/workflows/preparar-despliegue.yml
```

```bash
sha256sum --check paquete-despliegue-<commit>.tar.gz.sha256
```

Con la VPN F5 conectada, se copian `paquete-despliegue-<commit>.tar.gz` y `desplegar-vm.sh` a NBIG132 con `scp` y se
entra por `ssh`; la contraseña la escribe el operador. En la VM, primero sin cambios:

```bash
bash desplegar-vm.sh --paquete paquete-despliegue-<commit>.tar.gz --sha256 <sha256 del resumen> --url https://127.0.0.1:<puerto de websecure>
```

y después, si la comprobación es correcta:

```bash
bash desplegar-vm.sh --paquete paquete-despliegue-<commit>.tar.gz --sha256 <sha256 del resumen> --url https://127.0.0.1:<puerto de websecure> --aplicar
```

`--url` solo acepta `127.0.0.1`, `localhost` o `[::1]`; `-k` se usa porque Traefik sirve su certificado por defecto.
`--sin-http` omite la comprobación HTTP de forma explícita.

### Comprobación previa

- Paquete: `.tar.gz` regular de hasta 1 MiB, SHA-256 igual al publicado, solo las seis entradas esperadas, sin enlaces ni rutas que escapen, `SHA256SUMS` exacto.
- Metadatos: solo claves conocidas, cada una con su expresión regular, sin duplicados ni CRLF. No se ejecuta ni se interpreta nada del paquete (sin `eval` ni `source`).
- `deployments.yaml`: solo los dos Deployments del namespace, con las imágenes de los metadatos.
- Clúster: namespace con Pod Security `restricted`, Deployments con los contenedores esperados (`esperar-postgres` y `backend`, `frontend`), estado de los rollouts y PostgreSQL listo.
- Cambios: imágenes desplegadas frente a solicitadas, `kubectl set image --dry-run=server` y `kubectl diff` informativo con las diferencias de configuración que la actualización no aplica.

### Aplicación controlada

Con `--aplicar` el script toma un bloqueo, registra las imágenes anteriores y ejecuta `kubectl set image` solo en los
contenedores de `procesos-backend` y `procesos-frontend`. Réplicas, probes, recursos y seguridad no cambian. Después
espera `rollout status` (420 s el backend y 180 s el frontend, ajustable con `--tiempo-maximo`) y comprueba los pods
con la nueva imagen, PostgreSQL listo, `GET /` (200) y `GET /api/auth/csrf` (204) con reintentos, y escribe un
informe.

Los paquetes del workflow siempre registran `verificacion_entorno=environment:vm-universitaria`. Un paquete preparado
fuera de Actions sin esa verificación exige `--sin-entorno-github`, que deja constancia en el informe. Por
compatibilidad se aceptan el nombre anterior `--sin-aprobacion-github`, la opción `--aprobacion` de
`preparar-paquete.sh` y la clave `aprobacion=` de paquetes anteriores, que se lee como `verificacion_entorno`.

Nunca modifica PostgreSQL, StatefulSet, PVC, PV, StorageClass, Secrets, NetworkPolicies, Ingress, Middleware,
Traefik, Calico ni la configuración global de MicroK8s, y no usa `apply`, `delete`, `--prune` ni `rollout restart`.

Códigos de salida: 0 correcto, 1 comprobación fallida sin cambios, 2 uso incorrecto, 3 fallo después de modificar
el clúster. Los registros quedan en `~/despliegues-procesos-empresariales/<fecha>-<commit>/` con permisos 700/600:
`imagenes-anteriores.txt`, `imagenes-solicitadas.txt`, `metadatos.txt` e `informe.txt`.

## Rollback

- Imágenes: `bash desplegar-vm.sh --revertir ~/despliegues-procesos-empresariales/<registro> --url ... [--aplicar]`. Funciona aunque el rollout fallido no haya terminado: vuelve a fijar los digests anteriores y espera el nuevo rollout. El registro se valida como un paquete (solo esos Deployments y contenedores, digests de GHCR).
- Con `maxUnavailable: 0`, un rollout fallido deja activas las réplicas anteriores mientras no termine.
- Alternativa: aplicar el paquete de la versión anterior. `kubectl rollout undo` queda como último recurso manual.
- Base de datos: revertir la imagen no revierte Flyway. Las migraciones deben ser compatibles con la versión anterior (expandir y contraer). Si una migración destructiva ya se aplicó, la única vuelta atrás es restaurar un respaldo, con pérdida de lo escrito después y solo con autorización.

## PostgreSQL y migraciones de Flyway

PostgreSQL no forma parte del paquete ni de la actualización: el StatefulSet, el PVC, el PV con `Retain`, la
StorageClass, los Secrets y los datos se conservan. El script solo comprueba que PostgreSQL está listo.

Si el backend cambia y la versión desplegada no es la anterior registrada en Git, o la nueva trae migraciones,
`--aplicar` exige `--migraciones-revisadas`. Antes de usarla:

1. Revisar que las migraciones nuevas son aditivas y compatibles con la versión anterior.
2. Hacer un respaldo con `pg_dump -Fc` del pod de PostgreSQL, calcular su SHA-256 y copiarlo fuera de la VM.
3. Probar la restauración en un contenedor aislado `postgres:16-alpine`, sin tocar la base de la VM.

El respaldo inicial que ya existe en la VM sigue pendiente de verificar, copiar fuera y restaurar en aislamiento.

## Credenciales

| Credencial | Dónde vive | Uso |
| --- | --- | --- |
| `GITHUB_TOKEN` | Generado por GitHub en cada ejecución | Lectura del repositorio y de la configuración del environment; `id-token` y `attestations` solo en el job que firma |
| Cuenta de GitHub del operador | `gh` en su equipo | Descargar el artefacto y verificar la atestación |
| Usuario de la VPN F5 | Cliente VPN del operador | Conexión manual a la red universitaria |
| Contraseña SSH de NBIG132 | Memoria del operador | `scp` y `ssh`; no se guarda en Git, en GitHub ni en scripts |
| Acceso a MicroK8s | Sesión SSH en NBIG132 (`microk8s kubectl`) | Solo durante la ejecución de `desplegar-vm.sh` |
| Secrets de la aplicación | Kubernetes, creados con `crear-secretos.sh` | Nunca se leen, imprimen ni modifican durante el despliegue |

No hay Secrets de GitHub, kubeconfig, claves SSH ni tokens de Kubernetes asociados al CD.

## Alternativas descartadas

Se evaluó ejecutar el despliegue con un runner autohospedado, en NBIG132 o en otro equipo con acceso por VPN, y
conectar los runners de GitHub por SSH. Se descartaron por la infraestructura disponible y por seguridad: el
repositorio es público, una cuenta personal no permite restringir runners a un workflow, NBIG132 compartiría
nodo con los trabajos y la VPN F5 exige credenciales personales. El CD asistido por SSH cubre el objetivo sin esas
exposiciones.

## Pruebas locales

```bash
bash k8s/scripts/pruebas/probar-desplegar-vm.sh
```

```bash
bash k8s/scripts/pruebas/probar-preparar-paquete.sh
```

```bash
bash k8s/scripts/pruebas/probar-verificar-entorno.sh
```

La primera solo necesita bash y coreutils; la segunda, `git`, `yq`, `jq` y `kubectl`; la tercera, `jq`.
