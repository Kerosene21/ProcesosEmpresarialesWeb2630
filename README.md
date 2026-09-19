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
| HU-02 · Registro de usuario en empresa | Pendiente |
| HU-03 · Inicio de sesión | Implementada |
| HU-04 · Crear proceso | Implementada a nivel de servicio y vistas; pendiente de revisión |
| HU-05 · Editar proceso | Implementada a nivel de servicio y vistas; pendiente de revisión |

Con la autenticación en marcha, las pantallas de proceso que dependen del usuario autenticado ya
son accesibles de extremo a extremo: se registra una empresa, se inicia sesión con las credenciales
del administrador inicial y desde ahí se trabajan los procesos de esa empresa.

De HU-02 solo existe el administrador inicial que crea HU-01. **No hay alta de usuarios dentro de
una empresa, ni cambio de rol, ni activación o desactivación desde la interfaz.**

## Historias en desarrollo

El bloque actual cubre HU-01 a HU-05. La documentación de cada historia está en
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

### Ejecutar

```bash
./mvnw spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`.

### Pruebas

Las pruebas que no levantan el contexto completo tampoco necesitan base de datos. Eso incluye las
de seguridad, que usan `@WebMvcTest` y sí ejecutan los filtros de Spring Security:

```bash
./mvnw -Dtest='!ProcesosEmpresarialesWeb2630ApplicationTests,!RegistroYLoginIntegracionTest' test
```

La suite completa incluye dos clases que levantan el contexto de Spring
(`ProcesosEmpresarialesWeb2630ApplicationTests` y `RegistroYLoginIntegracionTest`) y sí requieren
que PostgreSQL esté disponible con las credenciales configuradas.

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
- [HU-03 · Inicio de sesión](docs/historias/HU-03-inicio-sesion.md)
- [HU-04 · Crear proceso](docs/historias/HU-04-crear-proceso.md)
- [HU-05 · Editar proceso](docs/historias/HU-05-editar-proceso.md)
