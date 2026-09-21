# HU-15 · Editar gateway

## Qué pide la historia

Como usuario con permisos de edición, quiero cambiar el tipo de un gateway cuando la lógica del
proceso cambia.

Criterios de aceptación:

- Se cambia el **tipo**.
- El **símbolo cambia** con él.
- Se **actualizan las condiciones** de los arcos salientes.
- Al pasar a `EXCLUSIVO`, se **advierte sobre la exclusividad** de las condiciones.
- Al pasar a `PARALELO`, se **eliminan las condiciones**.
- El cambio queda **en el historial**.

## Qué se edita

```java
public class EditarGatewayDto {
    private TipoGateway tipo;                  // obligatorio
    private Map<Long, String> condiciones;     // arcoId -> condición
}
```

El tipo y **las condiciones de sus arcos salientes en el mismo envío**. La posición no forma parte
de esta historia, igual que la posición de una actividad no forma parte de HU-09.

El mapa se indexa por identificador de arco, que es lo que el formulario conoce:

```html
<input type="text" th:field="*{condiciones[__${saliente.id}__]}" maxlength="200">
```

Un arco cuyo identificador no aparezca en el mapa **conserva la condición que ya tenía**; eso
permite editar solo una de varias salidas sin reescribir las demás.

## El cambio de tipo arrastra a los arcos

El punto delicado de esta historia es que el tipo del gateway y las condiciones de sus salidas
**tienen que quedar coherentes en la misma transacción**. Un gateway `PARALELO` con condiciones, o
uno `EXCLUSIVO` con salidas sin condición, sería un modelo inválido.

### A `PARALELO`: se eliminan las condiciones

```java
private List<Arco> limpiarCondiciones(List<Arco> salientes) {
    for (Arco arco : salientes) {
        if (arco.getCondicion() != null) {
            arco.setCondicion(null);
            modificados.add(arco);
        }
    }
    ...
}
```

Es el único sitio del proyecto donde una condición se descarta sin preguntar, y es así **porque el
criterio lo pide explícitamente**. En cualquier otro punto —crear o editar un arco— una condición
que no corresponde se rechaza con un 400 en lugar de ignorarse (ver HU-11).

Las salidas que ya venían sin condición no cuentan como modificadas, así que el historial refleja
cuántas se limpiaron de verdad.

### A `EXCLUSIVO` o `INCLUSIVO`: todas las salidas terminan con condición

```java
private List<Arco> aplicarCondiciones(List<Arco> salientes, EditarGatewayDto dto) {
    for (Arco arco : salientes) {
        String condicion = textoONulo(dto.getCondiciones().get(arco.getId()));
        if (condicion == null) {
            if (arco.getCondicion() == null) {
                throw new CondicionArcoNoValidaException(SALIDA_SIN_CONDICION);
            }
        } else if (!condicion.equals(arco.getCondicion())) {
            arco.setCondicion(condicion);
            modificados.add(arco);
        }
    }
    ...
}
```

Si una salida no tenía condición y el formulario tampoco la aporta, el cambio **se rechaza entero**
con `400 CONDICION_NO_VALIDA`: ni el tipo ni las condiciones se guardan. Una condición en blanco
equivale a ausente.

**Un gateway sin salidas no bloquea nada**: el bucle no itera y el cambio de tipo se aplica. Lo
único que aparece es la advertencia de divergencia incompleta de HU-14.

## Advertencia de exclusividad

Las condiciones son texto libre y el proyecto no ejecuta procesos, así que **no se intenta demostrar
matemáticamente** que dos condiciones se excluyen. Lo que sí se hace:

| Situación | Advertencia |
|---|---|
| Dos salidas con la **misma condición** (normalizando espacios y mayúsculas) | `Gateway EXCLUSIVO #12 repite la condición 'monto alto' en más de una salida: esas condiciones no son mutuamente excluyentes.` |
| Dos o más salidas con **condiciones distintas** | `Gateway EXCLUSIVO #12: revisa que las condiciones de sus salidas sean mutuamente excluyentes, porque solo una puede cumplirse.` |

La primera es una afirmación: dos condiciones idénticas **no pueden** ser excluyentes, y eso sí se
puede detectar. La segunda es un recordatorio informativo que solo aparece cuando hay algo que
revisar, es decir, con dos salidas o más.

La normalización es deliberadamente simple —`trim`, minúsculas y espacios colapsados— porque su
único propósito es detectar la misma frase escrita de dos maneras, no interpretar la condición.

`INCLUSIVO` **no recibe** la advertencia de exclusividad: en un gateway inclusivo varias salidas
pueden cumplirse a la vez, así que repetir o solapar condiciones no es un error.

## Sin cambios, sin historial

```java
if (cambios.isEmpty()) {
    return conAdvertencias(toDto(gateway), proceso, gateway);
}
```

Enviar el mismo tipo y las mismas condiciones devuelve el gateway tal cual, **sin guardar y sin
registrar historial**, igual que la edición de un proceso (HU-05), de una actividad (HU-09) o de un
arco (HU-12). Las advertencias sí se devuelven, porque son el estado actual del modelo, no un
cambio.

## Historial

```
gateway #12: tipo: 'EXCLUSIVO' -> 'PARALELO'; condiciones eliminadas en 2 arcos de salida
gateway #12: condiciones actualizadas en 1 arco de salida
```

Una sola entrada por edición, con los dos aspectos que pueden cambiar y solo los que cambiaron.
`editar` es `@Transactional`: el gateway, sus arcos y la entrada de historial se confirman juntos.

## Permisos

| Rol | Editar gateway |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ✅ |
| `SOLO_LECTURA` | ❌ |

La regla está en `GatewayService` y, para las rutas MVC, en `SecurityConfig`
(`GET /procesos/*/gateways/*/editar` y `POST /procesos/*/gateways/*`).

Un gateway inactivo no se edita (`RecursoNoEncontradoException`), y un proceso eliminado no admite
ediciones, igual que en HU-06.

## Interfaz

```
GET  /procesos/{procesoId}/gateways/{gatewayId}/editar → gateways/formularioeditar.html
POST /procesos/{procesoId}/gateways/{gatewayId}        → redirect a /procesos/{procesoId}
```

El formulario llega con el tipo actual y con **un campo de condición por cada arco saliente**,
etiquetado con el destino del arco (`Condición hacia Aprobar solicitud`), y muestra arriba las
advertencias de consistencia del gateway. Si no tiene salidas, lo dice.

## API REST

```
PUT /api/procesos/{procesoId}/gateways/{gatewayId}
```

| Situación | Respuesta |
|---|---|
| Edición correcta | `200 OK` + cuerpo con `tipo`, `simbolo` y `advertencias` |
| Cuerpo sin tipo | `400 Bad Request` |
| Una salida quedaría sin condición | `400` `CONDICION_NO_VALIDA` |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| Rol sin permiso, o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Gateway o proceso inexistente o eliminado | `404` `RECURSO_NO_ENCONTRADO` |

## Aislamiento entre empresas

La empresa sale del usuario autenticado y el gateway debe pertenecer a un proceso suyo
(`findByIdAndProcesoId`). Ningún identificador de empresa llega del cliente.

## Pruebas

- `GatewayServiceTest`: cambio de tipo con su historial; paso a `PARALELO` que elimina condiciones
  (y que ignora las salidas que ya no la tenían); paso a `EXCLUSIVO` que exige condición y que
  actualiza las recibidas; paso a `INCLUSIVO` que exige condición; condición en blanco tratada como
  ausente; advertencia de exclusividad; condiciones idénticas señaladas como no excluyentes;
  edición sin cambios que no guarda ni registra historial; gateway sin salidas; los tres roles;
  proceso eliminado y proceso de otra empresa.
- `GatewayControllerTest`: el formulario llega con el tipo y las condiciones actuales, y el envío
  delega en el servicio.
- `GatewayRestControllerTest`: 200, 400 y 404.
- `SeguridadGatewaysTest`: roles y CSRF sobre la edición.
- `ArcosYGatewaysIntegracionTest`: contra PostgreSQL, el paso a `PARALELO` deja las condiciones en
  `null` y el paso a `EXCLUSIVO` las persiste.

## Cómo demostrarla

1. Abrir un proceso con un gateway `EXCLUSIVO` que tenga dos salidas con condición.
2. Pulsar **Editar** sobre él y cambiar el tipo a `PARALELO`: el rombo pasa a mostrar **+** y las
   dos condiciones desaparecen del flujo.
3. Volver a `EXCLUSIVO` sin escribir condiciones: se rechaza con un mensaje claro.
4. Escribir una condición distinta en cada salida y guardar: aparece el recordatorio de
   exclusividad.
5. Poner la misma condición en las dos: la advertencia dice que no son mutuamente excluyentes.
6. Entrar al historial: solo hay entradas de las ediciones que cambiaron algo.

## Qué quedó pendiente

- **Editar la posición del gateway**: fuera de los criterios de esta historia.
- **Comprobar de verdad la exclusividad**: exigiría evaluar expresiones, y el proyecto no ejecuta
  procesos.
- **Eliminar gateways**: es HU-16.
