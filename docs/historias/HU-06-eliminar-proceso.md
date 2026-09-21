# HU-06 · Eliminación lógica de procesos

## Qué pide la historia

Como administrador de empresa, quiero eliminar un proceso que ya no aplica, sin perder su rastro ni
el de sus ediciones.

Criterios de aceptación:

- Se solicita **confirmación** antes de eliminar.
- La eliminación es **lógica**: el proceso pasa a estado inactivo y no se borra de la base de datos.
- Un proceso inactivo deja de aparecer en el listado por defecto, pero puede consultarse con un
  filtro.
- **Solo el administrador** de la empresa puede eliminar.
- La eliminación queda registrada en el historial.
- Ningún elemento se borra físicamente, para preservar la trazabilidad.

Además, y por coherencia con el resto del proyecto: un usuario nunca puede eliminar un proceso de
otra empresa.

> **El criterio del listado por defecto y su filtro se implementó en HU-07**, que es donde nació el
> listado de procesos. Todos los criterios de HU-06 están cubiertos; el detalle de ese punto está al
> final del documento.

## Diseño: qué se añadió al modelo

Un único campo booleano en `Proceso`:

```java
@Column(nullable = false, columnDefinition = "boolean default false")
private boolean eliminado;
```

**`eliminado = true` es la representación persistente del estado lógico «inactivo»** que pide la
historia. No se convirtió `EstadoProceso` en un tercer valor `INACTIVO` a propósito: `estado`
(`BORRADOR`/`PUBLICADO`) describe la madurez del documento y es un dato que el usuario edita;
«inactivo» es una dimensión distinta y ortogonal, que además debe conservarse junto al estado que
el proceso tenía al eliminarlo. Mezclarlas destruiría esa información.

**No se añadieron `fechaEliminacion` ni `eliminadoPor`.** El historial ya registra quién y cuándo
para cada operación, incluida la eliminación, así que duplicarlo en `Proceso` no aportaría
información nueva y habría que mantener los dos sitios sincronizados.

`columnDefinition = "boolean default false"` está puesto a propósito: hace que
`ddl-auto=update` pueda añadir la columna **aunque la tabla `proceso` ya tenga filas**, porque
PostgreSQL acepta `ADD COLUMN ... boolean default false not null` sobre una tabla no vacía. A
diferencia de lo que ocurrió con `password_hash` en HU-03, **esta migración no necesita
intervención manual** ni en desarrollo ni en la máquina virtual: los procesos existentes quedan
automáticamente como no eliminados, que es justo lo que corresponde.

## Flujo

La eliminación son **dos pasos**: primero se pide confirmación, después se ejecuta.

```
GET  /procesos/{id}/eliminar  → ProcesoController.confirmarEliminacion
                                 ├─ requiere rol ADMINISTRADOR (filtro)
                                 └─ confirmareliminacion.html
                                      NO cambia nada: solo lee el proceso y lo muestra

POST /procesos/{id}/eliminar  → ProcesoController.eliminar
                                 ├─ requiere rol ADMINISTRADOR (filtro) y token CSRF
                                 └─ ProcesoService.eliminar(id, username)
                                      ├─ usuarioAutenticado(username)
                                      ├─ validarRolAdministrador(usuario)
                                      │    └─ EDITOR o SOLO_LECTURA → UsuarioSinPermisoException
                                      ├─ procesoActivoDeLaEmpresa(id, usuario)
                                      │    ├─ no existe        → RecursoNoEncontradoException
                                      │    ├─ de otra empresa  → UsuarioSinPermisoException
                                      │    └─ ya eliminado     → RecursoNoEncontradoException
                                      ├─ proceso.setEliminado(true)
                                      ├─ ProcesoRepository.save(proceso)
                                      └─ HistorialProcesoRepository.save("proceso eliminado")
                                 → redirect a /procesos/{id}

DELETE /api/procesos/{id}     → ProcesoRestController.eliminar
                                 └─ mismo servicio → 204 No Content
```

`eliminar` es `@Transactional`: la marca y su entrada de historial se confirman juntas o no se
confirma ninguna.

## Confirmación antes de eliminar

El criterio se cumple con una **vista Thymeleaf real y verificable**, no con un `confirm()` de
JavaScript: así queda cubierto por pruebas y funciona sin JavaScript.

`GET /procesos/{id}/eliminar` muestra `procesos/confirmareliminacion.html`, que contiene:

- el **nombre** del proceso, su categoría y su estado actual;
- la **advertencia** de que pasará a estado inactivo, dejará de aparecer en los listados y no se
  borrará de la base de datos, conservando diagrama, empresa e historial;
- el botón **Confirmar eliminación**, que envía el `POST` con el token CSRF;
- el enlace **Cancelar y volver al proceso**.

**El `GET` no elimina nada**: solo lee el proceso. Hay pruebas que abren la confirmación y
comprueban después que `eliminado` sigue en `false` y que el historial no creció, tanto con mocks
como contra PostgreSQL.

En el detalle del proceso, «Eliminar proceso» es un **enlace** a esa página de confirmación, no un
botón que borre directamente, y solo se muestra a quien realmente puede eliminar
(`puedeEliminar`) y mientras el proceso siga activo.

## Nunca hay borrado físico

`ProcesoService` **no contiene ninguna llamada a `delete` ni a `deleteById`**, y hay pruebas que lo
verifican explícitamente tanto con mocks como contra PostgreSQL. Lo único que cambia en la fila es
la columna `eliminado`.

Tampoco se rompe ninguna clave foránea:

- El `Pool` del proceso sigue existiendo y sigue enlazado. La cascada `CascadeType.ALL` +
  `orphanRemoval` de `Proceso.pool` **no se dispara**, porque el proceso no se borra ni se
  desasocia de su pool.
- Las entradas de `historial_proceso` siguen apuntando al proceso y a su usuario.
- La empresa propietaria no cambia.

## Permisos

**Solo el administrador de la empresa puede eliminar.** Eliminar es más restrictivo que crear o
editar:

| Rol | Crear | Editar | Eliminar |
|---|---|---|---|
| `ADMINISTRADOR` | ✅ | ✅ | ✅ |
| `EDITOR` | ✅ | ✅ | ❌ |
| `SOLO_LECTURA` | ❌ | ❌ | ❌ |

Como los permisos son distintos, la regla de eliminación **no reutiliza `validarRolDeEscritura`**:
tiene su propio guardián, `validarRolAdministrador`, con su propio mensaje («Solo un administrador
puede eliminar procesos»). `validarRolDeEscritura` sigue siendo de HU-04/HU-05 y no cambió, así que
un `EDITOR` conserva intactas sus capacidades de crear y editar.

Como en el resto del proyecto, la regla se aplica en dos capas:

| Capa | Mecanismo |
|---|---|
| Filtro | `GET` y `POST` sobre `/procesos/*/eliminar` con `hasRole("ADMINISTRADOR")` |
| Negocio | `ProcesoService.validarRolAdministrador` |

**La ruta `DELETE /api/procesos/{id}` no lleva regla de rol en el filtro**, por la misma razón ya
documentada en HU-04: si la llevara, el `accessDeniedPage` respondería HTML y rompería el contrato
JSON. El servicio aplica igualmente la regla, así que tanto un `EDITOR` como un `SOLO_LECTURA`
reciben `403 {"codigo":"USUARIO_SIN_PERMISO"}`.

El enlace de eliminar solo se muestra a quien puede usarlo (`puedeEliminar`), pero **eso es
cosmética**: hay pruebas que envían el `GET` de confirmación y el `POST` directamente como `EDITOR`
y como `SOLO_LECTURA`, y comprueban que el servidor los rechaza y que el proceso sigue activo.

## Aislamiento multiempresa

`eliminar` usa `procesoActivoDeLaEmpresa`, que se apoya en el `procesoDeLaEmpresa` que ya usaban
`obtener`, `editar` y `consultarHistorial`. La empresa **siempre** sale del usuario autenticado; no
existe ningún parámetro de empresa en la ruta ni en el cuerpo.

Un usuario de la empresa A que intente eliminar un proceso de la empresa B recibe
`UsuarioSinPermisoException` (`403`), y el proceso de B sigue intacto. Verificado en unitario y
contra PostgreSQL, por la web y por el servicio.

## Historial de la eliminación

La entrada que se guarda contiene:

| Campo | Valor |
|---|---|
| `proceso` | El proceso eliminado |
| `usuario` | Quien ejecutó la eliminación |
| `fecha` | `LocalDateTime.now()` |
| `estadoAnterior` | `BORRADOR` o `PUBLICADO`, el estado que tenía al eliminarlo |
| `cambiosRealizados` | `proceso eliminado` |

El resumen es deliberadamente explícito en lugar de seguir el formato `campo: 'a' -> 'b'` de las
ediciones: al leer la tabla de historial se distingue de un vistazo una eliminación de un cambio de
campo.

`editar` y `eliminar` comparten el método `registrarHistorial`, así que la forma de escribir en el
historial está en un solo sitio.

**El historial anterior no se toca.** Tras eliminar, las entradas previas siguen ahí y la nueva se
añade encima (el historial se ordena de más reciente a más antiguo).

## Comportamiento de un proceso eliminado

La regla es una sola y se aplica de forma consistente: **un proceso eliminado se puede leer, pero
no se puede escribir**.

| Operación | Comportamiento | Motivo |
|---|---|---|
| `GET /procesos/{id}` | ✅ `200`, con el aviso «Este proceso fue eliminado» | Auditoría |
| `GET /procesos/{id}/historial` | ✅ `200` | Auditoría: es el criterio de HU-05 |
| `GET /procesos/{id}/editar` | ❌ `404` | Escritura |
| `POST /procesos/{id}` (editar) | ❌ `404` «El proceso ya fue eliminado» | Escritura |
| `GET /procesos/{id}/eliminar` | ❌ `404` «El proceso ya fue eliminado» | Escritura |
| `POST /procesos/{id}/eliminar` | ❌ `404` «El proceso ya fue eliminado» | Escritura |
| `DELETE /api/procesos/{id}` | ❌ `404 RECURSO_NO_ENCONTRADO` | Escritura |

En el detalle, un proceso eliminado **no ofrece las acciones de Editar ni de Eliminar**, y muestra
un aviso. Por eso no se comporta como un proceso activo, aunque siga siendo consultable.

### Decisión: la segunda eliminación falla con 404, no es idempotente

Un `DELETE` estrictamente idempotente devolvería `204` también la segunda vez. Se eligió `404`
porque:

- el enunciado pide definir y probar un comportamiento explícito, no necesariamente idempotencia;
- en el flujo web da una señal útil («ya fue eliminado») en lugar de fingir que se hizo algo;
- es coherente con el resto de operaciones de escritura sobre un proceso eliminado.

Queda anotado como un punto revisable si más adelante se prioriza la semántica REST clásica.

**No se implementó restauración**: HU-06 no la pide.

## Decisión: el nombre de un proceso eliminado sigue reservado

La restricción `uk_proceso_empresa_nombre` es `(empresa_id, nombre)` y **no se modificó**.
Consecuencia: crear un proceso con el nombre de uno eliminado en la misma empresa falla con
`NombreProcesoDuplicadoException`.

Se eligió la **opción A (nombre reservado)** porque:

- No hay criterio oficial que pida reutilizar nombres.
- Es la opción que **no requiere ningún cambio** en la restricción y mantiene la integridad actual,
  que es lo que pedía el enunciado ante la ausencia de criterio.
- La opción B exigiría sustituir la restricción por un índice parcial
  (`UNIQUE ... WHERE NOT eliminado`). PostgreSQL lo soporta, pero `@UniqueConstraint` de JPA no lo
  expresa, así que habría que salirse de la generación de esquema de Hibernate y mantener DDL a
  mano, justo cuando el esquema de pruebas se recrea con `create-drop`.

Otras empresas **sí** pueden usar ese nombre: la restricción sigue siendo por empresa. Las dos caras
están fijadas con pruebas (`elNombreDeUnProcesoEliminadoSigueReservadoEnLaEmpresa` y
`otraEmpresaSiPuedeUsarElNombreDeUnProcesoEliminadoEnLaEmpresaVecina`).

Si el curso pidiera reutilizar nombres, el cambio sería: índice parcial en la base y filtrar por
`eliminado = false` en `existsByEmpresaIdAndNombreIgnoreCase`.

## Repository

**No se añadió ninguna consulta nueva.** HU-06 no la necesita:

- `eliminar` localiza el proceso con el `findById` que ya usaba `procesoDeLaEmpresa`.
- La comprobación de nombre duplicado sigue siendo `existsByEmpresaIdAndNombreIgnoreCase`, sin
  filtrar por `eliminado`, que es precisamente lo que mantiene el nombre reservado.

Añadir un buscador de procesos activos sin que nada lo llame habría sido código muerto y una prueba
artificial para cubrirlo. Lo que HU-07 necesita se explica abajo.

## Archivos principales

### Creados

| Archivo | Para qué sirve |
|---|---|
| `templates/procesos/historial.html` (HU-05) | Se reutiliza: el historial del eliminado se ve aquí |
| `docs/historias/HU-06-eliminar-proceso.md` | Este documento |

### Modificados

| Archivo | Cambio |
|---|---|
| `domain/Proceso.java` | Campo `eliminado` con valor por defecto en la columna |
| `dto/ProcesoRespuestaDto.java` | Expone `eliminado` para que la vista y la API lo vean |
| `service/ProcesoService.java` | `eliminar`, `obtenerParaEliminar`, `puedeEliminar`, `validarRolAdministrador`, `procesoActivoDeLaEmpresa` y `registrarHistorial` |
| `controller/ProcesoController.java` | `GET` de confirmación y `POST` de eliminación; `puedeEliminar` en el detalle |
| `controller/ProcesoRestController.java` | `DELETE /api/procesos/{id}` con `204` |
| `config/SecurityConfig.java` | `GET` y `POST` sobre `/procesos/*/eliminar` exigen rol `ADMINISTRADOR` |
| `templates/procesos/proceso.html` | Enlace a la confirmación y aviso de proceso eliminado |
| `templates/procesos/confirmareliminacion.html` | Página de confirmación (creada) |
| `static/css/procesos.css` | Estilos de `.acciones` y listas de definición |

## Tests

| Clase | Pruebas de HU-06 | Necesita PostgreSQL |
|---|---|---|
| `ProcesoServiceTest` | 24 | No |
| `SeguridadProcesosTest` | 9 | No |
| `ProcesoRestControllerTest` | 4 | No |
| `ProcesoControllerTest` | 5 | No |
| `SeguridadRutasTest` | 1 | No |
| `EliminacionProcesosIntegracionTest` | 20 | **Sí** |

### Criterios y su prueba

| Criterio | Prueba |
|---|---|
| Se pide confirmación y el `GET` no elimina | `laConfirmacionDeEliminacionMuestraElProcesoYNoEliminaNada`, `abrirLaConfirmacionYNoConfirmarDejaElProcesoIntacto` |
| Confirmar elimina | `unAdministradorConfirmaYEliminaDesdeLaWeb` |
| Solo `ADMINISTRADOR` abre la confirmación | `unAdministradorAbreLaPaginaDeConfirmacionSinEliminarNada`, `obtenerParaEliminarExigeRolAdministrador` |
| `EDITOR` no abre la confirmación ni elimina | `unEditorNoAbreLaPaginaDeConfirmacion`, `unEditorNoPuedeEliminarUnProceso`, `unEditorNoPuedeAbrirLaConfirmacionNiEliminarDesdeLaWeb`, `elServicioRechazaLaEliminacionDeUnEditorDeLaMismaEmpresa` |
| `EDITOR` sigue creando y editando | `unEditorSiguePudiendoCrearYEditarAunqueNoPuedaEliminar` |
| `ADMINISTRADOR` elimina | `unAdministradorEliminaUnProceso` |
| `SOLO_LECTURA` no elimina | `unUsuarioDeSoloLecturaNoPuedeEliminarUnProceso`, `unUsuarioDeSoloLecturaNoPuedeAbrirLaConfirmacionNiEliminarDesdeLaWeb` |
| La confirmación de un eliminado no se muestra | `obtenerParaEliminarRechazaUnProcesoYaEliminado`, `laConfirmacionDeUnProcesoYaEliminadoNoSeMuestra` |
| El proceso queda marcado y conserva sus datos | `eliminarMarcaElProcesoComoEliminadoSinTocarSusDatos` |
| El proceso sigue existiendo | `elProcesoEliminadoSigueEnLaTablaMarcadoComoEliminado` |
| No se llama a `delete` | `eliminarNuncaBorraFisicamenteElProceso` |
| Empresa A no elimina proceso de B | `eliminarRechazaUnProcesoDeOtraEmpresa`, `elServicioRechazaLaEliminacionCruzadaEntreEmpresas` |
| La eliminación queda en el historial | `eliminarRegistraLaEliminacionEnElHistorialConUsuarioFechaYEstadoAnterior` |
| El historial anterior no desaparece | `eliminarConservaElHistorialAnteriorYAgregaLaEntradaDeEliminacion` |
| Segunda eliminación definida | `eliminarDosVecesRechazaLaSegundaEliminacion`, `eliminarDosVecesRechazaLaSegundaYNoDuplicaElHistorial` |
| Un eliminado no se edita | `editarRechazaUnProcesoEliminado`, `unProcesoEliminadoYaNoSePuedeEditar` |
| Un eliminado sí se consulta | `obtenerDevuelveElProcesoEliminadoMarcadoComoTalParaPoderAuditarlo` |
| El historial del eliminado se consulta | `consultarHistorialSigueDisponibleParaUnProcesoEliminado` |
| Pool y empresa intactos | `eliminarUnProcesoNoArrastraSuPoolNiCambiaSuEmpresa` |
| CSRF sigue activo | `eliminarUnProcesoSinTokenCsrfSeRechaza`, `eliminarSinTokenCsrfSeRechazaAunqueSeaAdministrador` |
| Requiere sesión | `eliminarUnProcesoExigeSesion`, `laPaginaDeConfirmacionExigeSesion`, `laApiDeEliminacionDeProcesosResponde401EnJsonSinAutenticacion` |
| REST: `204` para el administrador, `403` para los demás | `eliminarDevuelveDoscientosCuatroSinCuerpo`, `laApiEliminaLogicamenteYDevuelveDoscientosCuatroParaElAdministrador`, `laApiRechazaLaEliminacionDeUnEditorConTrescientosTres`, `laApiRechazaLaEliminacionDeUnUsuarioDeSoloLecturaConTrescientosTres` |

## Cómo demostrarla

1. Levantar PostgreSQL, definir `DB_PASSWORD` en `.env` y arrancar con `./mvnw spring-boot:run`.
2. Registrar una empresa e iniciar sesión con su administrador.
3. Crear un proceso y editarlo una vez, para que tenga historial.
4. En el detalle del proceso, pulsar **Eliminar proceso**: se abre la **página de confirmación** con
   el nombre del proceso y la advertencia. **Todavía no se ha eliminado nada.**
5. Pulsar **Cancelar y volver al proceso** y comprobar que el proceso sigue activo y editable.
6. Volver a entrar y pulsar **Confirmar eliminación**: la página vuelve al detalle con el aviso de
   que quedó eliminado, y ya no ofrece Editar ni Eliminar.
7. Abrir **Ver historial de cambios**: siguen las ediciones anteriores y encima aparece
   `proceso eliminado`, con la fecha, el correo del usuario y el estado anterior.
8. Intentar crear otro proceso con el mismo nombre: aparece el error de nombre duplicado, porque el
   nombre sigue reservado.
9. Ir a `/usuarios` y crear un usuario `EDITOR`. Entrar con él y abrir el detalle de un proceso
   activo: puede **Editar**, pero **no aparece la opción de Eliminar**. Si se escribe a mano la URL
   `/procesos/{id}/eliminar`, la aplicación responde con la página de acceso denegado.
10. Crear también un usuario `SOLO_LECTURA`: puede ver el proceso y su historial, pero no aparece
    ninguna acción de escritura.
11. Comprobar en la base de datos que la fila sigue ahí:
    `SELECT id, nombre, eliminado FROM proceso;`

## Qué quedó pendiente

- **No hay restauración.** HU-06 no la pide. Un proceso eliminado lo está de forma definitiva desde
  la interfaz; revertirlo hoy requeriría tocar la base de datos.
- **El `DELETE` de la API no es idempotente** (ver la decisión de arriba).
- **La migración con `ddl-auto=update` no se pudo verificar en local** por no haber PostgreSQL
  disponible; el valor por defecto de la columna está pensado justo para que funcione sin
  intervención, y CI lo ejercita con `create-drop`.

## El listado y el filtro de inactivos: resueltos en HU-07

El criterio «un proceso inactivo deja de aparecer en el listado por defecto, pero puede consultarse
con un filtro» se implementó en [HU-07 · Consultar procesos](HU-07-consultar-procesos.md), que es
donde nació el listado. Con eso, **HU-06 queda completa también en ese punto**:

1. **El listado por defecto excluye `eliminado = true`.** El filtro de situación usa
   `VisibilidadProceso.ACTIVOS` por defecto, y el servicio lo normaliza a ese valor incluso si llega
   vacío o manipulado desde la URL.
2. **Hay filtro para consultar los inactivos**: `visibilidad=INACTIVOS`, y `TODOS` para ver ambos.

Ambas reglas se resuelven **desde la consulta**, nunca trayendo todo y filtrando en Java. La
implementación final no usó un método derivado sino `ProcesoSpecifications`, que compone los filtros
opcionales sin generar predicados para los ausentes; el motivo está explicado en la documentación de
HU-07.

Lo que HU-06 dejó preparado y HU-07 aprovechó:

- La columna `eliminado`, con valor en todas las filas y consultable desde JPA.
- La definición de «activo» en un único lugar del código:
  `ProcesoService.procesoActivoDeLaEmpresa`, que distingue un proceso utilizable de uno eliminado.
