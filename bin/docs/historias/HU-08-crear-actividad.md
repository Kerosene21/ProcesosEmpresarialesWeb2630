# HU-08 · Crear actividad

## Qué pide la historia

Como usuario con permisos de edición, quiero agregar actividades al diagrama de un proceso para
documentar qué se hace y quién es el responsable.

Criterios de aceptación:

- Se especifican **nombre** y **tipo** de la actividad.
- La actividad **pertenece a un proceso**.
- La actividad **pertenece exactamente a una lane**, y esa lane representa a su responsable.
- El **nombre es obligatorio**.
- El nombre es **único dentro del proceso**.
- Se **conserva la posición visual** indicada.
- La actividad **aparece en el diagrama** en esa posición.
- La creación queda **registrada en el historial**.

Por coherencia con el resto del proyecto: un usuario nunca crea actividades en un proceso de otra
empresa, y un proceso eliminado (HU-06) no admite cambios.

## Qué se añadió al modelo

```java
@Entity
@Table(name = "actividad", uniqueConstraints = @UniqueConstraint(
        name = "uk_actividad_proceso_nombre", columnNames = { "proceso_id", "nombre" }))
public class Actividad {
    private Long id;
    private String nombre;        // not null, 150
    private TipoActividad tipo;   // not null, enum en texto
    private Proceso proceso;      // not null
    private Lane lane;            // not null
    private Integer posicionX;    // not null
    private Integer posicionY;    // not null
    private boolean activo;       // not null
}
```

`TipoActividad` es un enum con `TAREA_USUARIO`, `TAREA_SISTEMA`, `TAREA_MANUAL` y `TAREA_SERVICIO`.

**`lane` es obligatorio y es una sola referencia**: así el modelo obliga, por construcción, a que la
actividad pertenezca a exactamente una lane. No es una colección ni es opcional.

**`activo = true` representa la actividad vigente**; `activo = false` es la eliminación lógica de
HU-10. Es la misma forma que usa `Proceso.eliminado` en HU-06, con el booleano invertido para que el
valor por defecto de una actividad recién creada sea el vigente.

**La unicidad del nombre se declara dos veces a propósito**: como comprobación de negocio en el
servicio (`existsByProcesoIdAndNombreIgnoreCase`, que da un error legible) y como
`UniqueConstraint (proceso_id, nombre)` en la tabla, que es la que garantiza el invariante ante dos
peticiones simultáneas. Es el mismo patrón que `uk_proceso_empresa_nombre` en HU-04.

> El nombre de una actividad eliminada **sigue reservado** dentro de su proceso, porque la fila
> permanece y la restricción de la base de datos no distingue `activo`. Es coherente con HU-06,
> donde el nombre de un proceso eliminado también sigue reservado en su empresa.

## Lane: dependencia mínima con HU-22

HU-08 no puede existir sin lanes, pero **la gestión de lanes es HU-22 y no se implementó aquí**. Lo
que hay es la entidad mínima que el criterio obliga a tener:

```java
@Entity
@Table(name = "lane")
public class Lane {
    private Long id;
    private String nombre;   // not null, 150: el rol responsable de la banda
    private Pool pool;       // not null: toda lane es una división de un pool
}
```

Para que un proceso recién creado pueda recibir actividades, **`ProcesoService.crear` deja el pool
con una lane inicial llamada `General`**, que se persiste en cascada junto al pool. Es el mínimo
estricto: un pool sin lanes no puede contener actividades y HU-08 quedaría sin forma de ejecutarse.

**No se implementó nada más de HU-22**: no hay creación, renombrado, reordenamiento ni eliminación
de lanes, ni roles de proceso (HU-17), ni permisos por lane (HU-24). Mientras HU-22 no llegue, cada
proceso tiene **una sola lane**; el resto del código (servicio, formularios, diagrama y pruebas) ya
trabaja con varias, así que cuando esa historia añada la gestión, el cambio de banda de HU-09 queda
disponible en la interfaz sin tocar nada de este bloque.

> **Migración**: los procesos creados **antes** de este bloque tienen un pool sin lanes y no podrán
> recibir actividades hasta que su pool tenga una. En la base de desarrollo basta con:
>
> ```sql
> INSERT INTO lane (nombre, pool_id)
> SELECT 'General', p.id FROM pool p
> WHERE NOT EXISTS (SELECT 1 FROM lane l WHERE l.pool_id = p.id);
> ```
>
> En CI no aplica: el perfil de pruebas usa `create-drop` sobre un contenedor limpio.

## Flujo

```
POST /procesos/{procesoId}/actividades        → ActividadController.crear
POST /api/procesos/{procesoId}/actividades    → ActividadRestController.crear  → 201 + Location

                                 └─ ActividadService.crear(procesoId, dto, username)
                                      ├─ usuarioAutenticado(username)
                                      ├─ validarRolDeEscritura(usuario)
                                      │    └─ SOLO_LECTURA → UsuarioSinPermisoException
                                      ├─ procesoActivoDeLaEmpresa(procesoId, usuario)
                                      │    ├─ no existe        → RecursoNoEncontradoException
                                      │    ├─ de otra empresa  → UsuarioSinPermisoException
                                      │    └─ eliminado        → RecursoNoEncontradoException
                                      ├─ nombre = dto.nombre.trim()
                                      ├─ existsByProcesoIdAndNombreIgnoreCase
                                      │    └─ duplicado        → NombreActividadDuplicadoException
                                      ├─ laneDelProceso(laneId, proceso)
                                      │    └─ lane de otro pool o inexistente → LaneNoValidaException
                                      ├─ posicionX / posicionY del formulario
                                      ├─ activo = true
                                      ├─ ActividadRepository.save
                                      │    └─ DataIntegrityViolationException
                                      │         → NombreActividadDuplicadoException
                                      ├─ HistorialProcesoRepository.save("actividad creada: '...'")
                                      └─ ActividadRespuestaDto
```

`crear` es `@Transactional`: la actividad y su entrada de historial se confirman juntas o no se
confirma ninguna.

La colisión real de la base de datos se traduce igual que en `ProcesoService.crear`: la
`DataIntegrityViolationException` de la restricción `uk_actividad_proceso_nombre` se convierte en
`NombreActividadDuplicadoException`, de modo que el cliente ve un 409 estable y no un error 500.

## Normalización del nombre

El nombre se guarda con `trim()`, igual que el nombre del proceso en HU-04, y la comprobación de
duplicados es **insensible a mayúsculas** (`IgnoreCase`). `"  Revisar solicitud  "` y
`"revisar SOLICITUD"` colisionan con `"Revisar solicitud"`.

## Posición visual

`posicionX` y `posicionY` son obligatorios y se guardan tal como llegan: son las coordenadas del
rectángulo en el lienzo. El detalle del proceso las usa para dibujar la actividad dentro de la banda
de su lane, de modo que **la actividad aparece en el diagrama en la posición indicada**.

No se construyó un editor gráfico: la posición se escribe en el formulario y el diagrama la refleja.
El arrastrar y soltar pertenece a las historias de modelado visual, no a HU-08.

## Permisos

| Rol | Crear actividad |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ✅ |
| `SOLO_LECTURA` | ❌ |

La regla vive en `ActividadService.validarRolDeEscritura`, que es la misma que usa `ProcesoService`
para crear y editar procesos. Además, `SecurityConfig` corta antes las rutas MVC:

```java
.requestMatchers(HttpMethod.GET, "/procesos/*/actividades/nueva", "/procesos/*/actividades/*/editar")
    .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
.requestMatchers(HttpMethod.POST, "/procesos/*/actividades", "/procesos/*/actividades/*")
    .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
```

La API REST **no** se filtra por rol: `/api/**` solo exige autenticación y el permiso lo decide el
servicio, que responde 403 en JSON a través de `ApiExceptionHandler`. Es el mismo reparto que ya
tenían los procesos.

## Aislamiento entre empresas

La empresa **nunca llega del cliente**. Sale del usuario autenticado y se compara con la empresa del
proceso; si no coinciden, `UsuarioSinPermisoException`. Además, la lane se busca con
`findByIdAndPoolId(laneId, proceso.getPool().getId())`, así que **una lane del pool de otra empresa
no es utilizable** aunque se envíe su identificador a mano.

## Interfaz

```
GET  /procesos/{procesoId}/actividades/nueva   → actividades/formulario.html
POST /procesos/{procesoId}/actividades         → redirect a /procesos/{procesoId}
```

El formulario pide nombre, tipo, lane, posición X y posición Y. Los tipos salen del enum y las lanes
del pool del proceso; ningún identificador de empresa viaja en el formulario. La validación es Bean
Validation (`CrearActividadDto`) y los errores se muestran junto a cada campo.

## API REST

```
POST /api/procesos/{procesoId}/actividades
```

| Situación | Respuesta |
|---|---|
| Creación correcta | `201 Created` + `Location` + cuerpo |
| Cuerpo incompleto | `400 Bad Request` |
| Lane inválida o de otro pool | `400` `LANE_NO_VALIDA` |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| Rol sin permiso o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Proceso inexistente o eliminado | `404` `RECURSO_NO_ENCONTRADO` |
| Nombre repetido en el proceso | `409` `ACTIVIDAD_NOMBRE_DUPLICADO` |

## Historial

Cada creación añade una entrada a `historial_proceso` **del proceso**, no de la actividad: el
historial del proyecto es por proceso desde HU-05, y una actividad es parte de su diagrama. La
entrada guarda fecha, usuario, estado del proceso en ese momento y el texto
`actividad creada: 'Revisar solicitud'`.

## Pruebas

- `ActividadServiceTest` cubre la historia con mocks: usuario autenticado inexistente, los tres
  roles, normalización del nombre, duplicado por comprobación previa y por colisión real de la base
  de datos, proceso inexistente, de otra empresa y eliminado, lane buscada dentro del pool del
  proceso, lane de otro pool, posición persistida, `activo = true`, enlace con el proceso y entrada
  de historial con su texto exacto.
- `CrearActividadDtoTest` comprueba la validación del formulario campo por campo.
- `ActividadRestControllerTest` comprueba el contrato REST, incluido el `Location`.
- `ActividadControllerTest` y `SeguridadActividadesTest` cubren el formulario MVC, los roles y CSRF.
- `ActividadesIntegracionTest` lo verifica contra PostgreSQL: la lane inicial del pool, el enlace
  `Actividad → Proceso` y `Actividad → Lane → Pool`, la restricción de unicidad de la base de datos,
  el mismo nombre permitido en procesos distintos, el aislamiento entre empresas y que la actividad
  creada **aparece en el detalle del proceso**.

## Cómo demostrarla

1. Registrar una empresa, iniciar sesión como administrador y crear un proceso.
2. Abrir el proceso: la sección **Diagrama** muestra el pool y su lane `General`, todavía sin
   actividades.
3. Pulsar **Agregar actividad**, escribir nombre y tipo, elegir la lane y unas coordenadas.
4. Al guardar se vuelve al detalle y la actividad **aparece dibujada dentro de su lane** en esa
   posición.
5. Intentar crear otra con el mismo nombre: se rechaza.
6. Entrar a **Ver historial de cambios**: aparece la entrada de creación con su autor y su fecha.
7. Con un usuario `SOLO_LECTURA`, el botón no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Gestión de lanes (HU-22)**: crear, renombrar, ordenar y eliminar lanes. Aquí solo existe la lane
  inicial del pool.
- **Roles de proceso (HU-17)** como responsables de cada lane: hoy la lane solo tiene nombre.
- **Edición gráfica de la posición**: se escribe en el formulario, no se arrastra.
- **Arcos, gateways y eventos**: HU-11 en adelante.
