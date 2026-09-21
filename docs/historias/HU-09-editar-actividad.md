# HU-09 · Editar actividad

## Qué pide la historia

Como usuario con permisos de edición, quiero corregir una actividad del diagrama sin tener que
borrarla y volver a crearla.

Criterios de aceptación:

- Se modifica el **nombre**.
- Se modifica el **tipo**.
- Se modifica la **lane**.
- Cambiar de lane implica el **cambio visual de banda**.
- Los **arcos conectados se conservan**.
- Los cambios quedan **en el historial**.
- **Solo usuarios con permisos de edición** pueden modificar.

Por coherencia con el resto del proyecto: el nombre sigue siendo único dentro del proceso, una
actividad eliminada no se edita, y un proceso eliminado (HU-06) no admite cambios.

> **Todos los criterios están cumplidos.** El de los arcos quedó abierto en el bloque HU-08 a HU-10
> porque la entidad `Arco` no existía; con HU-11 existe y este bloque lo cierra con pruebas reales.
> Ver «Los arcos conectados se conservan».

## Qué se edita y qué no

```java
public class EditarActividadDto {
    private String nombre;       // obligatorio, máx. 150
    private TipoActividad tipo;  // obligatorio
    private Long laneId;         // obligatorio
}
```

**La posición no forma parte de esta historia.** Los criterios nombran tres campos y ninguno es la
posición; mover el rectángulo en el lienzo es otra acción, de las historias de modelado visual. Por
eso `editar` no toca `posicionX` ni `posicionY`, y hay una prueba que lo comprueba.

## Flujo

```
POST /procesos/{procesoId}/actividades/{actividadId}      → ActividadController.actualizar
PUT  /api/procesos/{procesoId}/actividades/{actividadId}  → ActividadRestController.editar → 200

                                 └─ ActividadService.editar(procesoId, actividadId, dto, username)
                                      ├─ usuarioAutenticado(username)
                                      ├─ validarRolDeEscritura(usuario)
                                      │    └─ SOLO_LECTURA → UsuarioSinPermisoException
                                      ├─ procesoActivoDeLaEmpresa(procesoId, usuario)
                                      │    ├─ no existe / eliminado → RecursoNoEncontradoException
                                      │    └─ de otra empresa       → UsuarioSinPermisoException
                                      ├─ actividadActivaDelProceso(actividadId, proceso)
                                      │    ├─ no es de este proceso → RecursoNoEncontradoException
                                      │    └─ ya eliminada          → RecursoNoEncontradoException
                                      ├─ nombre = dto.nombre.trim()
                                      │    └─ si cambia y ya existe → NombreActividadDuplicadoException
                                      ├─ laneDelProceso(laneId, proceso)
                                      │    └─ lane de otro pool     → LaneNoValidaException
                                      ├─ construirCambios(...)
                                      │    └─ vacío → sale sin guardar y sin historial
                                      ├─ nombre / tipo / lane
                                      ├─ ActividadRepository.save
                                      └─ HistorialProcesoRepository.save(resumen)
```

`editar` es `@Transactional`: el cambio y su entrada de historial se confirman juntos.

## Cambiar de lane es el cambio de banda

En el modelo, la banda **es** la lane: `Actividad.lane` es la única referencia que decide dentro de
qué banda se dibuja la actividad. Reasignarla es, por tanto, el cambio visual que pide el criterio,
y el detalle del proceso lo refleja solo, porque agrupa las actividades por `laneId` al renderizar.

La lane nueva se valida contra el **pool del proceso**, de modo que no se puede mover una actividad
a una banda de otro proceso ni de otra empresa.

> Mientras HU-22 no exista, cada proceso arranca con **una sola lane** (`General`), así que el
> desplegable del formulario muestra una única opción. El servicio, el formulario, el diagrama y las
> pruebas ya funcionan con varias lanes: `ActividadesIntegracionTest` crea una segunda lane en el
> pool y comprueba de extremo a extremo que la actividad cambia de banda y que el detalle la dibuja
> bajo la nueva. Cuando HU-22 añada la gestión de lanes, el cambio queda disponible en la interfaz
> sin tocar este bloque.

## Los arcos conectados se conservan

Editar **no toca la identidad de la actividad**: se modifica la fila existente, su `id` no cambia y
nunca se borra ni se recrea. Cualquier arco que la referencie como origen o destino seguirá
apuntando al mismo identificador después de cambiar su nombre, su tipo o su lane.

**Desde HU-11 existe la entidad `Arco`, y este criterio está verificado con arcos reales.** Un arco
referencia a la actividad por su tipo y su identificador (`origenTipo` + `origenId`,
`destinoTipo` + `destinoId`), así que una edición en sitio no lo afecta: no hay clave que se
invalide ni fila que se recree. `ActividadService.editar` **no llama a `ConexionesService`**, y hay
una prueba que lo comprueba con `verifyNoInteractions`.

Contra PostgreSQL se verifica lo mismo de extremo a extremo: una actividad con un arco entrante y
uno saliente se edita —nombre, tipo y lane— y los dos arcos siguen activos, con el mismo
identificador y los mismos extremos. **Cambiar de lane tampoco los toca**, porque la lane no
participa en la referencia del arco.

> `ActividadService.editar` no cambió ni una línea para cerrar este criterio: el diseño de HU-08 a
> HU-10 ya lo cumplía por construcción, y lo que faltaba era poder demostrarlo.

## El historial registra solo lo que cambió

`construirCambios` compara campo a campo y devuelve únicamente los modificados, igual que hace
`ProcesoService.editar` desde HU-05:

```
actividad 'Revisar solicitud': lane: 'General' -> 'Cartera'
actividad 'Revisar solicitud': nombre: 'Revisar solicitud' -> 'Validar solicitud'; tipo: 'TAREA_USUARIO' -> 'TAREA_SISTEMA'; lane: 'General' -> 'Cartera'
```

El resumen se construye **con el nombre anterior** de la actividad, que es como se la conocía hasta
ese momento, y luego se aplican los cambios.

**Si no cambia nada, no se guarda ni se registra historial.** `editar` devuelve el DTO actual y sale
sin llamar a `save`. Hay pruebas con mocks y contra PostgreSQL que lo verifican.

## Unicidad del nombre

Sigue siendo única dentro del proceso. La comprobación se salta cuando el nombre no cambia
(comparación insensible a mayúsculas contra el nombre actual), para que una actividad no choque
consigo misma; en cualquier otro caso se consulta `existsByProcesoIdAndNombreIgnoreCase`. Es el
mismo criterio que usa la edición de procesos en HU-05.

## Una actividad eliminada no se edita

`actividadActivaDelProceso` rechaza con `RecursoNoEncontradoException` cualquier actividad con
`activo = false`. Una actividad eliminada es consultable en el historial, pero **no vuelve a
editarse**, igual que un proceso eliminado en HU-06.

## Permisos

| Rol | Editar actividad |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ✅ |
| `SOLO_LECTURA` | ❌ |

La regla está en `ActividadService.validarRolDeEscritura` y, para las rutas MVC, en `SecurityConfig`
(`GET /procesos/*/actividades/*/editar` y `POST /procesos/*/actividades/*`). La API REST delega el
permiso en el servicio y responde 403 en JSON.

## Interfaz

```
GET  /procesos/{procesoId}/actividades/{actividadId}/editar → actividades/formularioeditar.html
POST /procesos/{procesoId}/actividades/{actividadId}        → redirect a /procesos/{procesoId}
```

El formulario llega precargado con el nombre, el tipo y la lane actuales, y ofrece las lanes del
pool del proceso. No pide la posición. Validación con Bean Validation y errores junto a cada campo.

## API REST

```
PUT /api/procesos/{procesoId}/actividades/{actividadId}
```

| Situación | Respuesta |
|---|---|
| Edición correcta | `200 OK` + cuerpo |
| Cuerpo incompleto | `400 Bad Request` |
| Lane inválida o de otro pool | `400` `LANE_NO_VALIDA` |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| Rol sin permiso o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Actividad o proceso inexistente, eliminado | `404` `RECURSO_NO_ENCONTRADO` |
| Nombre repetido en el proceso | `409` `ACTIVIDAD_NOMBRE_DUPLICADO` |

## Aislamiento entre empresas

Igual que en HU-08: la empresa sale del usuario autenticado, el proceso debe ser suyo, la actividad
debe pertenecer a ese proceso y la lane debe pertenecer al pool de ese proceso. Ningún identificador
de empresa llega del cliente.

## Pruebas

- `ActividadServiceTest`: los tres roles, cambio de nombre (con normalización), de tipo y de lane,
  edición sin cambios que no guarda ni registra historial, historial granular con un solo campo y
  con los tres, nombre duplicado, no se consulta duplicado cuando el nombre no cambia, actividad
  eliminada, actividad de otro proceso, proceso eliminado, proceso de otra empresa, lane de otro
  pool y posición intacta.
- `EditarActividadDtoTest`: validación del formulario y comprobación de que el DTO **no** expone la
  posición.
- `ActividadRestControllerTest`, `ActividadControllerTest` y `SeguridadActividadesTest`: contrato
  REST, formulario MVC, roles y CSRF.
- `ActividadesIntegracionTest`: contra PostgreSQL, el cambio de lane conserva el `id` de la
  actividad, el detalle la dibuja bajo la banda nueva, y una edición sin cambios no añade historial.
- Conservación de arcos: `ActividadServiceTest` comprueba que editar y cambiar de lane no tocan las
  conexiones, y `ArcosYGatewaysIntegracionTest` lo verifica contra PostgreSQL con arcos reales,
  entrantes y salientes.

## Cómo demostrarla

1. Abrir un proceso con al menos una actividad y pulsar **Editar** sobre ella.
2. Cambiar el nombre y el tipo, guardar: el detalle muestra los valores nuevos en la misma posición.
3. Entrar al historial: aparece una entrada con **solo los campos modificados**.
4. Volver a guardar sin cambiar nada: el historial **no crece**.
5. Intentar ponerle el nombre de otra actividad del mismo proceso: se rechaza.
6. Con un usuario `SOLO_LECTURA`, el enlace no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Cambio de lane demostrable en la interfaz con más de una banda**: depende de la gestión de lanes
  de HU-22. El modelo, la vista y las pruebas ya lo soportan.
- **Edición de la posición**: fuera de los criterios de esta historia.
