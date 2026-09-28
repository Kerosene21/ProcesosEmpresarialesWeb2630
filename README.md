# ProcesosEmpresarialesWeb2630

Backend de una aplicación **multiempresa** para modelar, editar y visualizar procesos
empresariales con conceptos de **BPMN**.

> El sistema **modela** procesos: **no los ejecuta**. No es un motor BPM, no envía mensajes reales
> ni invoca sistemas externos; solo documenta cómo funciona cada proceso.

## Objetivo

Que cada empresa tenga un espacio propio donde registrar, consultar y editar el modelo de sus
procesos (quién participa, qué actividades hay, cómo fluye el trabajo y qué mensajes se
intercambian), manteniendo la información de cada organización aislada de las demás.

## Funcionalidades del backend

- **Empresas y usuarios**: registro de empresas con su administrador inicial; el administrador
  crea usuarios, cambia su rol y los desactiva (desactivación lógica, sin envío de correo).
- **Autenticación**: inicio y cierre de sesión con Spring Security (formulario, sesión y
  contraseñas con BCrypt).
- **Procesos**: crear, editar, eliminar (eliminación lógica con confirmación) y consultar con
  búsqueda, filtros y paginación. Estados `BORRADOR` y `PUBLICADO`; publicar valida el modelo.
- **Historial**: cada cambio de un proceso y de sus elementos queda registrado con fecha, usuario y
  campos modificados.
- **Elementos del modelo**: actividades, gateways `EXCLUSIVO` (X), `PARALELO` (+) e `INCLUSIVO` (O)
  y arcos (flujos de secuencia) entre actividades, gateways y eventos de un mismo pool.
- **Estructura del diagrama**: pools (`PROPIETARIO`, `PARTICIPANTE`, `EXTERNO`, con opción de caja
  negra) y lanes que se crean, editan, reordenan y eliminan.
- **Roles de proceso**: catálogo por empresa, con historial, que se asigna a las lanes.
- **Permisos de estructura**: configuración por rol de usuario de quién puede crear, editar y
  eliminar pools y lanes en cada proceso.
- **Compartición entre empresas**: un proceso puede compartirse en solo lectura con otras empresas
  registradas; la empresa propietaria conserva la edición.
- **Eventos de mensaje**: Message Throw, Message Catch (de inicio o intermedio) y envíos externos
  (correo, servicio web o cola) hacia un pool externo.
- **Correlación de mensajes**: clave de correlación y advertencias de coherencia entre Message
  Throw y Message Catch (nombre del mensaje, clave y comportamiento).
- **Diagrama BPMN**: `GET /api/procesos/{id}/diagrama` devuelve el modelo completo (pools, lanes,
  actividades, gateways, eventos, arcos y flujos de mensaje).

La empresa nunca se recibe por parámetro: sale del usuario autenticado, y cada empresa solo ve y
modifica sus propios recursos (más los procesos que otras le compartan, en solo lectura).

### Roles de acceso

| Rol | Alcance general |
|---|---|
| `ADMINISTRADOR` | Todas las operaciones, incluidas la gestión de usuarios, las eliminaciones, la compartición y los permisos de estructura |
| `EDITOR` | Crea y edita procesos y sus elementos, sin eliminar; en pools y lanes depende de los permisos de estructura del proceso |
| `SOLO_LECTURA` | Solo consulta |

## Historias de usuario

El **backend** correspondiente a las historias **HU-01 a HU-28** está implementado en esta rama.

| Historias | Alcance de backend |
|---|---|
| HU-01 | Registro de empresa |
| HU-02 | Registro y administración de usuarios |
| HU-03 | Inicio de sesión |
| HU-04 a HU-07 | Crear, editar, eliminar y consultar procesos, con historial |
| HU-08 a HU-10 | Crear, editar y eliminar actividades |
| HU-11 a HU-13 | Crear, editar y eliminar arcos |
| HU-14 a HU-16 | Crear, editar y eliminar gateways |
| HU-17 a HU-20 | Roles de proceso: catálogo, edición, eliminación e historial |
| HU-21 a HU-24 | Pools, gestión de lanes, permisos de estructura y compartición entre empresas |
| HU-25 a HU-28 | Eventos de mensaje: Message Throw, envíos externos, Message Catch y correlación |

## Stack

| Área | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1.1 (Spring MVC, Spring Data JPA, Spring Validation, Spring Security) |
| Base de datos | PostgreSQL |
| Utilidades | ModelMapper 3.2.4, Lombok |
| Documentación de API | springdoc-openapi 3.1.1 (OpenAPI + Swagger UI) |
| Build | Maven Wrapper (`mvnw`) |
| Calidad | JaCoCo 0.8.13, SonarQube Cloud, GitHub Actions |
| Pruebas de carga | Apache JMeter |

## Arquitectura

```
Entity → Repository → DTO → Service → Controller
```

- La lógica de negocio vive en los **Service**; los **Controller** validan, delegan y responden.
- Las entidades JPA no se exponen: los controladores trabajan con **DTO**.

```
co.edu.javeriana.procesosempresariales
├── config      seguridad, OpenAPI y beans reutilizables
├── controller  controladores MVC y REST, y manejadores de excepciones
├── domain      entidades JPA y enumeraciones
├── dto         objetos de entrada y salida
├── exception   excepciones de negocio
├── repository  repositorios Spring Data
└── service     lógica de negocio
```

## Ejecución local

### Requisitos

- Java 21
- PostgreSQL en ejecución
- Maven no es necesario: el proyecto incluye el wrapper (`mvnw`)

### Base de datos y variables de entorno

| Base | Uso | `ddl-auto` |
|---|---|---|
| `procesos_empresariales` | desarrollo | `update` |
| `procesos_empresariales_test` | pruebas con contexto de Spring | `create-drop` |

Las credenciales se leen de variables de entorno o de un archivo `.env` local que no se versiona:

```bash
cp .env.example .env
```

`DB_PASSWORD` es obligatoria: si no está definida, la aplicación no arranca. La URL de la base de
pruebas es fija y no se puede redirigir por variables de entorno, porque `create-drop` destruye el
esquema al terminar.

### Ejecutar

```bash
./mvnw spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`. El flujo de uso empieza registrando
una empresa en `/empresas/nueva` e iniciando sesión en `/login` con su administrador inicial.

### Datos demo

El perfil `demo` carga una empresa de ejemplo con usuarios, roles de proceso y un proceso modelado
(pools, lanes, actividades, gateway, arcos y eventos de mensaje). Para usarlo, definir en el
entorno o en `.env` las variables `DEMO_ADMIN_PASSWORD` y `DEMO_USER_PASSWORD` (entre 8 y 100
caracteres) y ejecutar:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

| Usuario | Rol |
|---|---|
| `admin.demo@example.com` | `ADMINISTRADOR` (contraseña: `DEMO_ADMIN_PASSWORD`) |
| `editor.demo@example.com` | `EDITOR` (contraseña: `DEMO_USER_PASSWORD`) |
| `lectura.demo@example.com` | `SOLO_LECTURA` (contraseña: `DEMO_USER_PASSWORD`) |

El dataset se crea solo si no existe: los rearranques no duplican datos ni cambian contraseñas, y
cuando ya existe las variables dejan de ser necesarias. El perfil `demo` no se activa por defecto
y no debe usarse en producción.

### Bases de desarrollo creadas con versiones anteriores

Una base nueva no necesita migración: `ddl-auto=update` crea el esquema. Solo una base de
desarrollo que ya existía requiere pasos manuales, según desde qué versión venga:

- **Anterior a HU-03**: la tabla `usuario` necesita las columnas obligatorias `password_hash` y
  `activo`. Ver [HU-03 · Migración de base de datos](docs/historias/HU-03-inicio-sesion.md#migración-de-base-de-datos).
- **Anterior a HU-08**: los pools existentes se quedan sin lanes. Para crearles la lane inicial:

  ```sql
  INSERT INTO lane (nombre, pool_id)
  SELECT 'General', p.id FROM pool p
  WHERE NOT EXISTS (SELECT 1 FROM lane l WHERE l.pool_id = p.id);
  ```

- **Creada con el esquema de HU-01 a HU-20**: ejecutar una sola vez, con respaldo previo y antes de
  arrancar la aplicación, [`docs/migrations/hu21-hu24-migracion.sql`](docs/migrations/hu21-hu24-migracion.sql).

El perfil de pruebas y CI usan `create-drop` sobre una base limpia, así que no necesitan migración.

## Documentación de la API (Swagger / OpenAPI)

Con la aplicación en ejecución:

| Recurso | Ruta |
|---|---|
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` (`/swagger-ui.html` redirige aquí) |
| OpenAPI (JSON) | `/v3/api-docs` y `/v3/api-docs/api-rest` (grupo API REST) |
| OpenAPI (YAML) | `/v3/api-docs.yaml` |

- Swagger UI sirve para explorar y probar la API REST (`/api/**`).
- Las rutas de documentación son públicas, pero **la API conserva la seguridad real** del sistema.
- La autenticación usa la **sesión** del sistema: se inicia sesión en `/login` en el mismo
  navegador y Swagger UI reutiliza la cookie `JSESSIONID`.
- Las operaciones `POST`, `PUT` y `DELETE` exigen el token **CSRF** de la sesión en el encabezado
  `X-CSRF-TOKEN` (botón *Authorize*, esquema `csrf`).
- No existe un JWT ni un esquema Bearer ficticio solo para Swagger.

## Pruebas y calidad

### Ejecutar las pruebas

```bash
./mvnw clean verify
```

Ejecuta la suite completa (pruebas unitarias, de controladores y seguridad, y de integración) y
genera el reporte de cobertura de **JaCoCo** en `target/site/jacoco/index.html`.

Las pruebas de integración (`*IntegracionTest` y `ProcesosEmpresarialesWeb2630ApplicationTests`)
levantan el contexto de Spring y **requieren PostgreSQL** con la base `procesos_empresariales_test`
y las credenciales configuradas. El resto, incluidas las de seguridad con `@WebMvcTest`, no
necesitan base de datos:

```bash
./mvnw test -Dtest='!*IntegracionTest,!ProcesosEmpresarialesWeb2630ApplicationTests'
```

### SonarQube Cloud

El workflow [`.github/workflows/sonar.yml`](.github/workflows/sonar.yml) se ejecuta en cada push a
`main`, en cada pull request hacia `main` y manualmente. Levanta PostgreSQL (`postgres:16-alpine`),
ejecuta `verify` con JaCoCo y envía el análisis a **SonarQube Cloud**, que evalúa el
**Quality Gate**.

| Dato | Valor |
|---|---|
| Organization | `kerosene21` |
| Project | `Kerosene21_ProcesosEmpresarialesWeb2630` |

El token `SONAR_TOKEN` vive únicamente en GitHub Secrets; ningún secreto se versiona. El detalle
está en [`docs/calidad/sonarqube.md`](docs/calidad/sonarqube.md).

### Pruebas de carga con JMeter

El plan [`jmeter/backend-procesos.jmx`](jmeter/backend-procesos.jmx) simula usuarios autenticados
consultando la API:

- 25 usuarios, ramp-up de 10 segundos y 5 iteraciones.
- Cada usuario se autentica una sola vez: `GET /login`, extracción del token CSRF y `POST /login`.
- La cookie `JSESSIONID` se mantiene durante toda la prueba.
- En cada iteración consulta `GET /api/procesos`.

El plan apunta a `localhost:8085`, por lo que la aplicación debe levantarse en ese puerto (por
ejemplo, `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8085`). El usuario y la
contraseña se reciben como propiedades externas y **nunca deben guardarse en el `.jmx`**:

```bash
jmeter -n -t jmeter/backend-procesos.jmx -Jusername=USUARIO -Jpassword=CONTRASENA -l jmeter/resultados/carga.jtl -e -o jmeter/resultados/reporte-html
```

La última validación local produjo **175 solicitudes con 0 errores**. Los tiempos obtenidos en una
máquina local son orientativos y no constituyen una garantía de rendimiento. La carpeta
`jmeter/resultados/` no se versiona.

### Validación manual con Postman

La API también se validó manualmente con Postman: autenticación, manejo del token CSRF, consultas
protegidas, creación y consulta de recursos, y el error de negocio por nombre duplicado. La
colección se usó de forma local y no forma parte del repositorio.

## Documentación

- [`docs/historias/`](docs/historias/): documento detallado por historia; hoy contiene los de
  HU-01 a HU-15.
- Swagger UI: contrato actualizado de toda la API REST, incluidas las funcionalidades de HU-16 a
  HU-28, que no tienen documento propio en `docs/historias/`.
- [`docs/migrations/`](docs/migrations/): migraciones manuales para bases de desarrollo existentes.
- [`docs/calidad/sonarqube.md`](docs/calidad/sonarqube.md): análisis de calidad y cobertura.
