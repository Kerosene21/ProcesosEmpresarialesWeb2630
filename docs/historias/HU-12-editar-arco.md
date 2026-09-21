# HU-12 · Editar arco

## Qué pide la historia

Como usuario con permisos de edición, quiero corregir una conexión del diagrama sin tener que
borrarla y volver a trazarla.

Criterios de aceptación:

- Se cambia el **origen**.
- Se cambia el **destino**.
- Se edita la **etiqueta**.
- Si el arco sale de un gateway, se edita la **condición**.
- Se aplican las **mismas validaciones** que al crear.
- El diagrama **se redibuja** al guardar.
- El cambio queda **en el historial**.

## Qué se edita

```java
public class EditarArcoDto {
    private TipoNodoFlujo origenTipo;   // obligatorio
    private Long origenId;              // obligatorio
    private TipoNodoFlujo destinoTipo;  // obligatorio
    private Long destinoId;             // obligatorio
    private String etiqueta;            // opcional, máx. 150
    private String condicion;           // opcional, máx. 200
}
```

Es el mismo conjunto de campos que `CrearArcoDto`. Lo que **no** se edita es el proceso al que
pertenece el arco: un arco nace y muere dentro del mismo proceso.

## El arco conserva su identidad

`editar` **modifica la fila existente**: no borra ni recrea.

```java
Arco arco = arcoActivoDelProceso(arcoId, proceso);
...
arco.setOrigenTipo(origen.tipo());
arco.setOrigenId(origen.id());
arco.setDestinoTipo(destino.tipo());
arco.setDestinoId(destino.id());
arco.setEtiqueta(etiquetaNueva);
arco.setCondicion(condicionNueva);
arcoRepository.save(arco);
```

El `id` no cambia, de modo que cualquier referencia futura al arco sigue siendo válida. Hay pruebas
con mocks y contra PostgreSQL que lo comprueban.

## Las mismas validaciones que al crear

`editar` repite, en este orden, las comprobaciones de HU-11:

1. rol de escritura (`ADMINISTRADOR` o `EDITOR`);
2. proceso de la empresa del usuario y **no eliminado**;
3. el arco existe en ese proceso y **sigue activo**;
4. origen y destino existen, están **activos** y pertenecen al proceso;
5. **origen distinto de destino**;
6. **no hay otro arco activo** con ese mismo par;
7. la **condición** corresponde al tipo del nuevo origen.

La comprobación de duplicados usa una variante que se excluye a sí misma, para que guardar un arco
sin tocar sus extremos no choque consigo mismo:

```java
existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrueAndIdNot(
        procesoId, origenTipo, origenId, destinoTipo, destinoId, arcoId)
```

Cambiar el origen a un gateway `EXCLUSIVO` obliga a aportar condición en ese mismo envío; cambiarlo
a una actividad obliga a que la condición quede vacía. Las reglas completas están en HU-11.

## El historial registra solo lo que cambió

`construirCambios` compara campo a campo, igual que `ProcesoService.editar` desde HU-05 y
`ActividadService.editar` desde HU-09:

```
arco 'Revisar solicitud' -> 'Aprobar solicitud': etiqueta: '' -> 'solicitud completa'
arco 'Revisar solicitud' -> 'Aprobar solicitud': destino: 'Aprobar solicitud' -> 'Archivar solicitud'
arco 'Gateway EXCLUSIVO #12' -> 'Aprobar solicitud': condicion: 'monto alto' -> 'monto muy alto'
```

El resumen se construye **con los extremos anteriores**, que es como se conocía el arco hasta ese
momento, y después se aplican los cambios. Un valor ausente se escribe como `''`, no como `null`.

**Si no cambia nada, no se guarda ni se registra historial.** `editar` devuelve el DTO actual y sale
sin llamar a `save`. Hay pruebas con mocks y contra PostgreSQL que lo verifican.

## El diagrama se redibuja

No hace falta ninguna acción extra: el detalle del proceso consulta
`ArcoService.consultarActivos(procesoId, username)` en cada carga y recalcula los extremos visuales
con las posiciones **actuales** de los nodos. Cambiar el destino de un arco cambia, en la siguiente
carga, el punto donde termina la línea y hacia dónde apunta la punta.

## Advertencias al editar

La respuesta incluye las advertencias del gateway que quede como origen y, **si el origen cambió**,
también las del gateway que dejó de serlo: quitarle una salida a un gateway puede dejarlo con menos
de dos y conviene decirlo en ese momento.

No se calculan advertencias de desconexión al editar: ese criterio pertenece a la eliminación
(HU-13).

## Permisos

| Rol | Editar arco |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ✅ |
| `SOLO_LECTURA` | ❌ |

La regla está en `ArcoService` y, para las rutas MVC, en `SecurityConfig`
(`GET /procesos/*/arcos/*/editar` y `POST /procesos/*/arcos/*`). La API delega el permiso en el
servicio y responde 403 en JSON.

## Un arco eliminado no se edita

`arcoActivoDelProceso` rechaza con `RecursoNoEncontradoException("El arco ya fue eliminado")`
cualquier arco con `activo = false`, igual que una actividad eliminada en HU-09 o un proceso
eliminado en HU-06.

## Interfaz

```
GET  /procesos/{procesoId}/arcos/{arcoId}/editar → arcos/formularioeditar.html
POST /procesos/{procesoId}/arcos/{arcoId}        → redirect a /procesos/{procesoId}
```

El formulario llega precargado con el origen, el destino, la etiqueta y la condición actuales, y
ofrece los nodos activos del proceso. Validación con Bean Validation y errores junto a cada campo.

## API REST

```
PUT /api/procesos/{procesoId}/arcos/{arcoId}
```

| Situación | Respuesta |
|---|---|
| Edición correcta | `200 OK` + cuerpo |
| Cuerpo incompleto | `400 Bad Request` |
| Nodo inexistente, inactivo o de otro proceso | `400` `NODO_NO_VALIDO` |
| Condición que no corresponde, o ausente cuando se exige | `400` `CONDICION_NO_VALIDA` |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| Rol sin permiso, o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Arco o proceso inexistente o eliminado | `404` `RECURSO_NO_ENCONTRADO` |
| Otro arco activo con el mismo par | `409` `ARCO_DUPLICADO` |

## Aislamiento entre empresas

Igual que en HU-11: la empresa sale del usuario autenticado, el arco debe pertenecer a un proceso
suyo y los nodos nuevos deben pertenecer a ese mismo proceso. Ningún identificador de empresa llega
del cliente.

## Pruebas

- `ArcoServiceTest`: cambio de origen, de destino, de etiqueta y de condición; edición sin cambios
  que no guarda ni registra historial; conservación del identificador; duplicado con otro arco;
  origen igual a destino; arco eliminado; proceso eliminado; rol sin permiso; historial con el
  texto exacto; advertencias del gateway anterior y del nuevo.
- `EditarArcoDtoTest`: validación del formulario.
- `ArcoControllerTest`: el formulario llega precargado y el envío delega en el servicio.
- `ArcoRestControllerTest`: 200, 400 y 404.
- `SeguridadArcosTest`: roles y CSRF sobre la edición.
- `ArcosYGatewaysIntegracionTest`: contra PostgreSQL, el historial de la edición y la persistencia
  de los cambios.

## Cómo demostrarla

1. Abrir un proceso con un arco y pulsar **Editar** sobre él en la lista de arcos.
2. Cambiar el destino y guardar: el flujo dibuja la línea hacia el nodo nuevo.
3. Entrar al historial: aparece una entrada con **solo los campos modificados**.
4. Volver a guardar sin cambiar nada: el historial **no crece**.
5. Poner como destino el mismo nodo que el origen: se rechaza.
6. Con un usuario `SOLO_LECTURA`, el enlace no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Editar el proceso al que pertenece el arco**: no lo pide ningún criterio y rompería el
  aislamiento.
- **Eventos como extremo**: depende de HU-25 y HU-27, igual que en HU-11.
