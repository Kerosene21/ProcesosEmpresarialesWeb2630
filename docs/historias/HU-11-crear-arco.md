# HU-11 · Crear arco

## Qué pide la historia

Como usuario con permisos de edición, quiero conectar dos elementos del diagrama para representar
el orden en que ocurren.

Criterios de aceptación:

| Criterio | Estado |
|---|---|
| El origen y el destino son **nodos del proceso** | **Cumplido** |
| Pueden ser **actividades, gateways o eventos** | **Cumplido para actividades y gateways**; eventos, pendiente |
| **Origen distinto de destino** | **Cumplido** |
| **No cruza pools** | **Cumplido** |
| **No se duplica** el mismo par origen-destino | **Cumplido** |
| Aparece en el diagrama como **línea continua con punta sólida** | **Cumplido** |

> El único criterio abierto es el de los **eventos**, y lo está por una razón concreta: la entidad
> `Evento` no existe todavía en el dominio. Ver «Dependencia con los eventos».

## Cómo se referencia un nodo

Un arco tiene que poder apuntar a cosas distintas —una actividad, un gateway y mañana un evento—
sin obligar a que todas compartan una jerarquía JPA. La alternativa habitual sería convertir
`Actividad` en subclase de un `NodoFlujo` con `@Inheritance`, pero eso significa **rehacer la tabla
`actividad` que HU-08 a HU-10 ya dejaron estable y probada**, y no aporta nada a este bloque.

La solución elegida es una **referencia polimórfica explícita**: el arco guarda el *tipo* del nodo y
su *identificador*.

```java
public enum TipoNodoFlujo {
    ACTIVIDAD,
    GATEWAY,
    EVENTO
}
```

```java
@Enumerated(EnumType.STRING) @Column(name = "origen_tipo", nullable = false, length = 20)
private TipoNodoFlujo origenTipo;

@Column(name = "origen_id", nullable = false)
private Long origenId;
```

**Lo que se pierde** con esta decisión es la integridad referencial que daría una clave foránea:
la base de datos no puede comprobar que `origen_id` exista. **Lo que se gana** es que `Actividad`
no cambia una sola línea, que añadir `Evento` en HU-25/HU-27 solo requiere un caso más en el
resolutor, y que un arco puede unir tipos distintos sin tabla intermedia.

La comprobación que la base no hace la hace **`NodoFlujoResolver`**, en cada creación y en cada
edición:

```java
public NodoFlujo resolverActivo(Proceso proceso, TipoNodoFlujo tipo, Long nodoId) {
    if (tipo == TipoNodoFlujo.EVENTO) {
        throw new NodoFlujoNoValidoException(EVENTO_SIN_MODELO);
    }
    NodoFlujo nodo = buscar(proceso, tipo, nodoId)
            .orElseThrow(() -> new NodoFlujoNoValidoException(NODO_NO_EXISTE));
    if (!nodo.activo()) {
        throw new NodoFlujoNoValidoException(NODO_INACTIVO);
    }
    return nodo;
}
```

`buscar` consulta siempre **por identificador y proceso a la vez**
(`findByIdAndProcesoId`), así que un nodo de otro proceso simplemente no aparece. Devuelve un
`NodoFlujo`, un record de solo lectura con lo que el resto del código necesita: tipo, id, nombre,
posición, si está activo y, cuando es un gateway, su `TipoGateway`.

## No cruzar pools

En este modelo **un proceso tiene exactamente un pool** (`Proceso.pool` es `@OneToOne`, desde
HU-04). Por tanto, validar que los dos extremos pertenecen al mismo proceso **es** validar que no
se cruzan pools: no existe forma de construir un nodo que esté en el proceso y fuera de su pool.

Cuando HU-21 permita varios pools por proceso, este criterio necesitará una comprobación adicional
sobre el pool concreto de cada nodo. Hoy sería una condición imposible de falsear y por eso no se
escribió.

## Modelo

```java
@Entity
@Table(name = "arco", indexes = {
        @Index(name = "ix_arco_proceso_activo", columnList = "proceso_id, activo"),
        @Index(name = "ix_arco_origen", columnList = "proceso_id, origen_tipo, origen_id, activo"),
        @Index(name = "ix_arco_destino", columnList = "proceso_id, destino_tipo, destino_id, activo") })
public class Arco {
    private Long id;
    private Proceso proceso;        // @ManyToOne LAZY, nullable = false
    private TipoNodoFlujo origenTipo;   // nullable = false, length 20
    private Long origenId;              // nullable = false
    private TipoNodoFlujo destinoTipo;  // nullable = false, length 20
    private Long destinoId;             // nullable = false
    private String etiqueta;            // opcional, máx. 150
    private String condicion;           // opcional, máx. 200
    private boolean activo;             // nullable = false
}
```

`activo = true` es un arco vigente; `activo = false` es la eliminación lógica de HU-13. **La fila
nunca se borra.**

### Por qué no hay `@UniqueConstraint` para el par origen-destino

Porque la regla no es «no puede haber dos arcos iguales», sino «no puede haber dos arcos **activos**
iguales». Una restricción única sobre las cinco columnas impediría volver a crear un arco que se
eliminó antes, que es justo lo que HU-13 debe permitir. Expresar «único cuando `activo`» exige un
índice parcial (`CREATE UNIQUE INDEX ... WHERE activo`), que es sintaxis específica de PostgreSQL y
no se puede declarar con `@Table(uniqueConstraints = ...)`.

La regla vive por tanto **en el servicio**, y los tres índices declarados existen para que esa
comprobación y el dibujado del diagrama no recorran la tabla entera:

```java
boolean repetido = arcoRepository
        .existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrue(...);
```

`A -> B` y `B -> A` son arcos distintos y los dos son válidos: un retorno en el flujo es legítimo.
Lo que se rechaza es repetir exactamente el mismo par.

## Flujo

```
POST /procesos/{procesoId}/arcos       → ArcoController.crear
POST /api/procesos/{procesoId}/arcos   → ArcoRestController.crear → 201

                                 └─ ArcoService.crear(procesoId, dto, username)
                                      ├─ AccesoProcesoService.usuarioAutenticado(username)
                                      ├─ validarRolDeEscritura       → SOLO_LECTURA → 403
                                      ├─ procesoActivoDeLaEmpresa    → otra empresa → 403
                                      │                              → eliminado    → 404
                                      ├─ resolverActivo(origen)      → no existe / inactivo / evento → 400
                                      ├─ resolverActivo(destino)     → idem
                                      ├─ validarExtremosDistintos    → origen == destino → 400
                                      ├─ validarQueNoEsteRepetido    → duplicado activo  → 409
                                      ├─ condicionValidada(origen)   → condición inválida → 400
                                      ├─ ArcoRepository.save (activo = true)
                                      ├─ HistorialProcesoRepository.save
                                      └─ advertencias del gateway de origen
```

`crear` es `@Transactional`: el arco y su entrada de historial se confirman juntos.

## Condiciones

La condición no es un campo libre: **depende de quién sea el origen**.

| Origen | Condición |
|---|---|
| Actividad | debe llegar vacía; si llega con texto, `400 CONDICION_NO_VALIDA` |
| Gateway `EXCLUSIVO` | **obligatoria** |
| Gateway `INCLUSIVO` | **obligatoria** |
| Gateway `PARALELO` | debe llegar vacía; si llega con texto, `400 CONDICION_NO_VALIDA` |

Se eligió **rechazar** en lugar de ignorar en silencio: si alguien escribió una condición para un
arco que no la admite, es un error de modelado y conviene decirlo, no descartar el texto sin avisar.
La única excepción es el **cambio de tipo de un gateway a `PARALELO`** (HU-15), donde el criterio
pide explícitamente eliminar las condiciones existentes.

Una condición en blanco (`"   "`) equivale a ausente, y una condición con texto se guarda con
`trim`. **La etiqueta es independiente de la condición**: sirve para nombrar el arco («solicitud
completa») y se admite venga de donde venga.

## Advertencias de consistencia

Crear un arco desde un gateway devuelve, junto al DTO, las advertencias del gateway de origen: si
todavía tiene menos de dos salidas, o si alguna salida quedó sin condición. **No bloquean nada**;
son informativas y se muestran como mensaje al volver al detalle del proceso. La regla completa
está en HU-14.

## Permisos

| Rol | Crear arco |
|---|---|
| `ADMINISTRADOR` | ✅ |
| `EDITOR` | ✅ |
| `SOLO_LECTURA` | ❌ |

La regla está en `ArcoService`, y `SecurityConfig` corta antes las rutas MVC:

```java
.requestMatchers(HttpMethod.GET, "/procesos/*/arcos/nuevo", "/procesos/*/arcos/*/editar")
.hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
.requestMatchers(HttpMethod.POST, "/procesos/*/arcos", "/procesos/*/arcos/*")
.hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
```

## Interfaz

```
GET  /procesos/{procesoId}/arcos/nuevo → arcos/formulario.html
POST /procesos/{procesoId}/arcos       → redirect a /procesos/{procesoId}
```

El formulario pide el **tipo y el identificador** de cada extremo, la etiqueta y la condición. El
desplegable de nodos lista las actividades y los gateways activos del proceso con su tipo delante
(`ACTIVIDAD · Revisar solicitud`), de modo que se ve qué tipo corresponde a cada opción.

> **Simplificación consciente de la interfaz.** El tipo y el nodo son dos selectores separados, así
> que es posible enviar una combinación que no existe (tipo `GATEWAY` con el identificador de una
> actividad). El servicio la rechaza con un mensaje claro —`El nodo indicado no existe en este
> proceso`— porque la consulta se hace siempre por el par completo. Filtrar el segundo selector en
> función del primero exigiría JavaScript, que este bloque no introduce.

**Ningún identificador de empresa llega del cliente**: el proceso se resuelve desde el usuario
autenticado.

## API REST

```
POST /api/procesos/{procesoId}/arcos
```

| Situación | Respuesta |
|---|---|
| Creación correcta | `201 Created` + `Location` + cuerpo |
| Cuerpo incompleto | `400 Bad Request` |
| Nodo inexistente, inactivo, de otro proceso, o evento | `400` `NODO_NO_VALIDO` |
| Condición que no corresponde, o ausente cuando se exige | `400` `CONDICION_NO_VALIDA` |
| Sin sesión | `401` `USUARIO_NO_AUTORIZADO` (JSON) |
| Rol sin permiso, o proceso de otra empresa | `403` `USUARIO_SIN_PERMISO` |
| Proceso inexistente o eliminado | `404` `RECURSO_NO_ENCONTRADO` |
| Arco activo repetido | `409` `ARCO_DUPLICADO` |

El cuerpo de respuesta incluye los nombres de los extremos, las coordenadas calculadas para
dibujarlo y la lista `advertencias`.

## Dibujo

El detalle del proceso añadió una sección **Flujo**: un `<svg>` donde las actividades son
rectángulos en su posición, los gateways son rombos y los arcos son **líneas continuas** con un
`marker-end` que pinta un **triángulo relleno**:

```html
<marker id="punta-de-arco" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
    <polygon class="arco-punta" points="0 0, 10 4, 0 8"></polygon>
</marker>
```

Los extremos visuales los calcula `GeometriaArco` a partir de las posiciones guardadas: el arco
sale del **centro** del nodo origen y termina en el **borde** del nodo destino, para que la punta
quede visible y no debajo del rectángulo. Es una aproximación por caja envolvente, suficiente para
demostrar la semántica del diagrama; no es un enrutado BPMN.

No se añadió Canvas, ni librerías gráficas, ni JavaScript.

## Aislamiento entre empresas

El proceso se obtiene siempre con `procesoActivoDeLaEmpresa`, que compara la empresa del proceso con
la del usuario autenticado. Los nodos se buscan **dentro de ese proceso**, así que un identificador
de otra empresa no resuelve. Hay pruebas de las dos cosas.

## Dependencia con los eventos

`TipoNodoFlujo` ya incluye `EVENTO` y las columnas del arco lo admiten sin cambios de esquema. Lo
que **no existe** es la entidad `Evento`: nace con HU-25 y HU-27.

Por eso `NodoFlujoResolver.resolverActivo` rechaza `EVENTO` con un mensaje explícito —*«Los eventos
todavía no existen en el modelo del proceso»*— en lugar de fingir que funciona. **No se creó una
tabla de eventos ficticia solo para marcar el criterio.** Cuando exista la entidad, cerrar este
criterio es añadir un `case EVENTO` en `buscar` y quitar la guarda; ni el modelo del arco, ni el
servicio, ni el diagrama cambian.

## Pruebas

- `ArcoServiceTest`: los tres roles, origen igual a destino, duplicado activo, nodo de otro proceso,
  proceso de otra empresa, proceso eliminado, nodo inactivo, gateway inactivo, evento rechazado,
  etiqueta normalizada, las cuatro reglas de condición, historial con su texto exacto, coordenadas
  de los extremos y advertencias del gateway de origen.
- `NodoFlujoResolverTest`: resolución de actividades y gateways, evento pendiente, nodo inactivo,
  índice de nodos y descripción de un nodo desaparecido.
- `GeometriaArcoTest`: centros, llegada horizontal, vertical y diagonal, y nodos superpuestos.
- `CrearArcoDtoTest`: validación del formulario.
- `ArcoControllerTest`, `ArcoRestControllerTest`, `SeguridadArcosTest`: formulario MVC, contrato
  REST, roles, CSRF y 401 en JSON.
- `DiagramaFlujoTest`: el SVG con la línea, la punta y el rombo.
- `ArcosYGatewaysIntegracionTest`: contra PostgreSQL, persistencia, duplicados, aislamiento y
  dibujo de extremo a extremo.

## Cómo demostrarla

1. Abrir un proceso con al menos dos actividades y pulsar **Agregar arco**.
2. Elegir origen y destino, guardar: el flujo dibuja la línea con su punta.
3. Repetir el mismo par: se rechaza por duplicado.
4. Elegir el mismo nodo como origen y destino: se rechaza.
5. Con un usuario `SOLO_LECTURA`, el enlace no aparece y la ruta directa responde 403.

## Qué quedó pendiente

- **Eventos como extremo del arco**: depende de HU-25 y HU-27. El diseño ya los admite.
- **Varios pools por proceso**: hoy el proceso tiene uno solo, así que «no cruzar pools» equivale a
  «mismo proceso». Con HU-21 habrá que comprobar el pool de cada nodo.
- **Selector de nodos filtrado por tipo**: exigiría JavaScript; el servicio valida el par.
