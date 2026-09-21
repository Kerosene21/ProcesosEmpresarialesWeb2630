# HU-05 · Editar proceso

## Qué pide la historia

Como usuario con permisos, quiero editar un proceso para mantenerlo actualizado.

Criterios de aceptación:

- Solo los roles `ADMINISTRADOR` y `EDITOR` pueden modificar.
- Se pueden modificar nombre, descripción, categoría y estado.
- Cada cambio genera una entrada de historial.
- El historial guarda el usuario que hizo el cambio y la fecha.
- Un usuario `SOLO_LECTURA` puede consultar pero no editar.
- Se aplican las mismas validaciones básicas de la creación.

## Qué implementamos

La vertical de edición ya existía en el repositorio (commit `fc03261`). El primer bloque no modificó
código de producción: solo añadió pruebas de caracterización. El bloque de cierre de HU-04/HU-05
**sí lo modificó**, para tapar dos huecos reales: el historial era demasiado grueso y no se podía
consultar. Se detalla al final del documento.

La ruta completa, ya con los cambios aplicados, es:

```
GET  /procesos/{id}        → ProcesoController.ver
                              └─ proceso.html con el atributo puedeEditar

GET  /procesos/{id}/historial → ProcesoController.historial
                              └─ historial.html (cualquier rol de la empresa)

GET  /procesos/{id}/editar → ProcesoController.editar
                              ├─ requiere rol ADMINISTRADOR o EDITOR
                              └─ formularioprocesoseditar.html precargado

POST /procesos/{id}        → ProcesoController.actualizar
                              ├─ requiere rol ADMINISTRADOR o EDITOR
                              ├─ BindingResult con errores → vuelve al formulario
                              └─ ProcesoService.editar(id, dto, username)
                                   ├─ UsuarioRepository.findByUsername(username)
                                   │    └─ si no existe → RecursoNoEncontradoException
                                   ├─ validarRolDeEscritura(usuario)
                                   │    └─ SOLO_LECTURA → UsuarioSinPermisoException
                                   ├─ ProcesoRepository.findById(id)
                                   │    └─ si no existe → RecursoNoEncontradoException
                                   ├─ proceso de otra empresa → UsuarioSinPermisoException
                                   ├─ si el nombre cambió y ya existe → NombreProcesoDuplicadoException
                                   ├─ construirCambios(proceso, dto)
                                   │    └─ si no cambió nada → devuelve el proceso sin guardar nada
                                   ├─ ProcesoRepository.save(proceso)
                                   └─ HistorialProcesoRepository.save(historial)
                              → redirect a /procesos/{id}
```

Existe además `PUT /api/procesos/{id}`, que responde `200` con el proceso actualizado, `401` sin
autenticación, `403` sin permisos, `404` si el proceso no existe y `409` si el nombre está ocupado.

## Reglas verificadas

- **Solo `ADMINISTRADOR` o `EDITOR` editan.** La regla está centralizada en `validarRolDeEscritura`
  y se comprueba **antes** de cargar el proceso, de modo que un usuario sin permisos no puede
  siquiera averiguar si un identificador existe. Desde el cierre de HU-04/HU-05 ese mismo método
  guarda también la creación.
- **`SOLO_LECTURA` consulta pero no edita.** `obtener` no aplica ninguna restricción de rol; `editar`
  la rechaza; y la vista recibe `puedeEditar` para ocultar la acción.
- **Aislamiento entre empresas.** Tanto `obtener` como `editar` comparan la empresa del proceso con
  la del usuario y lanzan `UsuarioSinPermisoException` si no coinciden. Conocer el identificador de
  un proceso ajeno no sirve de nada.
- **Se modifican nombre, descripción, categoría y estado.** El pool y la empresa no se tocan: no son
  editables desde esta pantalla.
- **Transición `BORRADOR` → `PUBLICADO`.** Es la edición del campo `estado`; el servicio no impone
  un orden de transición.
- **Unicidad del nombre al editar.** Solo se consulta el duplicado **si el nombre cambió**. Guardar
  el mismo nombre, o cambiar únicamente mayúsculas y minúsculas, no produce un falso duplicado.
- **Historial por cada edición que cambia algo.** Se guarda un `HistorialProceso` con el proceso, el
  usuario que lo modificó, la fecha (`LocalDateTime.now()`), el estado anterior y un resumen que
  lista **solo los campos modificados**. Una edición que no cambia ningún valor no genera entrada
  ni vuelve a guardar el proceso. Los valores anteriores se leen antes de sobrescribir los campos.
- **El historial se puede consultar.** `GET /procesos/{id}/historial` lo muestra de la edición más
  reciente a la más antigua, y está acotado a la empresa del usuario autenticado.
- **Todo o nada.** `editar` es `@Transactional`: el proceso y su entrada de historial se confirman
  juntos o no se confirma ninguno.
- **Mismas validaciones que la creación.** `EditarProcesoDto` repite las restricciones de
  `CrearProcesoDto` y añade `estado` obligatorio.

## Clases principales

| Clase | Papel |
|---|---|
| `dto/EditarProcesoDto.java` | Datos del formulario de edición y sus restricciones |
| `service/ProcesoService.java` | Permisos, aislamiento por empresa, unicidad e historial |
| `controller/ProcesoController.java` | Detalle, formulario de edición y actualización |
| `controller/ProcesoRestController.java` | Edición por API |
| `domain/HistorialProceso.java` | Entrada de historial: proceso, usuario, fecha, cambios y estado anterior |
| `domain/RolUsuario.java` | Roles que determinan el permiso de edición |
| `repository/HistorialProcesoRepository.java` | Persistencia del historial |

## Tests

### `ProcesoServiceTest` · permisos, consulta, edición e historial (27 pruebas)

| Prueba | Qué verifica |
|---|---|
| `puedeEditarAutorizaAlAdministrador` | Que el rol `ADMINISTRADOR` habilita la edición |
| `puedeEditarAutorizaAlEditor` | Que el rol `EDITOR` habilita la edición |
| `puedeEditarNiegaAlUsuarioDeSoloLectura` | Que `SOLO_LECTURA` no habilita la edición |
| `puedeEditarRechazaUnUsuarioAutenticadoQueNoExiste` | Que el rol se consulta contra un usuario real |
| `obtenerDevuelveElProcesoDeLaEmpresaDelUsuario` | Que la consulta devuelve los datos y el `poolId` |
| `obtenerEstaPermitidoParaUnUsuarioDeSoloLectura` | Que consultar no exige permiso de edición |
| `obtenerRechazaUnUsuarioAutenticadoQueNoExiste` | Que un username desconocido no consulta nada |
| `obtenerRechazaUnProcesoInexistente` | Que un id inexistente produce `RecursoNoEncontradoException` |
| `obtenerRechazaUnProcesoDeOtraEmpresa` | Que no se puede leer un proceso ajeno |
| `editarRechazaUnUsuarioAutenticadoQueNoExiste` | Que un username desconocido no edita nada |
| `editarPermiteAlAdministrador` | Que `ADMINISTRADOR` puede guardar |
| `editarPermiteAlEditor` | Que `EDITOR` puede guardar |
| `editarRechazaAlUsuarioDeSoloLectura` | Que `SOLO_LECTURA` no guarda ni proceso ni historial |
| `editarRechazaUnProcesoInexistente` | Que un id inexistente produce `RecursoNoEncontradoException` |
| `editarRechazaUnProcesoDeOtraEmpresa` | Que no se puede modificar un proceso ajeno |
| `editarActualizaNombreDescripcionYCategoria` | Que los tres campos de texto se escriben en la entidad |
| `editarLlevaElProcesoDeBorradorAPublicado` | Que el estado cambia y se refleja en la respuesta |
| `editarEliminaLosEspaciosSobrantesDelNombre` | Que el nombre se recorta antes de guardar |
| `editarRechazaUnNombreYaUsadoPorOtroProcesoDeLaEmpresa` | Que el duplicado bloquea proceso e historial |
| `editarConservaElMismoNombreSinReportarUnDuplicadoInexistente` | Que guardar el mismo nombre no consulta duplicados |
| `editarAdmiteCambiarSoloLasMayusculasDelNombre` | Que cambiar mayúsculas no se toma como duplicado |
| `editarDevuelveElProcesoActualizado` | Que la respuesta refleja los valores nuevos |
| `editarRegistraUnaEntradaDeHistorialPorCadaModificacion` | Que toda edición correcta genera historial |
| `editarAsociaElHistorialAlProcesoYAlUsuarioQueLoModifico` | Que el historial referencia proceso y usuario |
| `editarRegistraLaFechaDelCambioEnElHistorial` | Que la fecha queda dentro del instante de la operación |
| `editarGuardaElEstadoAnteriorEnElHistorial` | Que se conserva el estado previo al cambio |
| `editarResumeLosValoresAnterioresYNuevosEnElHistorial` | Que el resumen lleva los valores antes y después |

### `EditarProcesoDtoTest` (11 pruebas, Bean Validation)

Formulario válido en ambos estados; nombre, descripción, categoría y estado obligatorios; límites
exactos de 150 y 100 caracteres en ambos sentidos; y formulario vacío que señala exactamente los
cuatro campos.

### `ProcesoControllerTest` · consulta y edición (9 de 13 pruebas)

Redirección a `/login` sin usuario en el detalle y en el formulario; `puedeEditar` en el modelo
tanto en `true` como en `false`; retorno al detalle cuando el usuario no puede editar; precarga del
formulario con los datos actuales y la lista de estados; retorno al formulario con errores por campo
sin llamar al servicio; redirección a `/login` sin usuario; y actualización correcta que delega en el
servicio con el id de la ruta, deja el mensaje flash y redirige al detalle.

### `ProcesoRestControllerTest` · edición (5 de 10 pruebas)

`200` con el proceso actualizado; delegación con el id de la ruta y el usuario de la sesión; `401`
sin autenticación; `404` si el proceso no existe; y `403` sin permisos.

### `ApiExceptionHandlerTest` (6 pruebas)

Comprueban el código HTTP y el cuerpo de cada excepción de negocio: `409` para nombre de proceso
duplicado, NIT duplicado y correo en uso; `404` para recurso no encontrado; `401` para falta de
autenticación; y `403` para falta de permisos.

## Cómo demostrarla

Igual que HU-04, la demostración de extremo a extremo depende de HU-03. Hoy se demuestra con las
pruebas:

```bash
./mvnw -Dtest=ProcesoServiceTest,EditarProcesoDtoTest,ProcesoControllerTest,ProcesoRestControllerTest,ApiExceptionHandlerTest test
```

Y la cobertura del código ejercitado se ve en `target/site/jacoco/index.html` después de:

```bash
./mvnw verify
```

## Qué quedó pendiente

- **No hay autenticación (bloqueado por HU-03).** Sin `Principal` no se puede ejercitar la edición
  desde el navegador ni desde la API. La lógica de permisos está implementada y probada contra los
  tres roles, pero **HU-05 no se declara completa** hasta que exista inicio de sesión real.
- **El resumen del historial no distingue qué campo cambió.** `construirCambios` escribe siempre los
  cuatro campos y el literal `descripcion actualizada`, aunque solo se haya modificado uno. Además,
  guardar el formulario sin cambiar nada genera igualmente una entrada. El historial cumple el
  criterio de la historia (registra usuario, fecha y estado anterior por cada edición), pero el
  resumen es más grueso de lo deseable. Está documentado con la prueba
  `editarResumeLosValoresAnterioresYNuevosEnElHistorial`, que fija el formato actual; **no se
  modificó**, porque cambiarlo es una decisión funcional que no pide la historia.
- **No hay pantalla de historial.** Las entradas se guardan pero no existe una vista que las muestre.
- **`actualizar` valida el formulario antes de comprobar el usuario.** Una petición sin autenticar y
  con datos inválidos vuelve al formulario en lugar de redirigir a `/login`. Hoy es inocuo porque no
  hay sesión; conviene revisar el orden cuando se implemente HU-03.
- **Errores en JSON sobre vistas MVC.** Mismo pendiente global registrado en
  [HU-01](HU-01-registro-empresa.md) y [HU-04](HU-04-crear-proceso.md).
- **Sin restricción de transiciones de estado.** Se puede volver de `PUBLICADO` a `BORRADOR`. La
  historia no lo prohíbe, así que no se añadió la regla.

## Actualización tras el bloque de HU-03

Con Spring Security en marcha, tres de los pendientes de arriba quedaron resueltos:

- **La autenticación ya existe.** La edición se puede ejercitar desde el navegador y desde la API
  con un usuario real. Las reglas de rol siguen donde estaban, en `ProcesoService`.
- **Orden de validación en `actualizar`.** Ya no valida el formulario antes que la autenticación:
  el filtro de seguridad se ejecuta antes que el `DispatcherServlet`, de modo que una petición sin
  sesión con datos inválidos redirige al login. Lo cubre la prueba
  `actualizarUnProcesoExigeAutenticacionAntesDeValidarElFormulario` de `SeguridadRutasTest`. No se
  añadió una segunda comprobación en el controlador para no duplicar la lógica de seguridad.
- **Errores en JSON sobre vistas MVC.** `ApiExceptionHandler` se limitó a los `@RestController` y
  se añadió `MvcExceptionHandler` para las vistas.

El resto de pendientes (resumen del historial, pantalla de historial, transiciones de estado) sigue
igual. El detalle está en [HU-03 · Inicio de sesión](HU-03-inicio-sesion.md).

## Actualización tras el cierre de HU-04/HU-05

### Historial granular (hueco corregido)

Antes, `construirCambios` escribía **siempre los cuatro campos**, incluido el literal fijo
`descripcion actualizada`, aunque la descripción no hubiera cambiado. Además, guardar el formulario
sin tocar nada generaba igualmente una entrada de historial.

Ahora `construirCambios` compara valor anterior contra valor nuevo campo por campo y solo añade los
que realmente cambiaron:

| Edición | Resumen guardado |
|---|---|
| Solo el estado | `estado: 'BORRADOR' -> 'PUBLICADO'` |
| Solo el nombre | `nombre: 'Ventas' -> 'Ventas Corporativas'` |
| Solo la categoría | `categoria: 'Comercial' -> 'Operaciones'` |
| Nombre y estado | `nombre: 'Ventas' -> 'Ventas Corporativas'; estado: 'BORRADOR' -> 'PUBLICADO'` |
| Nada | *(no se crea entrada y no se vuelve a guardar el proceso)* |

La comparación del nombre se hace sobre el valor ya recortado, así que añadir espacios alrededor no
cuenta como cambio. Sí cuenta un cambio de mayúsculas, porque es un valor distinto.

`estadoAnterior` se sigue guardando siempre en cada entrada, aunque el estado no haya sido uno de
los campos modificados: es el estado en que se encontraba el proceso cuando se hizo esa edición.

### Consulta de historial (hueco corregido)

Antes el historial se escribía pero no había forma de leerlo. Ahora:

- `HistorialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(procesoId, empresaId)`
  ordena por fecha descendente y **acota la consulta a la empresa**.
- `ProcesoService.consultarHistorial(procesoId, username)` comprueba primero que el proceso sea de
  la empresa del usuario —así un proceso ajeno devuelve `UsuarioSinPermisoException` en vez de una
  lista vacía— y después consulta. Que el `empresaId` viaje también en la consulta es defensa en
  profundidad: aunque alguien quitara la comprobación previa, la consulta seguiría sin poder cruzar
  empresas.
- `HistorialProcesoRespuestaDto` expone únicamente fecha, correo del usuario, estado anterior y
  resumen de cambios. No expone identificadores internos ni datos sensibles.

### Vista de historial

`GET /procesos/{id}/historial` muestra una tabla con fecha, usuario, estado anterior y cambios, y se
enlaza desde el detalle del proceso. **Es accesible para cualquier rol de la empresa**, incluido
`SOLO_LECTURA`, porque consultar no es editar.

Esto **no es HU-07**: es solo la evidencia funcional del criterio de historial de HU-05. No hay
comparación entre versiones, ni restauración, ni paginación.

### Transiciones de estado: sin restricciones nuevas

Se revisaron los criterios oficiales de HU-04 y HU-05. HU-04 dice que «el estado inicial es
`BORRADOR` y luego puede pasar a `PUBLICADO`», y ninguna de las dos historias prohíbe volver de
`PUBLICADO` a `BORRADOR`.

Por eso **no se implementó ninguna máquina de estados**: volver a `BORRADOR` sigue siendo posible.
Si el curso quiere restringirlo, es una decisión funcional que debe pedirse explícitamente; el
punto donde se aplicaría es `ProcesoService.editar`, justo antes de `construirCambios`.

### Qué sigue pendiente de HU-05

- **El historial no se pagina.** Un proceso con muchas ediciones devuelve la lista completa.
- **No hay comparación ni restauración de versiones.** El historial es un registro de auditoría en
  texto, no un control de versiones.
- **No se registra quién creó el proceso.** El historial solo cubre ediciones; el alta no deja
  entrada.

## Actualización tras el bloque de HU-11 a HU-15

`editar` ganó una única comprobación nueva: **cuando la edición saca el proceso de `BORRADOR`**, se
valida antes el modelo del diagrama con `ValidacionModeloService.validarParaSalirDeBorrador`. Si
algún gateway activo incumple las reglas de HU-14 —una sola salida, salidas sin condición en un
`EXCLUSIVO`/`INCLUSIVO`, o condiciones en un `PARALELO`— se lanza
`ModeloDeProcesoNoValidoException` y **no se guarda nada ni se registra historial**.

Lo demás no cambió:

- editar sin tocar el estado, o volver de `PUBLICADO` a `BORRADOR`, **no valida** el modelo;
- sigue sin haber restricción de orden en las transiciones;
- el resto de reglas de esta historia (unicidad del nombre, historial granular, edición sin cambios)
  funcionan igual.

El detalle está en [HU-14 · Crear gateway](HU-14-crear-gateway.md#bloqueo-al-salir-de-borrador).
