# HU-03 · Inicio de sesión

## Qué pide la historia

Como usuario registrado, quiero iniciar sesión con mis credenciales para acceder únicamente a la
información de mi empresa.

Criterios de aceptación:

- El usuario inicia sesión con su correo y su contraseña.
- La contraseña se almacena cifrada (hash), nunca en texto plano.
- Si las credenciales son incorrectas se muestra un mensaje de error genérico.
- Existe una opción para cerrar sesión.
- Un usuario inactivo no puede iniciar sesión.
- Después de iniciar sesión, el usuario solo accede a datos de su propia empresa.

## Qué implementamos

Se incorporó **Spring Security 7.1.1** (a través de `spring-boot-starter-security`, cuya versión
la gestiona el BOM de Spring Boot 4.1.1) y sobre esa base se construyó la autenticación real:

- La entidad `Usuario` pasó a tener `password` (hash BCrypt, columna `password_hash`) y `activo`.
- `UsuarioAutenticacionService` implementa `UserDetailsService` y traduce un `Usuario` de la base
  de datos al `UserDetails` que entiende Spring Security.
- `SecurityConfig` define el `PasswordEncoder` (BCrypt) y la única `SecurityFilterChain` de la
  aplicación: qué rutas son públicas, cuáles exigen sesión, el formulario de login y el logout.
- `AutenticacionController` sirve la vista `/login`; el procesamiento del formulario lo hace
  Spring Security, no un controlador propio.

La autenticación dejó de ser responsabilidad de los controladores. Antes cada método comprobaba
`principal == null` y devolvía `redirect:/login` (una ruta que ni siquiera existía). Ahora el
`SecurityFilterChain` corta la petición **antes** de que llegue al controlador, de modo que cuando
un método de `ProcesoController` o `EmpresaController` se ejecuta, el `Principal` siempre es real.

## Flujo

```
GET  /login            → autenticacion/login.html            (público)
POST /login            → Spring Security (UsernamePasswordAuthenticationFilter)
                          └─ UsuarioAutenticacionService.loadUserByUsername(correo)
                               ├─ normaliza el correo (trim + minúsculas)
                               ├─ no existe            → UsernameNotFoundException
                               ├─ existe sin password  → UsernameNotFoundException
                               └─ existe               → UserDetails(hash, activo, ROLE_<rol>)
                          └─ DaoAuthenticationProvider compara con BCrypt
                               ├─ hash distinto  → redirect /login?error
                               ├─ usuario inactivo → redirect /login?error
                               └─ correcto         → redirect /empresas (o a la ruta solicitada)

POST /logout           → invalida la sesión y limpia el contexto → redirect /login?logout

<cualquier ruta protegida sin sesión>
   ├─ /api/**   → 401 con cuerpo JSON {"codigo":"USUARIO_NO_AUTORIZADO", ...}
   └─ resto     → redirect a /login
```

### Rutas públicas y protegidas

| Ruta | Acceso | Motivo |
|---|---|---|
| `GET /login` | Público | Sin ella nadie podría autenticarse |
| `GET /empresas/nueva`, `POST /empresas` | Público | Es el punto de entrada de HU-01: quien registra la empresa todavía no tiene usuario |
| `/css/**`, `/js/**`, `/favicon.ico`, `/error` | Público | Recursos que la propia página de login necesita |
| `GET /empresas`, `GET /empresas/{id}` | Autenticado | Exponían información de todas las empresas |
| `/procesos/**` | Autenticado | Dependen del usuario autenticado |
| `/api/procesos/**` | Autenticado | Igual que las anteriores, pero responden JSON |
| `/usuarios/**` | Rol `ADMINISTRADOR` | Administración de usuarios de HU-02 |

## Contraseñas

- Se cifran con **BCrypt** (`BCryptPasswordEncoder`, fuerza por defecto). El hash resultante empieza
  por `$2a$` y no es reversible.
- El hash se guarda en la columna `password_hash`; **nunca** se guarda la contraseña en claro.
- No existe ningún DTO de respuesta con campo de contraseña. `EmpresaRespuestaDto` devuelve
  identificación de la empresa y el correo del administrador, nada más. Hay una prueba que
  recorre los campos de ese DTO y falla si alguno se llamara `password` o `contrasena`.
- Las entidades y DTO usan `@Getter @Setter` de Lombok, **no** `@Data` ni `@ToString`, así que no
  hay un `toString()` generado que pueda arrastrar el hash a un log.
- La contraseña del formulario de registro no se reimprime en el HTML: el campo se declara con
  `name="passwordAdministrador"` en lugar de `th:field`, de modo que al volver al formulario por
  un error de validación el valor no viaja de vuelta al navegador.
- Este repositorio **no contiene ninguna contraseña real**, ni de base de datos ni de usuario.

## Error genérico

Los tres casos de fallo devuelven exactamente la misma respuesta (`redirect /login?error`, que
la vista muestra como «Correo o contraseña incorrectos»):

- el correo no existe,
- el correo existe pero la contraseña no coincide,
- el usuario existe pero está inactivo.

`UsuarioAutenticacionService` lanza siempre el mismo mensaje (`Credenciales invalidas`) para no
revelar si un correo está registrado, y Spring Security convierte además `UsernameNotFoundException`
en `BadCredentialsException` por su propio mecanismo de ocultación.

## Aislamiento entre empresas

El hallazgo de la auditoría era que `GET /empresas` y `GET /empresas/{id}` mostraban información
sin autenticación. Se corrigió en dos capas:

1. **Filtro**: ambas rutas exigen sesión.
2. **Servicio**: `EmpresaService.listarVisiblesPara(username)` y
   `EmpresaService.obtenerParaUsuario(id, username)` resuelven la empresa **a partir del usuario
   autenticado**, no a partir de lo que pida el cliente.
   - El listado devuelve únicamente la empresa propia; ya no existe un `listar()` sin filtro.
   - El detalle de otra empresa lanza `UsuarioSinPermisoException` **antes** de consultar la base
     de datos, y el usuario ve una página de error 403 en lugar del dato.

`ProcesoService` ya aplicaba esta misma regla para procesos y no se modificó.

## CSRF

La protección CSRF de Spring Security queda **activada** en todas las rutas, incluida `/api/**`.
Los formularios Thymeleaf incorporan el token automáticamente (`th:action`), y la API se consume
desde el propio front-end con la sesión del navegador. Hay una prueba que envía un `POST` sin token
y comprueba que se rechaza con `403`.

## Archivos principales

### Creados

| Archivo | Para qué sirve |
|---|---|
| `config/SecurityConfig.java` | `PasswordEncoder` BCrypt y la `SecurityFilterChain`: rutas públicas, login, logout y punto de entrada |
| `config/ApiNoAutorizadoEntryPoint.java` | Responde `401` con cuerpo JSON cuando falta autenticación en `/api/**` |
| `service/UsuarioAutenticacionService.java` | `UserDetailsService`: carga el usuario, su hash, su estado y su rol |
| `controller/AutenticacionController.java` | Sirve la vista de `/login` |
| `controller/MvcExceptionHandler.java` | Convierte los errores de negocio en páginas HTML para las vistas |
| `templates/autenticacion/login.html` | Formulario de inicio de sesión |
| `templates/error/problema.html` | Página de error para las rutas MVC |

### Modificados

| Archivo | Cambio |
|---|---|
| `pom.xml` | `spring-boot-starter-security` y `spring-boot-starter-security-test` |
| `domain/Usuario.java` | Se agregaron `password` (columna `password_hash`) y `activo` |
| `service/EmpresaService.java` | Cifra la contraseña del administrador y expone solo la empresa propia |
| `controller/EmpresaController.java` | `lista` y `detalle` reciben el `Principal` y delegan el aislamiento |
| `controller/ProcesoController.java` | Se quitaron los `principal == null → redirect:/login` redundantes |
| `controller/ProcesoRestController.java` | Se quitaron los lanzamientos manuales de `UsuarioNoAutorizadoException` |
| `controller/ApiExceptionHandler.java` | Limitado a `@RestController` para no devolver JSON en las vistas |
| `templates/*` | Formulario de logout en las páginas autenticadas |

## Orden de validación (hallazgo corregido)

`ProcesoController.actualizar` validaba el `BindingResult` **antes** de comprobar la autenticación:
una petición sin sesión con datos inválidos volvía al formulario en lugar de ir al login.

Con Spring Security el problema desaparece de raíz, porque el filtro se ejecuta antes que el
`DispatcherServlet`. No se añadió una segunda comprobación en el controlador: la prueba
`actualizarUnProcesoExigeAutenticacionAntesDeValidarElFormulario` envía un formulario **inválido**
sin sesión y verifica que la respuesta es la redirección al login y que el servicio nunca se llama.

## REST vs MVC

`ApiExceptionHandler` era un `@RestControllerAdvice` sin restricción, así que también atendía a los
controladores de vistas: un error en `GET /empresas/{id}` respondía JSON en el navegador. Al añadir
la comprobación de aislamiento en `EmpresaController` eso pasó de ser un detalle a un problema real,
y se separaron las dos estrategias:

| Manejador | Alcance | Respuesta |
|---|---|---|
| `ApiExceptionHandler` | `@RestControllerAdvice(annotations = RestController.class)`, `@Order(10)` | JSON con `codigo` y `mensaje` |
| `MvcExceptionHandler` | `@ControllerAdvice`, `@Order(20)` | Vista `error/problema` con el estado HTTP correcto |

La selección por anotación evita listar controladores uno por uno: los `@RestController` entran en
el primero y los `@Controller` de vistas caen en el segundo.

## Tests

| Clase | Pruebas | Necesita PostgreSQL |
|---|---|---|
| `UsuarioAutenticacionServiceTest` | 7 | No |
| `SeguridadRutasTest` | 17 | No |
| `AutenticacionFlujoTest` | 9 | No |
| `AutenticacionControllerTest` | 1 | No |
| `MvcExceptionHandlerTest` | 2 | No |
| `RegistroYLoginIntegracionTest` | 6 | **Sí** |

Las pruebas de rutas y de flujo usan `@WebMvcTest` con `@Import(SecurityConfig.class)`, de modo que
**sí ejecutan los filtros de Spring Security**. Las pruebas que ya existían con
`MockMvcBuilders.standaloneSetup` se conservaron para lo que sí verifican (el comportamiento del
controlador), porque standalone no levanta la cadena de filtros.

### Criterios de aceptación y su prueba

| Criterio | Prueba |
|---|---|
| Login con correo y contraseña | `elLoginConCredencialesCorrectasAutenticaAlUsuarioYLoLlevaASuEmpresa` |
| Contraseña cifrada | `registrarGuardaLaContrasenaDelAdministradorComoHashBcrypt` |
| Contraseña incorrecta → error genérico | `elLoginConContrasenaIncorrectaNoAutenticaYVuelveAlLoginConErrorGenerico` |
| Usuario inexistente → el mismo error | `elLoginDeUnUsuarioInexistenteDevuelveElMismoErrorGenerico` |
| Usuario inactivo no entra | `unUsuarioInactivoNoPuedeIniciarSesion` |
| Logout invalida la sesión | `elLogoutInvalidaLaSesionYRedirigeAlLoginConAviso` |
| Ruta protegida sin sesión → login | `elListadoDeEmpresasExigeAutenticacion` y las demás de `SeguridadRutasTest` |
| El `Principal` llega real al controlador | `elUsuarioAutenticadoLlegaAlControladorDeProcesosConSuPrincipalReal` |
| Empresa A no ve datos de empresa B | `unUsuarioDeLaEmpresaANoPuedeConsultarLaEmpresaB` |
| `/login` accesible sin sesión | `laPaginaDeLoginEsAccesibleSinAutenticacion` |
| Recursos estáticos accesibles | `losRecursosEstaticosDeLasVistasSonAccesiblesSinAutenticacion` |

## Cómo demostrarla

1. Preparar PostgreSQL (ver «Migración de base de datos» más abajo) y definir `DB_PASSWORD` en `.env`.
2. Arrancar con `./mvnw spring-boot:run`.
3. Abrir `http://localhost:8080/empresas`. Como no hay sesión, la aplicación redirige a `/login`.
4. Pulsar «Registrar una empresa nueva» y registrar una empresa con su contraseña de administrador.
5. La aplicación intenta llevar al detalle de la empresa; como todavía no hay sesión, redirige al
   login. Iniciar sesión con el correo de contacto y la contraseña recién elegida: se vuelve
   automáticamente al detalle de esa empresa.
6. Escribir una contraseña incorrecta: aparece «Correo o contraseña incorrectos», sin decir si el
   correo existe.
7. Intentar entrar con un correo inexistente: exactamente el mismo mensaje.
8. Ya autenticado, abrir `http://localhost:8080/empresas`: solo aparece la empresa propia.
9. Registrar una segunda empresa desde una ventana privada y, con la sesión de la primera, pedir
   `http://localhost:8080/empresas/{id-de-la-segunda}`: la respuesta es una página 403.
10. Pulsar «Cerrar sesión»: vuelve al login con el aviso de sesión cerrada, y `/empresas` ya no es
    accesible.

## Migración de base de datos

El perfil de desarrollo usa `spring.jpa.hibernate.ddl-auto=update`. Las dos columnas nuevas de
`usuario` (`password_hash` y `activo`) son `NOT NULL`, así que **si la tabla `usuario` ya tiene
filas, Hibernate no podrá añadirlas**. El detalle importante es que `update` **no detiene el
arranque**: registra el error como advertencia, la aplicación levanta sin las columnas y el fallo
aparece después, al consultar o guardar usuarios.

En **CI no existe este problema**: el perfil `test` usa `create-drop` sobre un contenedor limpio.

Para desarrollo (Windows) y para la máquina virtual hay dos caminos. **Ninguno se ejecuta
automáticamente: hay que elegirlo y aplicarlo a mano.**

### Opción A — recrear el esquema (recomendada en desarrollo)

Los datos de desarrollo son desechables y los usuarios que existen hoy no tienen contraseña, así
que no podrían autenticarse de todos modos.

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Al arrancar, Hibernate recrea todo el esquema ya con las columnas nuevas. Hay que volver a
registrar las empresas de prueba desde `/empresas/nueva`.

### Opción B — migración aditiva (conserva empresas y procesos)

```sql
ALTER TABLE usuario ADD COLUMN password_hash varchar(100);
ALTER TABLE usuario ADD COLUMN activo boolean;
UPDATE usuario SET activo = false WHERE activo IS NULL;
ALTER TABLE usuario ALTER COLUMN activo SET NOT NULL;
```

`password_hash` queda **nullable a propósito**: no existe ninguna contraseña que poner ahí y no se
va a inventar una. Los usuarios heredados quedan sin contraseña y con `activo = false`, es decir,
no pueden iniciar sesión; `UsuarioAutenticacionService` rechaza explícitamente a cualquier usuario
sin hash. Cuando todos los usuarios heredados se hayan vuelto a crear con contraseña, se puede
cerrar el ciclo:

```sql
ALTER TABLE usuario ALTER COLUMN password_hash SET NOT NULL;
```

## Qué quedó pendiente

- **HU-02 (administración de usuarios): resuelta.** El administrador ya crea usuarios dentro de su
  empresa, les cambia el rol y los desactiva, y esas cuentas inician sesión con esta misma cadena de
  seguridad. Ver [HU-02 · Registro y administración de usuarios](HU-02-registro-usuario.md).
- **Autorización por rol en las rutas: parcial.** Con HU-02, `/usuarios/**` ya exige
  `ROLE_ADMINISTRADOR` en el `SecurityFilterChain`. Para procesos, en cambio, las reglas de permiso
  siguen aplicándose en
  `ProcesoService` (`puedeEditar`, `validarRolEditor`). No se añadieron reglas por rol en el
  `SecurityFilterChain` para no duplicar la lógica que ya funciona y está probada.
- **Recuperación de contraseña y cambio de contraseña.** No forman parte de HU-03.
- **Clientes externos de la API.** `/api/procesos/**` se autentica con la sesión del navegador y
  exige token CSRF. Un cliente externo (Postman, otro servicio) necesitaría una cadena de filtros
  propia y sin estado; queda fuera de este bloque.
- **`UsuarioNoAutorizadoException`.** Ya ningún controlador la lanza: la respuesta `401` de la API
  la produce `ApiNoAutorizadoEntryPoint` con el mismo `codigo` (`USUARIO_NO_AUTORIZADO`), de modo
  que el contrato de la API no cambió para el cliente. La excepción y su manejador se conservan
  porque siguen formando parte de ese contrato, pero hoy solo los cubre `ApiExceptionHandlerTest`.
