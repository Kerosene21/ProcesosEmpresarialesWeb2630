# HU-14 · Crear gateway

## Qué pide la historia

Como usuario con permisos de edición, quiero añadir un punto de decisión o de paralelismo al
diagrama.

Criterios de aceptación:

| Criterio | Estado |
|---|---|
| Tipos **EXCLUSIVO**, **PARALELO** e **INCLUSIVO** | **Cumplido** |
| El tipo **define el símbolo** | **Cumplido** |
| Un gateway de divergencia debe tener **al menos dos salidas** | **Cumplido**: advertencia en BORRADOR, bloqueo al publicar |
| `EXCLUSIVO` e `INCLUSIVO` exigen **condición en cada arco saliente** | **Cumplido** |
| La creación queda **en el historial** | **Cumplido** |

## Modelo

```java
public enum TipoGateway {
    EXCLUSIVO("X"),
    PARALELO("+"),
    INCLUSIVO("O");

    public String getSimbolo() { ... }

    public boolean exigeCondicion() {
        return this != PARALELO;
    }
}
```

El símbolo y la regla de condición viven **en el enum**, no repartidos por servicios y plantillas:
son propiedades del tipo, no de quien lo usa.

```java
@Entity
@Table(name = "gateway", indexes = @Index(name = "ix_gateway_proceso", columnList = "proceso_id, activo"))
public class Gateway {
    private Long id;
    private TipoGateway tipo;   // nullable = false, length 20
    private Proceso proceso;    // @ManyToOne LAZY, nullable = false
    private Integer posicionX;  // nullable = false
    private Integer posicionY;  // nullable = false
    private boolean activo;     // nullable = false

    public String etiqueta() {
        return "Gateway " + tipo + " #" + id;
    }
}
```

**El gateway no tiene nombre.** Los criterios no lo piden y añadirlo obligaría a inventar reglas de
unicidad que nadie pidió. Para identificarlo en los desplegables, en el historial y en las
advertencias se usa `etiqueta()`, derivada del tipo y del identificador: `Gateway EXCLUSIVO #12`.

`activo` nace en `true` y **hoy nadie lo pone en `false`**: la eliminación de gateways es HU-16. El
campo existe desde ahora para que esa historia sea una marca lógica más, coherente con procesos,
actividades y arcos, y no una migración de esquema. El repositorio ya filtra por `activo = true` en
todas sus consultas.

## Posición visual

`posicionX` y `posicionY` se guardan igual que en `Actividad`, y el detalle del proceso dibuja el
rombo en esa posición dentro del SVG del flujo:

```html
<polygon class="nodo-gateway"
         th:attr="points=${gateway.posicionX + 23} + ',' + ${gateway.posicionY} + ' ' ...">
</polygon>
<text class="gateway-simbolo" text-anchor="middle" th:text="${gateway.simbolo}">X</text>
```

Los cuatro vértices se calculan sobre un lado de 46 píxeles y el símbolo va centrado. No hay editor
de arrastrar y soltar: la posición se escribe en el formulario, que es suficiente para demostrar
que el diagrama la respeta.

## Flujo

```
POST /procesos/{procesoId}/gateways       → GatewayController.crear
POST /api/procesos/{procesoId}/gateways   → GatewayRestController.crear → 201

                                 └─ GatewayService.crear(procesoId, dto, username)
                                      ├─ AccesoProcesoService.usuarioAutenticado(username)
                                      ├─ validarRolDeEscritura     → SOLO_LECTURA → 403
                                      ├─ procesoActivoDeLaEmpresa  → otra empresa → 403
                                      │                            → eliminado    → 404
                                      ├─ GatewayRepository.save (activo = true)
                                      ├─ HistorialProcesoRepository.save
                                      └─ advertencias de consistencia
```

`crear` es `@Transactional`: el gateway y su entrada de historial se confirman juntos.

**No se inventan arcos.** Un gateway recién creado no tiene salidas, y eso es normal mientras el
proceso está en `BORRADOR`: los arcos se añaden después, con HU-11.

## La regla de divergencia: advertencia mientras se modela, bloqueo al publicar

El criterio dice que un gateway usado como divergencia debe tener al menos dos salidas. Esa
condición **no se puede exigir en el momento de crearlo**, porque en ese momento no tiene ninguna;
hacerlo impediría crear gateways. Tampoco al añadir el primer arco, porque entonces no se podría
llegar nunca al segundo.

La regla se garantiza por tanto en **dos niveles**:

| Momento | Comportamiento |
|---|---|
| Proceso en `BORRADOR` | Se permiten estados intermedios (0 salidas, 1 salida). Cada consulta devuelve **advertencias visibles**. |
| Transición `BORRADOR` → `PUBLICADO` | **Se bloquea** con `ModeloDeProcesoNoValidoException` si algún gateway incumple. |

### Advertencias durante el borrador

`ConexionesService.advertenciasDeGateway` evalúa la configuración a partir de los arcos salientes
activos y devuelve texto, nunca excepciones:

| Situación | Texto |
|---|---|
| Menos de dos salidas | `... tiene 1 arco de salida: como divergencia necesita al menos dos.` |
| `EXCLUSIVO`/`INCLUSIVO` con alguna salida sin condición | `... tiene arcos de salida sin condición: un gateway exclusivo o inclusivo la exige en cada salida.` |
| `EXCLUSIVO` con dos salidas que repiten la misma condición | `... repite la condición 'x' en más de una salida: esas condiciones no son mutuamente excluyentes.` |
| `EXCLUSIVO` con dos o más condiciones distintas | `...: revisa que las condiciones de sus salidas sean mutuamente excluyentes, porque solo una puede cumplirse.` |

**Dónde se ven:** al crear o editar un gateway y al crear o editar un arco que sale de uno, viajan
como mensaje de vuelta al detalle del proceso; y el propio detalle muestra, en cada carga, la
sección **Advertencias de consistencia del modelo** con las de todos los gateways activos
(`GatewayService.advertenciasDelProceso`).

### Bloqueo al salir de borrador

`EstadoProceso` tiene dos valores, `BORRADOR` y `PUBLICADO`, y la única transición entre ellos es
`ProcesoService.editar`. Ahí es donde el modelo incompleto deja de ser aceptable:

```java
if (saleDeBorrador(proceso, dto)) {
    validacionModeloService.validarParaSalirDeBorrador(proceso);
}
```

`ValidacionModeloService` recorre los gateways activos del proceso y **rechaza la publicación** si
alguno incumple:

| Regla | Cuándo falla |
|---|---|
| Divergencia | el gateway tiene **exactamente una** salida activa |
| `EXCLUSIVO` / `INCLUSIVO` | alguna salida activa **sin condición** |
| `PARALELO` | alguna salida activa **con condición** |

**Un gateway con cero salidas no se considera divergencia y no bloquea nada**: no se está usando
para bifurcar. La regla del criterio habla de un gateway *de divergencia*, y un gateway sin salidas
todavía no lo es.

Cuando algo falla, el proceso **no cambia de estado, no se guarda y no deja historial**; el mensaje
lista todos los problemas encontrados:

```
El proceso no puede salir de borrador: Gateway EXCLUSIVO #12 se usa como divergencia con una sola
salida: necesita al menos dos; Gateway EXCLUSIVO #12 tiene arcos de salida sin condición
```

La API lo traduce a `400 MODELO_NO_VALIDO` y la web a la página de problema.

### Por qué un servicio aparte

`ProcesoService` no debía aprender a leer gateways ni arcos. `ValidacionModeloService` depende solo
de `GatewayRepository` y de `ConexionesService`, y nadie de esa cadena depende de `ProcesoService`,
así que el grafo sigue siendo acíclico:

```
ValidacionModeloService  (GatewayRepository, ConexionesService)
        ▲
ProcesoService
```

Un gateway que **converge** (varias entradas, una salida) también recibe la advertencia y el bloqueo
de divergencia: el modelo no distingue todavía entre gateways de divergencia y de convergencia, y
ninguna historia pide esa distinción. Es una limitación consciente, no un descuido.

## Condiciones en los arcos salientes

Que `EXCLUSIVO` e `INCLUSIVO` exijan condición en cada salida se cumple en **dos momentos
distintos**:

1. **Al crear o editar el arco** (HU-11 y HU-12): un arco que sale de un gateway `EXCLUSIVO` o
   `INCLUSIVO` sin condición se rechaza con `400 CONDICION_NO_VALIDA`. Un arco que sale de un
   `PARALELO` con condición también.
2. **Al cambiar el tipo del gateway** (HU-15): pasar a `EXCLUSIVO` o `INCLUSIVO` obliga a que todas
   las salidas terminen con condición, y pasar a `PARALELO` las elimina.

Así, **ningún arco activo puede quedar con una condición incoherente con su gateway de origen**. La
advertencia de «salidas sin condición» cubre el caso residual de un gateway consultado antes de que
sus arcos estén completos.

## No hay motor de ejecución

Las condiciones son **texto libre**. El proyecto documenta procesos, no los ejecuta: no se evalúan,
no se compilan y no se comprueba matemáticamente si dos son excluyentes. Lo único que se detecta es
la repetición literal de una condición, normalizando espacios y mayúsculas. Todo lo demás es una
advertencia para que lo revise una persona.

## Permisos

| Rol | Crear gateway |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ✅ |
| `SOLO_LECTURA` | ❌ |

La regla está en `GatewayService` y, para las rutas MVC, en `SecurityConfig`
(`GET /procesos/*/gateways/nuevo` y `POST /procesos/*/gateways`).

**No se implementó `DELETE` de gateway**: pertenece a HU-16 y no hay ruta, ni método de servicio, ni
enlace en la interfaz.

## Interfaz

```
GET  /procesos/{procesoId}/gateways/nuevo → gateways/formulario.html
POST /procesos/{procesoId}/gateways       → redirect a /procesos/{procesoId}
```

El formulario pide el tipo —con su símbolo al lado, `EXCLUSIVO (X)`— y la posición. Al guardar, el
detalle muestra el rombo y, si el gateway todavía no tiene salidas, la advertencia correspondiente.

Ningún identificador de empresa llega del cliente.

## API REST

```
POST /api/procesos/{procesoId}/gateways
```

| Situación | Respuesta |
|---|---|
| Creación correcta | `201 Created` + `Location` + cuerpo |
| Cuerpo incompleto | `400 Bad Request` |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| Rol sin permiso, o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Proceso inexistente o eliminado | `404` `RECURSO_NO_ENCONTRADO` |

El cuerpo incluye `tipo`, `simbolo`, `etiqueta`, la posición y la lista `advertencias`.

## Historial

```
gateway creado: EXCLUSIVO #12 en (300, 120)
```

Con fecha, usuario y estado del proceso en ese momento, como el resto de entradas desde HU-05.

## Aislamiento entre empresas

El proceso se resuelve desde el usuario autenticado con `procesoActivoDeLaEmpresa`. Un usuario de
otra empresa recibe 403 al crear, al consultar y al editar.

## Pruebas

- `GatewayServiceTest`: administrador y editor crean, solo lectura no; los tres tipos con su
  símbolo; la posición se guarda; proceso eliminado; proceso de otra empresa; historial con su texto
  exacto; advertencia de divergencia incompleta; advertencias del proceso completo.
- `ConexionesServiceTest`: gateway paralelo sin condiciones, inclusivo con una salida sin condición
  e inclusivo que no exige exclusividad.
- `GatewayDtoTest`: validación del formulario, símbolos y `exigeCondicion` de cada tipo.
- `GatewayControllerTest`, `GatewayRestControllerTest`, `SeguridadGatewaysTest`: formulario MVC,
  contrato REST, roles, CSRF y 401 en JSON.
- `DiagramaFlujoTest`: el rombo, sus vértices y su símbolo en el SVG.
- `ValidacionModeloServiceTest`: proceso sin gateways, gateway sin salidas que no estorba, gateway
  con una sola salida que bloquea, exclusivo e inclusivo con salidas sin condición, paralelo con
  condiciones, y varios problemas acumulados en un mismo gateway.
- `ProcesoServiceTest`: la transición fuera de borrador llama al validador, un modelo incompleto
  impide publicar sin guardar ni dejar historial, y una edición que se queda en borrador -o que parte
  de un proceso ya publicado- no vuelve a validar.
- `ApiExceptionHandlerTest` y `MvcExceptionHandlerTest`: `400 MODELO_NO_VALIDO` y página de problema.
- `ArcosYGatewaysIntegracionTest`: contra PostgreSQL, persistencia con tipo y posición, relación con
  el proceso y dibujo de extremo a extremo. Además, publicar con una sola
  salida falla, con dos salidas condicionadas funciona, un proceso sin gateways se publica sin
  restricciones y un paralelo con dos salidas sin condición también.

## Cómo demostrarla

1. Abrir un proceso como **editor** y pulsar **Agregar gateway**.
2. Elegir `EXCLUSIVO`, dar una posición y guardar: aparece un rombo con una **X** y un aviso de que
   todavía no tiene dos salidas.
3. Añadir dos arcos desde ese gateway, cada uno con su condición: el aviso de divergencia desaparece
   y queda el recordatorio de exclusividad.
4. Intentar pasar el proceso a **PUBLICADO** con una sola salida: se rechaza con el detalle del
   problema y el proceso sigue en borrador. Con las dos salidas condicionadas, se publica.
4. Entrar al historial: la creación aparece con su autor y su fecha.
5. Con un usuario `SOLO_LECTURA`, el enlace no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Eliminar gateways**: es HU-16. El campo `activo` ya está preparado.
- **Distinguir divergencia de convergencia**: el modelo no lo hace y ninguna historia lo pide; la
  regla de dos salidas se aplica a cualquier gateway que tenga salidas.
- **Evaluar las condiciones**: el proyecto no ejecuta procesos.
