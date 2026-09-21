# HU-07 · Consultar procesos

## Qué pide la historia

Como usuario de la empresa, quiero ver la lista de procesos existentes para acceder a sus detalles.

Criterios de aceptación:

- Lista con búsqueda por nombre.
- Filtros por estado.
- Filtros por categoría.
- El listado es paginado.
- Solo se muestran procesos de la empresa del usuario autenticado.
- Al abrir un proceso se visualiza su diagrama completo: eventos, actividades, arcos, gateways,
  pools y lanes.
- Se puede consultar el historial de cambios.

HU-06 añade además que los procesos inactivos no aparezcan por defecto y puedan consultarse con un
filtro.

> **Aviso sobre el último criterio del diagrama.** Seis de los siete criterios quedan cerrados en
> este bloque. El de la visualización del diagrama **no**, y no por falta de vista: el modelo de
> dominio todavía no tiene eventos, actividades, arcos, gateways ni lanes. El detalle muestra lo
> único que existe hoy —el pool— y lo dice explícitamente. Ver «Estado real del diagrama BPMN».

## Listado por empresa

```
GET /procesos?q=&estado=&categoria=&visibilidad=&page=
```

La empresa **no es un parámetro**: se resuelve siempre desde el usuario autenticado
(`usuario.getEmpresa().getId()`) y entra en la consulta como primera condición. No existe ninguna
forma de pedir procesos de otra empresa, ni por URL ni cambiando filtros.

No se usa `findAll()` con filtrado posterior en Java: la consulta llega a la base ya restringida.

## Repository

`ProcesoRepository` pasó a extender `JpaSpecificationExecutor<Proceso>` y la combinación de filtros
se construye en `ProcesoSpecifications`.

**Por qué Specification y no JPQL con parámetros opcionales.** La alternativa habitual es una
`@Query` con condiciones del estilo `(:estado is null or p.estado = :estado)`. Se descartó por una
razón concreta: ese patrón depende de que el proveedor infiera el tipo de un parámetro nulo, y es
justo el tipo de detalle que funciona en un motor y sorprende en otro. **En este equipo no hay
PostgreSQL disponible**, así que no se puede comprobar en local. Con `Specification`, cada filtro
ausente simplemente **no genera predicado**: el SQL solo contiene las condiciones realmente usadas y
no hay ningún parámetro nulo que tipar.

```java
public static Specification<Proceso> deLaEmpresaCon(Long empresaId, FiltroProcesosDto filtro) {
    List<Specification<Proceso>> especificaciones = new ArrayList<>();
    especificaciones.add(deLaEmpresa(empresaId));
    agregarSiAplica(especificaciones, conVisibilidad(filtro.getVisibilidad()));
    agregarSiAplica(especificaciones, conNombreParecidoA(filtro.getQ()));
    agregarSiAplica(especificaciones, conEstado(filtro.getEstado()));
    agregarSiAplica(especificaciones, conCategoria(filtro.getCategoria()));
    return Specification.allOf(especificaciones);
}
```

Se usa `Specification.allOf` y no encadenar `.and(...)`: en Spring Data 4 `and(null)` lanza
`IllegalArgumentException`, cosa que detectaron las pruebas antes de llegar a CI.

Sí se añadió **una consulta JPQL**, donde aporta de verdad porque un método derivado no la expresa:

```java
@Query("select distinct p.categoria from Proceso p where p.empresa.id = :empresaId order by p.categoria")
List<String> categoriasDeLaEmpresa(@Param("empresaId") Long empresaId);
```

Alimenta el selector de categorías y también está acotada por empresa.

No se creó ninguna cascada de métodos `findByEmpresaAndNombreAndEstadoAnd...`.

## Búsqueda por nombre

- **Parcial**: `like '%texto%'`.
- **Insensible a mayúsculas**: se comparan `lower(nombre)` y el patrón en minúsculas.
- **Combinable** con el resto de filtros.

Buscar `venta` encuentra `Proceso de Ventas` **si pertenece a la empresa del usuario**; si es de
otra empresa no aparece, y hay una prueba dedicada a ese caso.

El texto se recorta y, si queda vacío, se trata como filtro ausente.

## Filtro por estado

`BORRADOR`, `PUBLICADO` o sin filtro (opción «Todos los estados»).

`EstadoProceso` y el indicador `eliminado` son **dimensiones distintas** y se filtran por separado:
un proceso puede estar `PUBLICADO` y a la vez inactivo. El formulario las presenta como dos
selectores independientes, «Estado» y «Situación».

## Filtro por categoría

Se mantiene el campo `String` existente; **no se creó ninguna entidad `Categoria`**.

La estrategia es un **selector poblado con las categorías que la empresa ya usa**, obtenidas con la
consulta JPQL de arriba, y la comparación es de igualdad **insensible a mayúsculas**, de modo que
también funciona si alguien escribe la categoría a mano en la URL.

## Procesos activos e inactivos

El filtro «Situación» tiene tres valores, representados por `VisibilidadProceso`:

| Valor | Efecto |
|---|---|
| `ACTIVOS` | **Por defecto.** Solo `eliminado = false` |
| `INACTIVOS` | Solo `eliminado = true` |
| `TODOS` | Sin condición sobre `eliminado` |

Si no se indica visibilidad —o llega vacía— el servicio la normaliza a `ACTIVOS`, así que el
comportamiento por defecto cumple HU-06 incluso ante una URL manipulada.

Un proceso inactivo sigue siendo **consultable**: se abre su detalle y su historial, aparece marcado
como *Inactivo* en el listado, y no se puede editar ni volver a eliminar.

## Paginación

Con `Page` y `Pageable` de Spring Data; no hay paginación manual en Java.

- Tamaño **fijo de 10** por página (`TAMANO_PAGINA`).
- Orden estable por `nombre` ascendente, para que la paginación sea determinista.
- Los enlaces *Anterior* y *Siguiente* **conservan** búsqueda, estado, categoría y situación.
- Cambiar un filtro y pulsar «Buscar» vuelve a la primera página, que es lo esperable: el formulario
  no arrastra el número de página.

**Página fuera de rango**: Spring Data devuelve una página vacía, no un error. Un número **negativo**
se corrige a `0` antes de construir el `PageRequest`, porque `PageRequest.of(-1, ...)` sí lanzaría
excepción. Ambos casos están probados.

## Service

```java
@Transactional(readOnly = true)
public Page<ProcesoResumenDto> consultarProcesos(FiltroProcesosDto filtro, String username)

@Transactional(readOnly = true)
public List<String> categoriasDisponibles(String username)
```

`consultarProcesos` resuelve el usuario, normaliza los filtros (recorta textos, vacío a nulo,
visibilidad por defecto, página mínima 0), construye la paginación y devuelve DTO. `obtener` y
`consultarHistorial` no se tocaron.

## DTO

| DTO | Papel |
|---|---|
| `FiltroProcesosDto` | Entrada del listado: `q`, `estado`, `categoria`, `visibilidad`, `page` |
| `ProcesoResumenDto` | Fila del listado: `id`, `nombre`, descripción resumida, `categoria`, `estado`, `eliminado` |
| `VisibilidadProceso` | Enumerado del filtro de situación |

El resumen recorta la descripción a 120 caracteres con puntos suspensivos; el texto completo sigue
estando en el detalle. `page` es `Integer` a propósito: así una URL con `?page=` no provoca un error
de conversión.

## Controller

```java
@GetMapping
public String lista(@ModelAttribute("filtro") FiltroProcesosDto filtro, Principal principal, Model model)
```

El filtro se enlaza directamente desde los parámetros y vuelve al modelo, de modo que la vista
repuebla el formulario sin trabajo extra. **No se acepta ningún parámetro de empresa.**

Al modelo van `procesos` (el `Page`), `filtro`, `categorias`, `estados` y `visibilidades`.
`GET /procesos/{id}` y `GET /procesos/{id}/historial` siguen igual.

## Vista

`templates/procesos/lista.html` permite buscar por nombre, filtrar por estado, categoría y
situación, limpiar filtros, navegar páginas y abrir el detalle. La tabla muestra nombre (con la
descripción resumida debajo), categoría, estado y situación *Activo/Inactivo*.

Navegación añadida: enlace **Ver procesos** desde la empresa, **Volver al listado** desde el detalle,
y desde el listado se llega a crear proceso y a la empresa. El redirect de crear → detalle se
mantuvo, porque sigue siendo útil.

## Detalle del proceso y estado real del diagrama BPMN

Esta es la parte que **no se puede declarar cerrada**, y conviene ser explícito.

El dominio actual solo contiene:

| Elemento BPMN del criterio | ¿Existe hoy? |
|---|---|
| Pools | **Sí**, entidad `Pool` con `id` y `nombre` |
| Eventos | No |
| Actividades | No |
| Arcos | No |
| Gateways | No |
| Lanes | No |

El detalle muestra una sección **Diagrama** con el pool del proceso (`poolNombre`, añadido a
`ProcesoRespuestaDto` en este bloque) y un texto que advierte de que los demás elementos llegarán
con las historias de modelado. No se creó ninguno de esos dominios aquí, porque pertenecen a
HU-08 en adelante.

> **HU-07 queda completa para consulta de procesos, búsqueda, filtros, paginación, detalle e
> historial. La visualización del diagrama BPMN completo depende de las historias de modelado
> HU-08 en adelante y pools/lanes.**

## Historial

`GET /procesos/{id}/historial` ya venía de HU-05 y **no se rehízo**. Se verificó que sigue
cumpliendo: accesible para los tres roles, orden por fecha descendente, aislamiento entre empresas y
disponible también para procesos eliminados. Esas pruebas siguen verdes.

## Seguridad y aislamiento

Consultar es una operación de lectura: **los tres roles autenticados pueden usar el listado**,
incluido `SOLO_LECTURA`, y también el filtro de inactivos. Sin sesión, `GET /procesos` redirige a
`/login`.

Las reglas de escritura de HU-04/HU-05/HU-06 no cambiaron: crear y editar siguen siendo de
`ADMINISTRADOR` y `EDITOR`, y eliminar sigue siendo solo de `ADMINISTRADOR`.

Un usuario de la empresa A **no** lista, ni encuentra por búsqueda, ni alcanza cambiando filtros, ni
abre el detalle, ni ve el historial de un proceso de la empresa B. El aislamiento vive en la
consulta, no en un filtro posterior.

## Tests

| Clase | Pruebas de HU-07 | Necesita PostgreSQL |
|---|---|---|
| `ProcesoServiceTest` | 14 | No |
| `ProcesoSpecificationsTest` | 13 | No |
| `SeguridadProcesosTest` | 5 | No |
| `ProcesoControllerTest` | 5 | No |
| `ConsultaProcesosIntegracionTest` | 16 | **Sí** |

**Sobre qué prueba cada nivel, que aquí importa.** Una `Specification` es opaca para un test con
mocks: no se puede afirmar desde `ProcesoServiceTest` qué filas devolvería. El reparto es:

- `ProcesoSpecificationsTest` ejecuta las especificaciones contra un `CriteriaBuilder` simulado y
  comprueba **qué predicados se construyen**: que la empresa siempre está, que `ACTIVOS` compara
  `eliminado = false`, que `TODOS` no añade condición, que el nombre se compara en minúsculas con
  `%texto%`, que un filtro ausente no genera predicado.
- `ConsultaProcesosIntegracionTest` comprueba **qué filas salen** de la base real: aislamiento entre
  empresas, exclusión de eliminados, filtro de inactivos y de todos, búsqueda parcial e insensible a
  mayúsculas, estado, categoría, filtros combinados, paginación de 12 procesos en 2 páginas y página
  fuera de rango.

Es decir: **la semántica final de los filtros se verifica contra PostgreSQL, y eso ocurre en CI**,
igual que el resto de pruebas de persistencia del proyecto.

## Cómo demostrarla

1. Levantar PostgreSQL, definir `DB_PASSWORD` en `.env` y arrancar con `./mvnw spring-boot:run`.
2. Registrar una empresa, iniciar sesión y crear varios procesos con distintas categorías.
3. Publicar alguno desde su formulario de edición.
4. Entrar a **Ver procesos**: aparecen todos los activos, ordenados por nombre, de 10 en 10.
5. Escribir `venta` en el buscador: encuentra los procesos cuyo nombre lo contenga, sin importar
   mayúsculas.
6. Filtrar por **Estado = PUBLICADO** y por una **categoría**: los filtros se combinan y se
   mantienen al paginar.
7. Pulsar **Limpiar filtros** para volver al listado por defecto.
8. Eliminar un proceso (como administrador) y comprobar que **desaparece del listado**.
9. Cambiar **Situación = INACTIVOS**: aparece, marcado como *Inactivo*; su detalle y su historial
   siguen abriéndose, pero sin acciones de edición.
10. Crear más de 10 procesos y navegar con *Siguiente* y *Anterior*.
11. Registrar una segunda empresa en una ventana privada y comprobar que su listado es independiente
    y que buscar el nombre de un proceso ajeno no devuelve nada.

## Qué quedó pendiente

- **Visualización del diagrama BPMN completo.** Depende de HU-08 en adelante, que deben introducir
  eventos, actividades, arcos, gateways y lanes en el dominio. Hoy solo existe el pool.
- **Ordenación configurable.** El listado ordena por nombre ascendente y no es elegible desde la
  interfaz.
- **Tamaño de página configurable.** Está fijo en 10.
- **Búsqueda solo por nombre.** No busca dentro de la descripción; el criterio pedía por nombre.
- **Sin API REST de listado.** HU-07 se expone solo por MVC, como el resto del bloque.
