# HU-04 · Crear proceso

## Qué pide la historia

Como usuario de una empresa, quiero crear un proceso para empezar a documentarlo.

Criterios de aceptación:

- Se solicita nombre, descripción y categoría.
- El proceso queda asociado a la empresa del usuario.
- El estado inicial es `BORRADOR` y luego puede pasar a `PUBLICADO`.
- El nombre debe ser único dentro de una empresa.
- Empresas distintas pueden tener procesos con el mismo nombre.
- El proceso queda preparado con el pool de la empresa.

## Qué implementamos

La vertical de creación ya existía en el repositorio (commit `fc03261`). El primer bloque no
modificó código de producción: solo añadió pruebas de caracterización. El bloque de cierre de
HU-04/HU-05 **sí lo modificó**, para tapar dos huecos reales: la falta de comprobación de rol al
crear y la redirección posterior al alta. Ambos se detallan al final del documento.

La ruta completa, ya con los cambios aplicados, es:

```
GET  /procesos/nuevo  → formularioprocesos.html        (CrearProcesoDto vacío)
                         └─ requiere rol ADMINISTRADOR o EDITOR
POST /procesos        → ProcesoController.crear
                         ├─ requiere rol ADMINISTRADOR o EDITOR
                         ├─ BindingResult con errores → vuelve a formularioprocesos.html
                         └─ ProcesoService.crear(dto, username)
                              ├─ UsuarioRepository.findByUsername(username)
                              │    └─ si no existe → RecursoNoEncontradoException
                              ├─ validarRolDeEscritura(usuario)
                              │    └─ SOLO_LECTURA → UsuarioSinPermisoException
                              ├─ empresaId = usuario.getEmpresa().getId()
                              ├─ existsByEmpresaIdAndNombreIgnoreCase(empresaId, nombre.trim())
                              │    └─ si existe → NombreProcesoDuplicadoException
                              ├─ nombre recortado, estado BORRADOR, empresa del usuario
                              ├─ pool nuevo con el nombre de la empresa
                              └─ ProcesoRepository.save(proceso)   → PostgreSQL (cascade al pool)
                                   └─ DataIntegrityViolationException → NombreProcesoDuplicadoException
                         → redirect (Post/Redirect/Get) a /procesos/{id}
```

Existe además una ruta REST equivalente: `POST /api/procesos`, que responde `201 Created` con la
cabecera `Location` del proceso nuevo, `401` si no hay usuario autenticado y `403` si el usuario
no tiene rol de escritura.

## Reglas verificadas

- **Solo `ADMINISTRADOR` y `EDITOR` pueden crear.** `SOLO_LECTURA` recibe `UsuarioSinPermisoException`
  desde `ProcesoService` y `403` desde el `SecurityFilterChain`. Se detalla en la actualización del
  final de este documento.
- **La empresa sale del usuario autenticado, nunca del formulario.** El DTO de entrada solo lleva
  nombre, descripción y categoría; no existe forma de que el cliente elija otra empresa.
- **Nombre único por empresa, sin distinguir mayúsculas.** Se comprueba con
  `existsByEmpresaIdAndNombreIgnoreCase` acotado al `empresaId` del usuario.
- **Empresas distintas pueden repetir nombre.** La consulta está acotada por empresa y la
  restricción de tabla es `uk_proceso_empresa_nombre` sobre `(empresa_id, nombre)`.
- **Doble defensa ante duplicados.** Si dos peticiones simultáneas superan la verificación previa,
  la restricción de la tabla produce `DataIntegrityViolationException` y el servicio la traduce a
  `NombreProcesoDuplicadoException`, de modo que el cliente recibe siempre el mismo error de negocio.
- **Normalización del nombre.** Se recortan espacios al inicio y al final antes de comprobar el
  duplicado y antes de guardar, para que no se creen nombres que solo difieren en espacios.
- **Estado inicial `BORRADOR`.** Lo fija el servicio; no llega del formulario.
- **Pool inicial.** Se crea un `Pool` con el nombre de la empresa y se asocia al proceso. Se
  persiste junto al proceso por `CascadeType.ALL`.
- **Validación de entrada.** `nombre` obligatorio y hasta 150 caracteres, `descripcion` obligatoria
  sin límite, `categoria` obligatoria y hasta 100 caracteres.

## Clases principales

| Clase | Papel |
|---|---|
| `dto/CrearProcesoDto.java` | Datos del formulario y sus restricciones de validación |
| `dto/ProcesoRespuestaDto.java` | Salida hacia vistas y API; expone `poolId` en lugar del pool |
| `service/ProcesoService.java` | Regla de negocio: empresa del usuario, unicidad, estado inicial y pool |
| `controller/ProcesoController.java` | Rutas MVC del formulario y del alta |
| `controller/ProcesoRestController.java` | Alta por API con `201 Created` y cabecera `Location` |
| `repository/ProcesoRepository.java` | `existsByEmpresaIdAndNombreIgnoreCase` para la unicidad |
| `domain/Proceso.java` | Entidad y restricción `uk_proceso_empresa_nombre` |
| `domain/Pool.java` | Pool inicial del diagrama |

## Tests

Todos son unitarios: no requieren PostgreSQL ni contexto de Spring. Los de controller usan MockMvc
en modo *standalone* con el servicio mockeado.

### `ProcesoServiceTest` · creación (13 pruebas)

| Prueba | Qué verifica |
|---|---|
| `crearIdentificaAlUsuarioPorSuUsername` | Que la empresa se deduce del usuario autenticado |
| `crearRechazaUnUsuarioAutenticadoQueNoExiste` | Que un username desconocido no crea nada |
| `crearBuscaNombresDuplicadosUnicamenteEnLaEmpresaDelUsuario` | Que la consulta de unicidad va acotada por empresa |
| `crearRechazaUnNombreYaUsadoEnLaMismaEmpresa` | Que el duplicado lanza la excepción y no guarda |
| `crearGuardaElProcesoCuandoElNombreEstaDisponible` | Que el alta llega al repositorio |
| `crearAdmiteElMismoNombreEnUnaEmpresaDistinta` | Que el nombre ocupado en otra empresa no bloquea |
| `crearDejaElProcesoEnEstadoBorrador` | Que el estado inicial lo fija el servidor |
| `crearAsociaElProcesoALaEmpresaDelUsuarioAutenticado` | Que el dueño del proceso es la empresa del usuario |
| `crearPreparaElPoolInicialConElNombreDeLaEmpresa` | Que el pool se crea con el nombre de la empresa |
| `crearDevuelveElIdentificadorDelPoolAsociadoAlProceso` | Que la respuesta expone el `poolId` |
| `crearEliminaLosEspaciosSobrantesDelNombre` | Que se recorta antes de comparar y de guardar |
| `crearDevuelveLosDatosDelProcesoRegistrado` | Que la respuesta lleva id, nombre, descripción y categoría |
| `crearTraduceLaViolacionDeIntegridadEnNombreDuplicado` | Que el error de la tabla se convierte en error de negocio |

### `CrearProcesoDtoTest` (11 pruebas, Bean Validation)

Formulario completo sin errores; nombre, descripción y categoría obligatorios; nombre nulo;
límites exactos de 150 y 100 caracteres en ambos sentidos; descripción sin límite de longitud;
y formulario vacío que señala exactamente los tres campos.

### `ProcesoControllerTest` · creación (4 de 13 pruebas)

Apertura del formulario con un DTO vacío; retorno al formulario con errores por campo sin llamar
al servicio; redirección a `/login` sin usuario autenticado; y alta correcta que delega en el
servicio con los datos del formulario, deja el mensaje flash y redirige.

### `ProcesoRestControllerTest` · creación (5 de 10 pruebas)

`201 Created` con `Location` y cuerpo JSON; delegación con el usuario de la sesión; `401` sin
autenticación; `400` con cuerpo inválido; y `409` cuando el nombre está duplicado.

## Cómo demostrarla

1. Levantar PostgreSQL y definir `DB_PASSWORD` en `.env`.
2. Arrancar con `./mvnw spring-boot:run`.
3. Abrir `http://localhost:8080/procesos/nuevo`.
4. Enviar el formulario vacío: aparecen los tres mensajes de campo obligatorio.
5. Enviar un nombre de más de 150 caracteres: aparece el mensaje de longitud.

6. Registrar una empresa, iniciar sesión con su administrador y crear un proceso con datos
   correctos: la aplicación lleva al **detalle del proceso recién creado**, en estado `BORRADOR`.
7. Crear otro proceso con el mismo nombre: aparece el error de nombre duplicado.
8. Crear un usuario `SOLO_LECTURA` desde `/usuarios`, iniciar sesión con él y abrir
   `http://localhost:8080/procesos/nuevo`: aparece la página de acceso denegado.

```bash
./mvnw -Dtest=ProcesoServiceTest,CrearProcesoDtoTest,ProcesoControllerTest,ProcesoRestControllerTest,SeguridadProcesosTest test
```

## Qué quedó pendiente

- **No hay autenticación (bloqueado por HU-03).** `Principal` siempre llega nulo, así que
  `POST /procesos` redirige a `/login` y `POST /api/procesos` responde `401`. La lógica de creación
  está implementada y probada, pero **HU-04 no se puede ejercitar de extremo a extremo en la
  aplicación** hasta que exista inicio de sesión. Por eso no se declara completa.
- **No hay listado de procesos por empresa.** Existe el detalle `GET /procesos/{id}`, pero no una
  pantalla que muestre los procesos de la empresa del usuario.
- **Tras crear, la aplicación redirige de nuevo al formulario** (`/procesos/nuevo`) en lugar de al
  detalle del proceso creado. Funciona y evita reenviar el POST, pero conviene revisarlo cuando
  exista el listado.
- **Errores en JSON sobre vistas MVC.** `ApiExceptionHandler` es un `@RestControllerAdvice`, así
  que un `NombreProcesoDuplicadoException` lanzado desde la ruta MVC se responde en JSON y no como
  error dentro del formulario. Es el mismo pendiente ya registrado en
  [HU-01](HU-01-registro-empresa.md) y debe resolverse con una estrategia global de errores.
- **Unicidad a nivel de tabla sin verificar contra PostgreSQL.** La regla está probada en el
  servicio; la restricción `uk_proceso_empresa_nombre` se validará en la máquina virtual del curso.

## Actualización tras el bloque de HU-03

Con Spring Security en marcha, dos de los pendientes de arriba quedaron resueltos:

- **La autenticación ya existe.** `Principal` llega real a `ProcesoController` y a
  `ProcesoRestController`, así que HU-04 **sí se puede ejercitar de extremo a extremo**: registrar
  empresa → iniciar sesión → `GET /procesos/nuevo` → crear. Las comprobaciones manuales
  `principal == null → redirect:/login` se eliminaron: ahora la corta el `SecurityFilterChain`
  antes de llegar al controlador, y las pruebas de ese comportamiento viven en `SeguridadRutasTest`.
- **Errores en JSON sobre vistas MVC.** `ApiExceptionHandler` se limitó a los `@RestController` y
  se añadió `MvcExceptionHandler` para las vistas.

El resto de pendientes (listado de procesos por empresa, redirección tras crear, unicidad a nivel
de tabla) sigue igual. El detalle está en [HU-03 · Inicio de sesión](HU-03-inicio-sesion.md).

## Actualización tras el cierre de HU-04/HU-05

### Permiso de creación (hueco corregido)

`ProcesoService.crear` **no comprobaba el rol**: cualquier usuario autenticado, incluido un
`SOLO_LECTURA`, podía crear procesos. La regla ya existía para editar (`validarRolEditor`), así que
**se reutilizó en lugar de duplicarla**: el método pasó a llamarse `validarRolDeEscritura` —porque
ahora guarda creación y edición— y su mensaje cubre las dos operaciones.

La comprobación ocurre **antes** de buscar duplicados, de modo que un `SOLO_LECTURA` ni siquiera
provoca una consulta a la tabla de procesos.

La regla se aplica en dos capas:

| Capa | Mecanismo | Alcance |
|---|---|---|
| Filtro | `GET /procesos/nuevo`, `GET /procesos/*/editar`, `POST /procesos`, `POST /procesos/*` con `hasAnyRole("ADMINISTRADOR", "EDITOR")` | Rutas MVC |
| Negocio | `ProcesoService.validarRolDeEscritura` | Toda llamada al servicio, incluida la API REST |

**Las rutas `/api/**` no llevan regla de rol en el filtro a propósito.** Si la llevaran, el
`accessDeniedPage` respondería HTML y rompería el contrato JSON de la API. Como el servicio aplica
la misma regla, un `SOLO_LECTURA` que use la API sigue recibiendo
`403 {"codigo":"USUARIO_SIN_PERMISO"}`, que es lo que ya estaba documentado y probado.

`GET /procesos/{id}` y `GET /procesos/{id}/historial` **siguen abiertos a `SOLO_LECTURA`**: ese rol
consulta, no escribe.

### Redirección tras crear (hueco corregido)

Antes, crear un proceso redirigía de nuevo a `/procesos/nuevo` y el proceso recién creado quedaba
fuera de la vista. Ahora redirige a **`/procesos/{id}`**, el detalle del proceso creado, que ya
existía y funciona. Se mantiene el patrón Post/Redirect/Get, así que recargar no repite el alta.

### Qué sigue pendiente de HU-04

- **No hay listado de procesos por empresa.** Sigue sin existir una pantalla con todos los procesos
  de la empresa; se llega al detalle por el enlace que deja la creación o por URL directa.
- **Unicidad a nivel de tabla.** `uk_proceso_empresa_nombre` se ejercita en CI a través de las
  pruebas de integración, pero no hay una prueba dedicada que la fuerce con dos altas simultáneas.
