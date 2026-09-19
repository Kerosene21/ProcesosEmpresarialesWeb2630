# HU-02 · Registro y administración de usuarios en la empresa

## Qué pide la historia

Como administrador de empresa quiero crear y gestionar cuentas de usuario dentro de mi empresa.

Criterios de aceptación:

- Puedo invitar usuarios por correo electrónico.
- Puedo asignar rol de acceso: `ADMINISTRADOR`, `EDITOR`, `SOLO_LECTURA`.
- Un usuario pertenece a una sola empresa.
- Puedo cambiar el rol de un usuario.
- Puedo desactivar un usuario sin borrar su historial.
- Los procesos pertenecen a la empresa, no al usuario.
- Si un usuario se desactiva, los procesos de la empresa siguen disponibles.

Estos son **roles de acceso** a la aplicación. No tienen relación con los roles/lanes de BPMN que
aparecerán en el diagramador.

## Alcance de «invitar por correo»

La invitación se implementa como **creación directa de la cuenta usando el correo como
identificador de acceso**. El administrador escribe correo, contraseña inicial y rol; el usuario
entra con esas credenciales.

**No se integró ningún proveedor de correo ni SMTP**, porque el proyecto no lo exige y añadirlo
implicaría infraestructura y secretos nuevos. La consecuencia práctica es que el administrador debe
comunicarle la contraseña inicial al usuario por fuera de la aplicación. Queda anotado como
pendiente real más abajo.

El correo sigue siendo el `username` de autenticación definido en HU-03: se normaliza con `trim` y
minúsculas, y mantiene su unicidad global mediante `uk_usuario_username`.

## Modelo

**`Usuario` no cambió.** Ya tenía todo lo que HU-02 necesita desde el bloque de HU-03:

| Campo | Uso en HU-02 |
|---|---|
| `username` | Correo e identificador de acceso (no se duplica en otra columna) |
| `password` (columna `password_hash`) | Hash BCrypt de la contraseña inicial |
| `rol` | Rol de acceso asignado por el administrador |
| `activo` | Desactivación lógica |
| `empresa` | Un usuario pertenece a una sola empresa; `NOT NULL` |

`Proceso` y `HistorialProceso` **tampoco cambiaron**. El proceso ya apunta a `Empresa`, no a
`Usuario`, así que el criterio «los procesos pertenecen a la empresa» ya se cumplía: no se creó
ninguna relación `Usuario → Proceso` para satisfacerlo artificialmente.

## Flujo

```
GET  /usuarios              → usuarios/lista.html           (solo ADMINISTRADOR)
GET  /usuarios/nuevo        → usuarios/formulario.html
POST /usuarios              → UsuarioService.crear(dto, administrador)
                                ├─ exige rol ADMINISTRADOR
                                ├─ normaliza el correo
                                ├─ existsByUsername → CorreoAdministradorEnUsoException
                                ├─ password → BCrypt
                                ├─ activo = true
                                └─ empresa = la del administrador autenticado
                              → redirect /usuarios
GET  /usuarios/{id}         → usuarios/detalle.html
GET  /usuarios/{id}/editar  → usuarios/formularioeditar.html
POST /usuarios/{id}         → UsuarioService.cambiarRol(id, dto, administrador)
                              → redirect /usuarios
POST /usuarios/{id}/desactivar → UsuarioService.desactivar(id, administrador)
                              → redirect /usuarios   (activo = false, nada se borra)
```

La empresa **nunca** llega desde el formulario. Se deriva siempre del administrador autenticado
(`administrador.getEmpresa()`), de modo que no existe ningún parámetro que un cliente pueda
manipular para crear un usuario en otra empresa.

## Permisos

Se aplican en **dos capas**, como pide la historia:

| Capa | Mecanismo | Qué cubre |
|---|---|---|
| Filtro | `SecurityConfig`: `.requestMatchers("/usuarios/**").hasRole("ADMINISTRADOR")` | Corta la petición HTTP antes del controlador |
| Negocio | `UsuarioService.exigirAdministrador(username)` | Protege el servicio aunque se invoque desde otro punto |

No se trata de lógica duplicada por descuido: el filtro protege la **ruta web** y el servicio
protege la **operación**. Un `EDITOR` o un `SOLO_LECTURA` reciben `403` del filtro; si alguien
llamara al servicio directamente, obtendría `UsuarioSinPermisoException`.

No se confía en ocultar botones: la lista solo muestra acciones donde tienen sentido, pero la
autorización real está en el servidor y hay pruebas que lo verifican enviando peticiones directas.

El `403` se atiende con `accessDeniedPage("/acceso-denegado")`, que reutiliza la vista
`error/problema` en lugar de mostrar la página en blanco del contenedor.

## Aislamiento multiempresa

Es el punto crítico de la historia y se resuelve **en la consulta**, no filtrando en Java:

| Operación | Consulta | Efecto |
|---|---|---|
| Listar | `findByEmpresaIdOrderByUsernameAsc(empresaId)` | La empresa entra en el `WHERE`; nunca se usa `findAll()` |
| Consultar / cambiar rol / desactivar | `findByIdAndEmpresaId(id, empresaId)` | Un id de otra empresa simplemente no existe para el administrador |

El `empresaId` siempre sale del administrador autenticado.

**Decisión: un usuario de otra empresa responde `404`, no `403`.** Al resolver el aislamiento en la
consulta, el recurso «no existe» desde el punto de vista de quien pregunta, y esa es también la
respuesta que menos información filtra: un `403` confirmaría que ese id existe en alguna parte. Los
dos códigos conviven con significados distintos:

- `403` → «no puedes administrar usuarios» (no eres administrador).
- `404` → «ese usuario no existe en tu empresa».

Tanto `UsuarioServiceTest` como `GestionUsuariosIntegracionTest` cubren los dos sentidos: la
empresa A no lista, ni consulta, ni modifica, ni desactiva usuarios de la empresa B, y viceversa.

## Contraseñas

- Se cifran con el mismo `PasswordEncoder` (BCrypt) que ya usaba HU-01/HU-03.
- La longitud exigida es la misma que en el registro de empresa: entre 8 y 100 caracteres.
- `UsuarioRespuestaDto` tiene únicamente `id`, `correo`, `rol` y `activo`. Hay una prueba que
  recorre sus campos por reflexión y falla si alguno se llamara `password` o `contrasena`.
- El campo de contraseña del formulario se declara con `name="password"` en lugar de `th:field`,
  así que un error de validación no reimprime la contraseña en el HTML.
- Ni la contraseña ni el hash se escriben en logs ni se devuelven en ninguna vista.

## Desactivación lógica

`desactivar` hace exactamente una cosa: `activo = false` y `save`. **No existe ninguna llamada a
`delete` ni a `deleteById` en `UsuarioService`**, y hay una prueba que lo verifica explícitamente.

Consecuencias comprobadas con pruebas:

- El usuario sigue en la tabla con su id, su correo y su rol; el conteo de usuarios no cambia.
- No puede iniciar sesión: `UsuarioAutenticacionService` lo entrega como `disabled`, y el login
  devuelve el mismo error genérico que cualquier otra credencial inválida.
- Sus entradas de `historial_proceso` siguen existiendo y siguen apuntando a él.
- Los procesos de la empresa siguen existiendo, con la misma empresa propietaria, y siguen siendo
  accesibles para el resto de usuarios de esa empresa.
- Sigue visible en la lista, marcado como **Inactivo**, precisamente para evidenciar que no se borró.

### Regla añadida: un administrador no puede actuar sobre su propia cuenta

`cambiarRol` y `desactivar` rechazan que el administrador se aplique la operación a sí mismo.

**Esta regla no está en los criterios de la historia**, así que se documenta en lugar de darla por
obvia. El motivo es de consistencia técnica, no de negocio: sin ella, un administrador puede
desactivarse o degradarse a `SOLO_LECTURA` y dejar a la empresa **sin ningún administrador**, sin
ninguna forma de recuperarse desde la aplicación. Como el actor de la operación siempre queda
excluido, la empresa conserva siempre al menos un administrador activo.

Se evaluó la alternativa «impedirlo solo si es el último administrador»: es más permisiva, pero
exige una consulta de conteo y deja el caso de borde de que el actor pierda el acceso a mitad de
sesión. Se prefirió la regla simple, que es más fácil de explicar y de probar.

## Excepciones

No se creó ninguna excepción nueva. Se reutilizan las existentes porque representan exactamente las
mismas reglas:

| Situación | Excepción | Respuesta MVC |
|---|---|---|
| No es administrador | `UsuarioSinPermisoException` | `403` con `error/problema` |
| Se opera sobre la propia cuenta | `UsuarioSinPermisoException` | `403` con `error/problema` |
| Usuario inexistente o de otra empresa | `RecursoNoEncontradoException` | `404` con `error/problema` |
| Correo ya usado | `CorreoAdministradorEnUsoException` | Error sobre el campo `correo` del formulario |

Sobre la última: su **nombre** es más estrecho que la regla que representa (nació en HU-01 para el
administrador inicial), pero la regla es idéntica —unicidad global del correo de usuario— y su
código de API ya era `USUARIO_CORREO_EN_USO`. Duplicarla en una `CorreoUsuarioDuplicadoException`
habría creado dos excepciones equivalentes, así que se reutiliza. Renombrarla a
`CorreoUsuarioEnUsoException` es una limpieza pendiente, anotada abajo.

`ApiExceptionHandler` no se tocó: sigue limitado a los `@RestController` y HU-02 no añade API REST.

## Repository

`UsuarioRepository` creció con dos consultas derivadas:

```java
List<Usuario> findByEmpresaIdOrderByUsernameAsc(Long empresaId);
Optional<Usuario> findByIdAndEmpresaId(Long id, Long empresaId);
```

**No se añadió JPQL.** Las consultas derivadas ya expresan el aislamiento dentro del `WHERE`, que
era el requisito, y son más legibles aquí que un `@Query` equivalente. Escribir JPQL solo para
demostrar que se sabe usar habría sido una consulta artificial. Si más adelante aparece una
necesidad real —por ejemplo proyectar directamente a DTO o un `join fetch` que evite una consulta
extra— ese será el momento de introducirlo.

## Archivos principales

### Creados

| Archivo | Para qué sirve |
|---|---|
| `dto/CrearUsuarioDto.java` | Formulario de creación: correo, contraseña inicial y rol, con sus validaciones |
| `dto/CambiarRolUsuarioDto.java` | Formulario de cambio de rol |
| `dto/UsuarioRespuestaDto.java` | Salida: `id`, `correo`, `rol`, `activo`. Sin contraseña ni hash |
| `service/UsuarioService.java` | Reglas de HU-02: permisos, aislamiento, hashing y desactivación lógica |
| `controller/UsuarioController.java` | Rutas MVC de `/usuarios` |
| `templates/usuarios/lista.html` | Listado con correo, rol y estado |
| `templates/usuarios/formulario.html` | Alta de usuario |
| `templates/usuarios/detalle.html` | Detalle con acciones |
| `templates/usuarios/formularioeditar.html` | Cambio de rol |
| `static/css/usuarios.css` | Ajustes de estilo para las acciones dentro de la tabla |

### Modificados

| Archivo | Cambio |
|---|---|
| `repository/UsuarioRepository.java` | Consultas de listado y de búsqueda aisladas por empresa |
| `config/SecurityConfig.java` | `/usuarios/**` exige `ROLE_ADMINISTRADOR`; `accessDeniedPage` |
| `controller/AutenticacionController.java` | Ruta `/acceso-denegado` que reutiliza `error/problema` |
| `templates/empresas/lista.html` | Enlace a la administración de usuarios |

## Tests

| Clase | Pruebas | Necesita PostgreSQL |
|---|---|---|
| `UsuarioServiceTest` | 28 | No |
| `SeguridadUsuariosTest` | 16 | No |
| `CrearUsuarioDtoTest` | 11 | No |
| `UsuarioControllerTest` | 10 | No |
| `CambiarRolUsuarioDtoTest` | 2 | No |
| `GestionUsuariosIntegracionTest` | 14 | **Sí** |

`SeguridadUsuariosTest` usa `@WebMvcTest` con `@Import(SecurityConfig.class)`, de modo que **sí
ejecuta los filtros de Spring Security** y las pruebas de rol y de CSRF son reales.

### Criterios de aceptación y su prueba

| Criterio | Prueba |
|---|---|
| Crear usuario por correo | `unAdministradorCreaUnUsuarioEnSuEmpresa` |
| Contraseña hasheada | `laContrasenaDelUsuarioCreadoSeGuardaHasheadaConBcrypt` |
| Usuario creado activo | `elUsuarioCreadoQuedaActivo` |
| Asignar los tres roles | `elUsuarioCreadoRecibeElRolIndicadoEnElFormulario`, `losTresRolesDeAccesoSonAceptados` |
| Un usuario, una empresa | `laEmpresaDelUsuarioCreadoEsLaDelAdministradorAutenticado` |
| Correo normalizado y único | `elCorreoDelUsuarioCreadoSeNormaliza...`, `elCorreoDuplicadoSeRechaza...` |
| Cambiar el rol | `unAdministradorCambiaElRolDeUnUsuarioDeSuEmpresa` |
| Desactivar sin borrar | `elUsuarioDesactivadoSeGuardaConActivoEnFalso...`, `desactivarNuncaBorraFisicamenteAlUsuario` |
| Procesos de la empresa, no del usuario | `desactivarUnUsuarioNoEliminaLosProcesosDeLaEmpresa` |
| Procesos siguen disponibles | `elProcesoSigueDisponibleParaElRestoDeLaEmpresaTrasDesactivarASuEditor` |
| Historial conservado | `desactivarUnUsuarioNoEliminaSuHistorialDeEdiciones` |
| Solo el administrador gestiona | `unEditorNoPuedeCrearUsuarios`, `unUsuarioDeSoloLecturaNoPuedeCrearUsuarios`, y las de `403` |
| Aislamiento entre empresas | `unAdministradorNoPuedeConsultar/CambiarRol/Desactivar...DeOtraEmpresa` |

### Integración con HU-03

| Criterio | Prueba |
|---|---|
| El usuario nuevo puede iniciar sesión | `unUsuarioCreadoPorElAdministradorPuedeIniciarSesionConSusCredenciales` |
| Su contraseña queda hasheada en la base | `laContrasenaDelUsuarioCreadoQuedaHasheadaEnLaBaseDeDatos` |
| El usuario desactivado ya no entra | `elUsuarioDesactivadoYaNoPuedeIniciarSesion` |
| Los authorities reflejan el rol | `elUsuarioCreadoRecibeLasAutoridadesDeSuRolDeAcceso` |
| El `Principal` lleva el correo correcto | `unUsuarioCreadoPorElAdministrador...` (`authenticated().withUsername(correo)`) |

El administrador inicial de HU-01 no se tocó: se sigue creando igual y aparece en la lista de
usuarios de su empresa como `ADMINISTRADOR` activo.

### Pruebas de persistencia

`GestionUsuariosIntegracionTest` incluye tres comprobaciones que solo tienen sentido contra la base
real, y **requieren PostgreSQL**:

- `elCorreoDeUsuarioEsUnicoGlobalmenteEnLaBaseDeDatos`: un `saveAndFlush` con un correo repetido
  —incluso en otra empresa— falla con `DataIntegrityViolationException` por `uk_usuario_username`.
- `unUsuarioSinEmpresaNoSePuedePersistir`: la columna `empresa_id` es `NOT NULL`.
- `elUsuarioDesactivadoSigueEnLaBaseDeDatosConActivoEnFalso`: el conteo de usuarios no cambia al
  desactivar.

No se añadió Testcontainers ni ninguna infraestructura nueva: se reutiliza el perfil `test` que ya
existía. En CI corren con el contenedor `postgres:16-alpine` del workflow; en un equipo sin
PostgreSQL hay que excluirlas (ver README).

## Cómo demostrarla

1. Preparar PostgreSQL y `DB_PASSWORD` en `.env`, y arrancar con `./mvnw spring-boot:run`.
2. Registrar una empresa en `/empresas/nueva` e iniciar sesión con el administrador inicial.
3. Entrar a **Usuarios de la empresa**: aparece el administrador inicial como `ADMINISTRADOR` activo.
4. **Crear usuario**: correo, contraseña y rol `EDITOR`. Vuelve al listado con el aviso de creación.
5. Enviar el formulario vacío o con una contraseña corta: los errores aparecen junto a cada campo y
   la contraseña escrita no se reimprime.
6. Repetir un correo ya usado: el error aparece sobre el campo del correo.
7. Cerrar sesión y entrar con el usuario nuevo: **funciona**, y al intentar abrir `/usuarios` recibe
   una página de acceso denegado, porque no es administrador.
8. Volver como administrador y **cambiar el rol** del usuario a `SOLO_LECTURA`: se ve en la lista.
9. **Desactivar** al usuario: sigue en la lista marcado como *Inactivo*.
10. Intentar iniciar sesión con él: aparece el mismo mensaje genérico de credenciales incorrectas.
11. Comprobar que los procesos de la empresa siguen ahí y siguen siendo accesibles.
12. Registrar una segunda empresa desde una ventana privada y comprobar que su administrador solo
    ve sus propios usuarios, y que pedir el id de un usuario de la otra empresa devuelve
    «no encontrado».

## Qué quedó pendiente

- **Invitación real por correo.** Hoy el administrador define la contraseña inicial y debe
  comunicarla por fuera de la aplicación. Una invitación de verdad (enlace con token de un solo uso
  y contraseña elegida por el invitado) necesita un proveedor SMTP, plantillas de correo y una
  entidad de token; no se abordó porque el proyecto no lo pide.
- **Reactivar un usuario desactivado.** La historia solo pide desactivar. La operación inversa no
  está implementada, así que hoy una desactivación es definitiva desde la interfaz.
- **Cambio de contraseña.** Ni el usuario puede cambiar la suya ni el administrador puede
  reiniciarla. No forma parte de HU-02 ni de HU-03.
- **El administrador no puede actuar sobre su propia cuenta.** Es la regla añadida que se explica
  arriba. Si el curso prefiere otro criterio —por ejemplo permitirlo mientras quede otro
  administrador activo— el cambio está localizado en `exigirQueNoSeaSuPropiaCuenta`.
- **El enlace a `/usuarios` es visible para todos los roles.** La seguridad está en el servidor y un
  `EDITOR` que lo pulse recibe la página de acceso denegado, pero ocultarlo según el rol necesitaría
  `thymeleaf-extras-springsecurity6` (disponible en el BOM) o pasar el rol al modelo desde
  `EmpresaController`. No se hizo para no tocar código de HU-01 en este bloque.
- **`CorreoAdministradorEnUsoException` tiene un nombre más estrecho que su regla.** Renombrarla a
  `CorreoUsuarioEnUsoException` toca HU-01, su controlador y sus pruebas; se deja para una limpieza
  propia.
- **Sin API REST de usuarios.** HU-02 se expone solo por MVC, como se pidió.
