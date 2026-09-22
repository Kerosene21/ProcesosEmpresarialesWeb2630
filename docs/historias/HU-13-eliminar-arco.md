# HU-13 · Eliminar arco

## Qué pide la historia

Como administrador, quiero quitar una conexión que ya no aplica, sin perder su rastro y sabiendo
qué deja desconectado.

Criterios de aceptación:

| Criterio | Estado |
|---|---|
| Se solicita **confirmación previa** | **Cumplido** |
| La eliminación es **lógica** | **Cumplido** |
| El arco **desaparece del diagrama** | **Cumplido** |
| Se **advierte** si deja un elemento sin entrada o sin salida | **Cumplido** |
| Queda **registrada en el historial** | **Cumplido** |
| Solo el **ADMINISTRADOR** elimina | **Cumplido** |

## Eliminación lógica

```java
arco.setActivo(false);
arcoRepository.save(arco);
```

`activo = false` es la representación persistente del estado inactivo, igual que `eliminado = true`
en `Proceso` (HU-06) y `activo = false` en `Actividad` (HU-10). La fila **se conserva** con sus dos
extremos, su etiqueta, su condición y su proceso.

**`ArcoService` no contiene ninguna llamada a `delete`, `deleteById` ni `deleteAll`**, y hay pruebas
que lo verifican explícitamente, con mocks y contra PostgreSQL contando filas antes y después.

Consecuencias:

- El arco **desaparece del diagrama**: `consultarActivos` filtra por `activo = true`, así que la
  línea deja de dibujarse en el SVG y la fila desaparece de la lista de arcos.
- El par origen-destino **queda libre**: como la unicidad solo aplica a los arcos activos, se puede
  volver a trazar la misma conexión más adelante, y se creará un arco nuevo con otro identificador.
  Es una diferencia deliberada con el nombre de un proceso o de una actividad, que sí quedan
  reservados: un arco no tiene nombre propio que proteger.
- El **historial del proceso se conserva íntegro**.

## Confirmación previa

El criterio se cumple con una **vista Thymeleaf real y verificable**, no con un `confirm()` de
JavaScript: así queda cubierta por pruebas y funciona sin JavaScript. Son dos pasos:

```
GET  /procesos/{procesoId}/arcos/{arcoId}/eliminar
     → ArcoController.confirmarEliminacion
       ├─ requiere rol ADMINISTRADOR (filtro de seguridad)
       ├─ ArcoService.obtenerParaEliminar  → @Transactional(readOnly = true)
       └─ arcos/confirmareliminacion.html
            NO cambia nada: lee el arco, calcula la advertencia y la muestra

POST /procesos/{procesoId}/arcos/{arcoId}/eliminar
     → ArcoController.eliminar
       ├─ requiere rol ADMINISTRADOR (filtro) y token CSRF
       └─ ArcoService.eliminar → redirect a /procesos/{procesoId}

DELETE /api/procesos/{procesoId}/arcos/{arcoId}
     → mismo servicio → 204 No Content
```

La página de confirmación muestra el **origen**, el **destino**, la **etiqueta** y la **condición**,
advierte de que la fila se conserva marcada como inactiva, **adelanta la advertencia de
desconexión** y ofrece el botón **Confirmar eliminación** —que envía el `POST` con el token CSRF— y
un enlace **Cancelar y volver al proceso**.

**El `GET` no elimina nada**: `obtenerParaEliminar` es de solo lectura y hay pruebas que abren la
confirmación y comprueban después que `activo` sigue en `true`.

> En la API REST el `DELETE` es directo, sin confirmación: confirmar es una interacción de interfaz.
> La confirmación demostrable de este criterio es la del flujo MVC.

## Advertencia de desconexión

Después de desactivar el arco, `ConexionesService` mira **solo sus dos extremos** y comprueba si
alguno se quedó sin conexiones en el sentido afectado:

```java
private void revisar(Proceso proceso, TipoNodoFlujo tipo, Long nodoId, boolean salida, ...) {
    ...
    List<Arco> restantes = salida ? salientesActivos(...) : entrantesActivos(...);
    if (restantes.stream().anyMatch(arco -> !Objects.equals(arco.getId(), arcoIgnorado))) {
        return;
    }
    advertencias.add("'" + describir(...) + (salida ? SIN_SALIDAS : SIN_ENTRADAS));
}
```

El texto que llega al usuario es del estilo:

```
'Revisar solicitud' quedó sin arcos de salida
'Aprobar solicitud' quedó sin arcos de entrada
```

**Lo que este cálculo hace y lo que no:**

- Mira el **origen** del arco (¿le queda alguna salida?) y el **destino** (¿le queda alguna
  entrada?). Dos consultas indexadas.
- **No** recorre el grafo, no busca componentes inconexas, no detecta caminos rotos entre el inicio
  y el final ni ciclos. Un análisis BPMN global no lo pide esta historia y sería un algoritmo con su
  propia complejidad.
- **No bloquea la eliminación**: es informativa y así lo dice la propia página de confirmación.
- El mismo nodo no se advierte dos veces aunque aparezca en varios arcos.

La advertencia se calcula **antes** (en la confirmación, ignorando el arco que se va a borrar) y
**después** (en la respuesta de la eliminación, sobre el estado ya persistido). Las dos rutas usan
el mismo código y devuelven el mismo texto.

La misma maquinaria la reutiliza HU-10 cuando eliminar una actividad desactiva sus arcos.

## Permisos

| Rol | Eliminar arco |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ❌ |
| `SOLO_LECTURA` | ❌ |

Eliminar es más restrictivo que crear o editar, igual que con los procesos en HU-06 y las
actividades en HU-10. La regla está en `ArcoService.eliminar` y `obtenerParaEliminar`, y
`SecurityConfig` corta antes las dos rutas MVC:

```java
.requestMatchers(HttpMethod.GET,  "/procesos/*/arcos/*/eliminar").hasRole(ROL_ADMINISTRADOR)
.requestMatchers(HttpMethod.POST, "/procesos/*/arcos/*/eliminar").hasRole(ROL_ADMINISTRADOR)
```

Un `EDITOR` **ni siquiera llega a la página de confirmación**.

## Segunda eliminación

Eliminar dos veces el mismo arco lanza `RecursoNoEncontradoException("El arco ya fue eliminado")`,
que la API traduce a **404** y la web a la página de error. Es consistente con la segunda
eliminación de un proceso (HU-06) y de una actividad (HU-10), y está probado con mocks y contra
PostgreSQL: la segunda llamada no guarda nada ni añade historial.

## Proceso eliminado

Un proceso con `eliminado = true` es consultable pero **no modificable** desde HU-06. Por eso
`eliminar` usa `procesoActivoDeLaEmpresa`: no se pueden eliminar arcos de un proceso eliminado. Sus
arcos **siguen siendo consultables**.

## Historial

Cada eliminación añade una entrada al historial del proceso con fecha, usuario, estado del proceso
en ese momento y el texto:

```
arco eliminado: 'Revisar solicitud' -> 'Aprobar solicitud'
```

La descripción se calcula **antes** de desactivar el arco. `eliminar` es `@Transactional`: la marca
y su entrada se confirman juntas.

## API REST

```
DELETE /api/procesos/{procesoId}/arcos/{arcoId}
```

| Situación | Respuesta |
|---|---|
| Eliminación correcta | `204 No Content`, sin cuerpo |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| No es administrador, o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Arco o proceso inexistente, o ya eliminado | `404` `RECURSO_NO_ENCONTRADO` |

> El `204` no lleva cuerpo, así que **la advertencia de desconexión no viaja en el `DELETE`**. Un
> cliente que la necesite puede leerla antes con la confirmación del flujo MVC o recalcularla
> consultando el proceso. Cambiar el contrato a `200` con cuerpo se descartó por coherencia con el
> `DELETE` de actividades de HU-10.

## Aislamiento entre empresas

La empresa sale del usuario autenticado. Un administrador de otra empresa recibe
`UsuarioSinPermisoException` (403) tanto en la confirmación como en la eliminación.

## Pruebas

- `ArcoServiceTest`: administrador elimina, editor y solo lectura no, `activo = false`, ausencia
  total de borrado físico, entrada de historial con su texto exacto, segunda eliminación, proceso
  eliminado, advertencia de los dos extremos, advertencia de un solo extremo cuando el otro conserva
  conexiones, y `obtenerParaEliminar` que exige administrador, no modifica nada y adelanta la
  advertencia.
- `ConexionesServiceTest`: nodos con otras conexiones que no generan advertencia, nodos repetidos
  que no se advierten dos veces y nodos desaparecidos descritos por su referencia.
- `ArcoControllerTest`: la confirmación muestra el arco y **no** llama a `eliminar`; el `POST` sí
  elimina, redirige y lleva las advertencias como mensaje.
- `ArcoRestControllerTest`: 204, 403 y 404.
- `SeguridadArcosTest`: login obligatorio, los tres roles sobre la confirmación y sobre el envío,
  `POST` sin CSRF rechazado y 401 JSON en la API.
- `ArcosYGatewaysIntegracionTest`: contra PostgreSQL, la fila se conserva y solo cambia `activo`, el
  arco desaparece de los activos, el par queda libre para volver a trazarse y la segunda eliminación
  se rechaza.

## Cómo demostrarla

1. Abrir un proceso con arcos como **administrador**.
2. Pulsar **Eliminar** sobre un arco: se abre la confirmación con sus datos y con la advertencia de
   qué queda desconectado.
3. Pulsar **Cancelar**: se vuelve al proceso y el arco **sigue ahí**.
4. Repetir y pulsar **Confirmar eliminación**: la línea **ya no aparece** en el flujo y el mensaje
   superior repite la advertencia.
5. Entrar al historial: la eliminación aparece como una entrada más, con su autor y su fecha.
6. Comprobar en la base de datos que la fila sigue existiendo con `activo = false`.
7. Iniciar sesión como **editor**: el enlace no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Advertencia en la respuesta del `DELETE` REST**: el contrato es `204` sin cuerpo.
- **Análisis global de conectividad del flujo** (componentes inconexas, caminos rotos): fuera del
  alcance de esta historia.
- **Restaurar un arco eliminado**: no lo pide ningún criterio; volver a crearlo produce un arco
  nuevo.
