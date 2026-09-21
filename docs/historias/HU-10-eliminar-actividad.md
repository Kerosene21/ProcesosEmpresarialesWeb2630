# HU-10 · Eliminar actividad

## Qué pide la historia

Como administrador, quiero quitar del diagrama una actividad que ya no aplica, sin perder su rastro.

Criterios de aceptación:

| Criterio | Estado |
|---|---|
| Solo el **ADMINISTRADOR** elimina | **Cumplido** |
| Se solicita **confirmación previa** | **Cumplido** |
| La eliminación es **lógica** | **Cumplido** |
| Queda **registrada en el historial** | **Cumplido** |
| Se eliminan también los **arcos entrantes y salientes** | **Pendiente: depende de HU-11** |
| Se **advierte si el flujo queda con elementos desconectados** | **Pendiente: depende de HU-11** |

> **Los dos últimos criterios no están implementados y no se declaran cerrados.** La entidad `Arco`
> no existe todavía en el proyecto: nace con HU-11. No se creó una implementación ficticia de arcos
> solo para poder marcar esos criterios; ver «Dependencia con HU-11» más abajo.

## Eliminación lógica

```java
actividad.setActivo(false);
actividadRepository.save(actividad);
```

`activo = false` es la representación persistente del estado inactivo, igual que `eliminado = true`
en `Proceso` (HU-06). La fila **se conserva** con su nombre, su tipo, su lane, su posición y su
proceso.

**`ActividadService` no contiene ninguna llamada a `delete`, `deleteById` ni `deleteAll`**, y hay
pruebas que lo verifican explícitamente, con mocks y contra PostgreSQL contando filas antes y
después. Lo único que cambia en la fila es la columna `activo`.

Consecuencias:

- La actividad **desaparece del diagrama activo**: `activasDelProceso` filtra por `activo = true`.
- Su **nombre sigue reservado** dentro del proceso, porque la restricción
  `uk_actividad_proceso_nombre` no distingue `activo`. Es coherente con HU-06, donde el nombre de un
  proceso eliminado también queda reservado.
- El **historial del proceso se conserva íntegro**, incluidas las entradas que mencionan a esa
  actividad.

## Confirmación previa

El criterio se cumple con una **vista Thymeleaf real y verificable**, no con un `confirm()` de
JavaScript: así queda cubierta por pruebas y funciona sin JavaScript. Son dos pasos:

```
GET  /procesos/{procesoId}/actividades/{actividadId}/eliminar
     → ActividadController.confirmarEliminacion
       ├─ requiere rol ADMINISTRADOR (filtro de seguridad)
       ├─ ActividadService.obtenerParaEliminar  → @Transactional(readOnly = true)
       └─ actividades/confirmareliminacion.html
            NO cambia nada: solo lee la actividad y la muestra

POST /procesos/{procesoId}/actividades/{actividadId}/eliminar
     → ActividadController.eliminar
       ├─ requiere rol ADMINISTRADOR (filtro) y token CSRF
       └─ ActividadService.eliminar → redirect a /procesos/{procesoId}

DELETE /api/procesos/{procesoId}/actividades/{actividadId}
     → mismo servicio → 204 No Content
```

La página de confirmación muestra el **nombre**, el **tipo** y la **lane** de la actividad, advierte
de que dejará de aparecer en el diagrama pero no se borrará de la base de datos, ofrece el botón
**Confirmar eliminación** —que envía el `POST` con el token CSRF— y un enlace **Cancelar y volver al
proceso**.

**El `GET` no elimina nada**: `obtenerParaEliminar` es de solo lectura y hay pruebas que abren la
confirmación y comprueban después que `activo` sigue en `true`, tanto con mocks como contra
PostgreSQL.

En el diagrama, «Eliminar» es un **enlace** a esa página, no un botón que borre directamente, y solo
se muestra a quien realmente puede eliminar (`puedeEliminar`) y mientras el proceso siga activo.

> En la API REST el `DELETE` es directo, sin confirmación: confirmar es una interacción de interfaz.
> La confirmación demostrable de este criterio es la del flujo MVC.

## Permisos

| Rol | Eliminar actividad |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ❌ |
| `SOLO_LECTURA` | ❌ |

Eliminar es más restrictivo que crear o editar, igual que con los procesos en HU-06. La regla está
en `ActividadService.validarRolAdministrador`, y `SecurityConfig` corta antes las dos rutas MVC:

```java
.requestMatchers(HttpMethod.GET,  "/procesos/*/actividades/*/eliminar").hasRole(ROL_ADMINISTRADOR)
.requestMatchers(HttpMethod.POST, "/procesos/*/actividades/*/eliminar").hasRole(ROL_ADMINISTRADOR)
```

Un `EDITOR` **ni siquiera llega a la página de confirmación**. La API REST delega el permiso en el
servicio y responde 403 en JSON.

## Segunda eliminación

Eliminar dos veces la misma actividad lanza `RecursoNoEncontradoException("La actividad ya fue
eliminada")`, que la API traduce a **404** y la web a la página de error. Es consistente con la
segunda eliminación de un proceso en HU-06 y está probado con mocks y contra PostgreSQL: la segunda
llamada no guarda nada ni añade historial.

## Proceso eliminado

Un proceso con `eliminado = true` es consultable pero **no modificable** desde HU-06. Por eso
`eliminar` usa `procesoActivoDeLaEmpresa`: no se pueden eliminar actividades de un proceso
eliminado. Las actividades de ese proceso **siguen siendo consultables**.

## Historial

Cada eliminación añade una entrada al historial del proceso con fecha, usuario, estado del proceso
en ese momento y el texto `actividad eliminada: 'Revisar solicitud'`. `eliminar` es `@Transactional`:
la marca y su entrada se confirman juntas.

## Aislamiento entre empresas

La empresa sale del usuario autenticado. Un administrador de otra empresa recibe
`UsuarioSinPermisoException` (403) tanto en la confirmación como en la eliminación.

## API REST

```
DELETE /api/procesos/{procesoId}/actividades/{actividadId}
```

| Situación | Respuesta |
|---|---|
| Eliminación correcta | `204 No Content`, sin cuerpo |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| No es administrador, o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Actividad o proceso inexistente, ya eliminado | `404` `RECURSO_NO_ENCONTRADO` |

## Dependencia con HU-11: arcos y desconexiones

Dos criterios de esta historia hablan de **arcos**:

- eliminar los arcos entrantes y salientes de la actividad;
- advertir si el flujo queda con elementos desconectados.

**`Arco` no existe en el dominio**: es la entidad que introduce HU-11, y este bloque cubre HU-08,
HU-09 y HU-10 únicamente. Inventar aquí una tabla de arcos para poder marcar los criterios daría un
modelo que HU-11 tendría que rehacer, y una prueba verde que no demuestra nada real. Por eso:

- **no se implementaron**, y se declaran **pendientes**;
- **no se dejó código muerto ni comentarios** anticipando la solución;
- el diseño **queda preparado** para completarlos sin rehacer nada.

Qué hará falta cuando exista `Arco`, sin tocar el resto de HU-10:

1. Un `ArcoRepository` con la consulta de los arcos activos que referencian la actividad como origen
   o como destino.
2. Dentro de `ActividadService.eliminar`, y **en la misma transacción** que ya existe, marcar esos
   arcos como inactivos con la misma eliminación lógica.
3. Registrar esos arcos en el mismo resumen de historial que ya se escribe.
4. Calcular, después de desactivarlos, qué actividades del proceso quedan sin arcos y devolver esa
   advertencia junto al resultado —`eliminar` ya devuelve un `ActividadRespuestaDto` en lugar de
   `void`, así que hay dónde colgarla— para mostrarla en el detalle y en la respuesta de la API.

Hasta entonces, **HU-10 está cerrada en permisos, confirmación, eliminación lógica e historial, y
abierta en arcos y desconexiones**.

## Pruebas

- `ActividadServiceTest`: administrador elimina, editor y solo lectura no, `activo = false`,
  ausencia total de borrado físico (`delete`, `deleteById`, `deleteAll`), entrada de historial con
  su texto exacto, segunda eliminación, proceso eliminado, proceso de otra empresa,
  `obtenerParaEliminar` que no modifica nada y que exige rol de administrador.
- `ActividadRestControllerTest`: 204, 403 y 404.
- `ActividadControllerTest`: la confirmación muestra la actividad y **no** llama a `eliminar`; el
  `POST` sí elimina y redirige.
- `SeguridadActividadesTest`: login obligatorio, los tres roles sobre la confirmación y sobre el
  envío, `POST` sin CSRF rechazado y 401 JSON en la API.
- `ActividadesIntegracionTest`: contra PostgreSQL, la fila se conserva y solo cambia `activo`, la
  actividad desaparece de `activasDelProceso` y del detalle renderizado, la segunda eliminación se
  rechaza, el historial queda persistido y un editor no llega a la confirmación.

## Cómo demostrarla

1. Abrir un proceso con actividades como **administrador**.
2. Pulsar **Eliminar** sobre una actividad: se abre la página de confirmación con sus datos.
3. Pulsar **Cancelar**: se vuelve al proceso y la actividad **sigue ahí**.
4. Repetir y pulsar **Confirmar eliminación**: se vuelve al proceso y la actividad **ya no aparece**
   en el diagrama.
5. Entrar al historial: la eliminación aparece como una entrada más, con su autor y su fecha.
6. Comprobar en la base de datos que la fila sigue existiendo con `activo = false`.
7. Iniciar sesión como **editor**: el enlace de eliminar no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Eliminar los arcos entrantes y salientes** de la actividad: depende de `Arco` (HU-11).
- **Advertir si el flujo queda con elementos desconectados**: depende de `Arco` (HU-11).
- **Restaurar una actividad eliminada**: no lo pide ningún criterio.
