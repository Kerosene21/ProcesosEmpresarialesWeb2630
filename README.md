# ProcesosEmpresarialesWeb2630

Editor y visualizador de procesos empresariales basado conceptualmente en BPMN.
La aplicación permite registrar empresas y documentar sus procesos; **no ejecuta procesos**.

## Objetivo

Que cada empresa tenga un espacio propio donde registrar, consultar y editar sus procesos,
manteniendo la información de cada organización aislada de las demás.

## Stack

- Java 21
- Spring Boot 4.1.1
- Maven
- Spring MVC
- Spring Data JPA
- Spring Validation
- Spring Security 7.1.1
- Thymeleaf
- PostgreSQL
- ModelMapper
- Lombok

## Arquitectura por capas

```
Entity → Repository → DTO → Service → Controller → Thymeleaf
```

- La lógica de negocio vive en los **Service**.
- Los **Controller** son ligeros: validan la entrada, delegan y eligen la vista.
- Las entidades JPA no se exponen a las vistas; para eso se usan **DTO**.
- El código no lleva comentarios: la explicación está en `docs/`.

### Paquetes

```
co.edu.javeriana.procesosempresariales
├── config      configuración de beans reutilizables
├── controller  controladores MVC y REST, y el manejador de excepciones
├── domain      entidades JPA y enumeraciones
├── dto         objetos de entrada y salida
├── exception   excepciones de negocio
├── repository  repositorios Spring Data
└── service     lógica de negocio
```

## Estado actual

| Historia | Estado |
|---|---|
| HU-01 · Registro de empresa | Implementada |
| HU-02 · Registro de usuario en empresa | Implementada |
| HU-03 · Inicio de sesión | Implementada |
| HU-04 · Crear proceso | Implementada |
| HU-05 · Editar proceso | Implementada |
| HU-06 · Eliminar proceso | Implementada |

Con la autenticación en marcha, las pantallas de proceso que dependen del usuario autenticado ya
son accesibles de extremo a extremo: se registra una empresa, se inicia sesión con las credenciales
del administrador inicial y desde ahí se administran los usuarios y los procesos de esa empresa.

El administrador de cada empresa crea usuarios con rol `ADMINISTRADOR`, `EDITOR` o `SOLO_LECTURA`,
cambia su rol y los desactiva. La desactivación es **lógica** (`activo = false`): el usuario deja de
poder iniciar sesión, pero su cuenta y su historial de ediciones se conservan, y los procesos de la
empresa siguen disponibles. La invitación se hace creando la cuenta con el correo como identificador
de acceso: **no hay envío de correo ni integración SMTP**.

Sobre los procesos, el rol decide qué se puede hacer:

| Rol | Crear | Editar | Eliminar | Consultar proceso e historial |
|---|---|---|---|---|
| `ADMINISTRADOR` | ✅ | ✅ | ✅ | ✅ |
| `EDITOR` | ✅ | ✅ | ❌ | ✅ |
| `SOLO_LECTURA` | ❌ | ❌ | ❌ | ✅ |

Cada edición que cambia algún dato deja una entrada de historial con la fecha, el usuario, el estado
anterior y **solo los campos modificados**; se consulta en `GET /procesos/{id}/historial`.

**Solo el administrador de la empresa elimina procesos, y siempre con confirmación previa**: la
acción abre una página que muestra el proceso y advierte del efecto, y solo el envío de ese
formulario ejecuta la eliminación. La eliminación es **lógica** (`eliminado = true`, la
representación persistente del estado inactivo): el proceso permanece en la base de datos con su
pool, su empresa y todo su historial, la eliminación queda registrada como una entrada más, y el
proceso pasa a ser solo consultable —no se puede editar ni volver a eliminar—. El nombre de un
proceso eliminado **sigue reservado** dentro de su empresa.

HU-07 no está implementada: no hay listado, búsqueda, filtros ni paginación de procesos. Cuando se
implemente, el listado por defecto excluirá los procesos inactivos y tendrá un filtro para
consultarlos. La vista de historial es la evidencia del criterio de HU-05, no HU-07.

## Historias en desarrollo

El bloque actual cubre HU-01 a HU-06. La documentación de cada historia está en
[`docs/historias/`](docs/historias/).

## Requisitos para ejecutar

- Java 21
- PostgreSQL en ejecución
- Maven no es necesario: el proyecto incluye el wrapper (`mvnw`)

### PostgreSQL

Se necesitan dos bases de datos:

| Base | Uso | `ddl-auto` |
|---|---|---|
| `procesos_empresariales` | desarrollo | `update` |
| `procesos_empresariales_test` | pruebas con contexto de Spring | `create-drop` |

Las credenciales se leen de variables de entorno o de un archivo `.env` local que no se versiona.
Para prepararlo, copiar la plantilla y completar la contraseña:

```bash
cp .env.example .env
```

`DB_PASSWORD` es obligatoria: si no está definida, la aplicación no arranca.
La URL de la base de pruebas es fija y no se puede redirigir por variables de entorno, porque
`create-drop` destruye el esquema al terminar.

> **Si la base de desarrollo ya existía antes de HU-03**, hay que migrarla antes de arrancar. La
> tabla `usuario` tiene dos columnas nuevas obligatorias (`password_hash` y `activo`) y `ddl-auto=update`
> no puede añadirlas si la tabla ya tiene filas: registra el error como advertencia, la aplicación
> arranca sin las columnas y el fallo aparece más tarde. Los dos caminos posibles (recrear el
> esquema o migrar de forma aditiva) están en
> [HU-03 · Migración de base de datos](docs/historias/HU-03-inicio-sesion.md#migración-de-base-de-datos).
> En CI no ocurre: el perfil de pruebas usa `create-drop` sobre un contenedor limpio.
>
> La columna `eliminado` que añadió HU-06 a `proceso` **no** tiene ese problema: se declara con
> valor por defecto, así que `ddl-auto=update` la añade sola aunque la tabla ya tenga filas.

### Ejecutar

```bash
./mvnw spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`.

### Pruebas

Las pruebas que no levantan el contexto completo tampoco necesitan base de datos. Eso incluye las
de seguridad, que usan `@WebMvcTest` y sí ejecutan los filtros de Spring Security:

```bash
./mvnw -Dtest='!ProcesosEmpresarialesWeb2630ApplicationTests,!RegistroYLoginIntegracionTest,!GestionUsuariosIntegracionTest,!ProcesosYHistorialIntegracionTest,!EliminacionProcesosIntegracionTest' test
```

La suite completa incluye cinco clases que levantan el contexto de Spring
(`ProcesosEmpresarialesWeb2630ApplicationTests`, `RegistroYLoginIntegracionTest`,
`GestionUsuariosIntegracionTest`, `ProcesosYHistorialIntegracionTest` y
`EliminacionProcesosIntegracionTest`) y sí requieren que
PostgreSQL esté disponible con las credenciales configuradas. En CI corren todas contra el
contenedor `postgres:16-alpine` del workflow.

## Calidad de código

El proyecto se analiza con **SonarQube Cloud**. La cobertura la mide **JaCoCo** durante
`mvn verify` y el análisis lo dispara **GitHub Actions**, no el análisis automático de Sonar.

| Dato | Valor |
|---|---|
| Organization | `kerosene21` |
| Project | `Kerosene21_ProcesosEmpresarialesWeb2630` |

El flujo es:

```
Código → Maven → Tests → JaCoCo → SonarQube Cloud → Quality Gate
```

El **Quality Gate se evalúa en CI**: el workflow corre en cada push a `main` y en cada pull
request hacia `main`, de modo que los cambios de una rama de feature se revisan al abrir el PR.

El token de análisis (`SONAR_TOKEN`) vive **únicamente en GitHub Secrets** y el workflow lo
consume como `${{ secrets.SONAR_TOKEN }}`. **Ningún secreto se versiona en este repositorio**:
ni tokens, ni contraseñas, ni archivos `.env`.

El detalle está en [`docs/calidad/sonarqube.md`](docs/calidad/sonarqube.md).

## Documentación

Las explicaciones de cada historia de usuario están en [`docs/historias/`](docs/historias/):

- [HU-01 · Registro de empresa](docs/historias/HU-01-registro-empresa.md)
- [HU-02 · Registro y administración de usuarios](docs/historias/HU-02-registro-usuario.md)
- [HU-03 · Inicio de sesión](docs/historias/HU-03-inicio-sesion.md)
- [HU-04 · Crear proceso](docs/historias/HU-04-crear-proceso.md)
- [HU-05 · Editar proceso](docs/historias/HU-05-editar-proceso.md)
- [HU-06 · Eliminar proceso](docs/historias/HU-06-eliminar-proceso.md)
