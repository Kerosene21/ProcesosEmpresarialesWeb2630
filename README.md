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
| HU-01 · Registro de empresa | Implementada, salvo la credencial del administrador inicial (depende de HU-03) |
| HU-02 · Registro de usuario en empresa | Pendiente |
| HU-03 · Inicio de sesión | Pendiente; aún no hay autenticación en el proyecto |
| HU-04 · Crear proceso | Implementada a nivel de servicio y vistas; pendiente de revisión |
| HU-05 · Editar proceso | Implementada a nivel de servicio y vistas; pendiente de revisión |

Como todavía no existe autenticación, las pantallas de proceso que dependen del usuario
autenticado no son accesibles de extremo a extremo. Las pantallas de empresa sí lo son.

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

### Ejecutar

```bash
./mvnw spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080`.

### Pruebas

Las pruebas unitarias no necesitan base de datos:

```bash
./mvnw -Dtest=EmpresaServiceTest,RegistroEmpresaDtoTest test
```

La suite completa incluye una prueba que levanta el contexto de Spring y sí requiere que
PostgreSQL esté disponible con las credenciales configuradas.

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
