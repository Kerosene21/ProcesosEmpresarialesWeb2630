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
| Se eliminan también los **arcos entrantes y salientes** | **Cumplido con HU-11** |
| Se **advierte si el flujo queda con elementos desconectados** | **Cumplido con HU-11** |

> **Los dos últimos criterios quedaron abiertos en el bloque HU-08 a HU-10** porque la entidad
> `Arco` no existía y no se quiso inventar una tabla ficticia solo para marcarlos. Con HU-11 la
> entidad existe, y este bloque los cierra con pruebas reales; ver «Arcos y desconexiones».

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

## Arcos y desconexiones

Desde HU-11 existe `Arco`, y eliminar una actividad hace dos cosas más, **dentro de la misma
transacción** que ya marcaba la fila como inactiva:

```java
actividad.setActivo(false);
actividadRepository.save(actividad);

List<Arco> desactivados = conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.ACTIVIDAD,
        actividad.getId());
registrarHistorial(proceso, usuario, "actividad eliminada: '" + actividad.getNombre() + "'"
        + resumenDeConexiones(desactivados));
```

1. **Desactiva todos sus arcos activos**, entrantes y salientes, con una sola consulta
   (`conectadosAlNodo`) y un `saveAll`. Son eliminaciones **lógicas**: `activo = false`, la fila se
   conserva. `ConexionesService` tampoco llama nunca a `delete`, `deleteById` ni `deleteAll`, y hay
   pruebas contra PostgreSQL que cuentan filas antes y después.
2. **Calcula qué vecinos quedaron desconectados** y los devuelve como advertencias en el
   `ActividadRespuestaDto`, junto con `arcosDesactivados`.

`ActividadController.eliminar` las propaga como `RedirectAttributes.addFlashAttribute("advertencias", ...)`,
igual que hace `ArcoController`, y el detalle del proceso las pinta en el bloque **Advertencias de la
última operación**. Hay pruebas de los dos tramos: que el controlador las coloca en el flash y que
`proceso.html` las renderiza.

> El `DELETE` de la API sigue devolviendo **204 sin cuerpo**, así que por REST la advertencia no
> viaja. Es el mismo contrato que en HU-13.

La actividad eliminada **no se advierte a sí misma**: se excluye del cálculo, porque desaparecer es
justo lo que se pidió. Lo que se advierte son sus vecinos:

```
'Aprobar solicitud' quedó sin arcos de salida
'Rechazar solicitud' quedó sin arcos de entrada
```

El alcance del cálculo es el mismo de HU-13: mira los extremos de los arcos afectados, no recorre el
grafo. La advertencia es informativa y **no bloquea** la eliminación.

### Sin dependencias circulares

`ActividadService` necesitaba lógica de arcos, y `ArcoService` necesita saber de actividades. Para
no cruzarlos, la lógica de conexiones vive en un colaborador propio:

```
NodoFlujoResolver   (ActividadRepository, GatewayRepository)
        ▲
ConexionesService   (ArcoRepository, NodoFlujoResolver)
        ▲                  ▲                  ▲
ActividadService     ArcoService        GatewayService
```

`ConexionesService` no conoce a ninguno de los tres servicios que lo usan, así que el grafo de
dependencias sigue siendo acíclico. `ActividadService` ganó **un** parámetro en su constructor y
diez líneas en `eliminar`; el resto de la historia no cambió.

### El historial solo crece cuando hay algo que contar

```
actividad eliminada: 'Revisar solicitud'
actividad eliminada: 'Revisar solicitud'; arcos desactivados: 2
```

El sufijo aparece **solo si de verdad se desactivó algún arco**, así que una actividad sin
conexiones deja exactamente la misma entrada que antes de HU-11.

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
- Arcos y desconexiones: `ActividadServiceTest` verifica que se delega en `ConexionesService`, que
  el historial anota los arcos desactivados solo cuando los hay y que las advertencias llegan al
  DTO; `ConexionesServiceTest` cubre la desactivación y el cálculo de vecinos;
  `ArcosYGatewaysIntegracionTest` lo comprueba contra PostgreSQL con dos arcos reales, contando
  filas antes y después.
- Advertencias en la interfaz: `ActividadControllerTest.eliminarLlevaLasAdvertenciasDeDesconexionAlDetalleDelProceso`
  comprueba el flash (y `eliminarSinArcosConectadosNoAnadeAdvertencias` el caso vacío);
  `DiagramaFlujoTest.elDetalleMuestraLasAdvertenciasQueLleganTrasEliminarUnaActividad` comprueba que
  `proceso.html` las pinta de verdad, y `ArcosYGatewaysIntegracionTest` recorre el POST completo.

## Cómo demostrarla

1. Abrir un proceso con actividades como **administrador**.
2. Pulsar **Eliminar** sobre una actividad: se abre la página de confirmación con sus datos.
3. Pulsar **Cancelar**: se vuelve al proceso y la actividad **sigue ahí**.
4. Repetir y pulsar **Confirmar eliminación**: se vuelve al proceso y la actividad **ya no aparece**
   en el diagrama.
   Si tenía arcos, desaparecen con ella y el detalle muestra, bajo el mensaje de éxito, la lista de
   vecinos que quedaron sueltos.
5. Entrar al historial: la eliminación aparece como una entrada más, con su autor y su fecha.
6. Comprobar en la base de datos que la fila sigue existiendo con `activo = false`.
7. Iniciar sesión como **editor**: el enlace de eliminar no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Análisis global de conectividad del flujo** (componentes inconexas, caminos rotos): fuera del
  alcance de esta historia; la advertencia mira solo los extremos de los arcos afectados.
- **Restaurar una actividad eliminada**: no lo pide ningún criterio.
