# HU-01 · Registro de empresa

## Qué pide la historia

Como representante de una empresa, quiero crear una cuenta empresarial para que mi organización
tenga un espacio propio donde administrar sus procesos.

Criterios de aceptación:

- Se solicita nombre de empresa, NIT u otro identificador y correo de contacto.
- El NIT debe ser único globalmente.
- La empresa queda registrada como entidad independiente.
- Se debe generar un usuario administrador inicial asociado a la empresa.
- Los campos obligatorios deben validarse y los errores mostrarse al usuario.
- Empresas diferentes pueden tener procesos con el mismo nombre.

> **Estado: completa.** El único punto que quedaba abierto —el administrador inicial se creaba sin
> contraseña— se cerró en el bloque de HU-03. El registro ahora pide también la contraseña del
> administrador, la guarda cifrada con BCrypt y deja al usuario activo, de modo que esas
> credenciales sirven para iniciar sesión de inmediato. El detalle de la autenticación está en
> [HU-03 · Inicio de sesión](HU-03-inicio-sesion.md).

## Qué implementamos

Se completó la vertical de registro de empresas siguiendo la arquitectura del curso
(Entity → Repository → DTO → Service → Controller → Thymeleaf):

- La entidad `Empresa` pasó de tener únicamente `id` y `nombre` a incluir también `nit` y
  `correoContacto`, con restricciones declaradas en el modelo JPA.
- La unicidad global del NIT se expresa en dos capas: una restricción única en la tabla
  (`uk_empresa_nit`) y una verificación previa en el servicio que produce un mensaje entendible
  para el usuario en lugar de un error de base de datos.
- El registro crea, dentro de la misma transacción, el usuario administrador inicial de la empresa,
  con su contraseña cifrada, su rol `ADMINISTRADOR` y en estado activo.
- El formulario valida los campos obligatorios y muestra cada error junto a su campo.

La creación y edición de procesos (HU-04 y HU-05) no se modificó.

## Flujo

```
GET  /empresas/nueva   → formulario.html          (RegistroEmpresaDto vacío)   [público]
POST /empresas         → EmpresaController.registrar                           [público]
                          ├─ BindingResult con errores → vuelve a formulario.html
                          └─ sin errores → EmpresaService.registrar(dto)
                                            ├─ normaliza nombre, NIT y correo
                                            ├─ exige la contraseña del administrador
                                            ├─ EmpresaRepository.existsByNit(nit)
                                            │    └─ si existe → NitEmpresaDuplicadoException
                                            ├─ UsuarioRepository.existsByUsername(correo)
                                            │    └─ si existe → CorreoAdministradorEnUsoException
                                            ├─ EmpresaRepository.save(empresa)      → PostgreSQL
                                            └─ UsuarioRepository.save(administrador) → PostgreSQL
                                                 password = BCrypt(contraseña), activo = true
                          → redirect (Post/Redirect/Get) a /empresas/{id}
GET  /empresas/{id}    → detalle.html   (solo la empresa propia)  [requiere sesión]
GET  /empresas         → lista.html     (solo la empresa propia)  [requiere sesión]
```

Las dos rutas de registro son públicas a propósito: quien registra una empresa todavía no tiene
usuario con el que autenticarse. Como el detalle sí exige sesión, el `redirect` posterior al
registro lleva al login y, tras iniciar sesión, Spring Security devuelve al usuario al detalle de
su empresa.

Las dos excepciones de negocio se capturan en el controller y se convierten en errores del campo
correspondiente (`nit` o `correoContacto`), de modo que el usuario ve el problema en el formulario
y no una página de error.

## Archivos principales

### Creados

| Archivo | Para qué sirve |
|---|---|
| `repository/EmpresaRepository.java` | Acceso a datos de empresa; aporta `existsByNit` para la regla de unicidad |
| `dto/RegistroEmpresaDto.java` | Objeto del formulario de registro, con las restricciones de validación |
| `dto/EmpresaRespuestaDto.java` | Representación de salida; evita exponer la entidad a las vistas |
| `service/EmpresaService.java` | Lógica de negocio de HU-01: registrar, listar y obtener |
| `exception/NitEmpresaDuplicadoException.java` | Señala que el NIT ya está registrado |
| `exception/CorreoAdministradorEnUsoException.java` | Señala que el correo ya identifica a otro usuario |
| `controller/EmpresaController.java` | Rutas MVC de empresa; delega toda la lógica en el servicio |
| `templates/empresas/formulario.html` | Formulario de registro con errores por campo |
| `templates/empresas/lista.html` | Listado de empresas registradas |
| `templates/empresas/detalle.html` | Detalle de una empresa y su administrador inicial |
| `static/css/empresas.css` | Estilos propios de las vistas de empresa |

### Modificados

| Archivo | Cambio |
|---|---|
| `domain/Empresa.java` | Se agregaron `nit` y `correoContacto` y las restricciones de columna y unicidad |
| `domain/Usuario.java` | `username` pasó a ser obligatorio y único; `rol` pasó a ser obligatorio |
| `repository/UsuarioRepository.java` | Se agregaron `existsByUsername` y la búsqueda del administrador de una empresa |
| `controller/ApiExceptionHandler.java` | Se registraron las dos excepciones nuevas como respuestas 409 |

### Modificados en el bloque de HU-03

| Archivo | Cambio |
|---|---|
| `dto/RegistroEmpresaDto.java` | Se agregó `passwordAdministrador`, obligatorio y de 8 a 100 caracteres |
| `domain/Usuario.java` | Se agregaron `password` (columna `password_hash`) y `activo` |
| `service/EmpresaService.java` | Cifra la contraseña con BCrypt, deja al administrador activo y restringe la consulta a la empresa propia |
| `controller/EmpresaController.java` | `lista` y `detalle` trabajan con el usuario autenticado |
| `templates/empresas/formulario.html` | Campo de contraseña del administrador |

## Reglas implementadas

- **Campos obligatorios**: nombre, NIT y correo de contacto se validan con Bean Validation y el
  formulario muestra cada mensaje junto al campo que lo produce.
- **Correo con formato válido**: el correo de contacto debe tener forma de email.
- **Longitudes**: nombre hasta 150, NIT hasta 20 y correo hasta 180 caracteres, coherentes con las
  columnas de la base de datos.
- **NIT único globalmente**: restricción `uk_empresa_nit` en la tabla más verificación previa en el
  servicio para dar retroalimentación clara.
- **Normalización**: se recortan espacios de los tres campos y el correo se guarda en minúsculas,
  de modo que la unicidad no se pueda burlar con espacios o mayúsculas.
- **Administrador inicial**: al registrar la empresa se crea un `Usuario` con rol `ADMINISTRADOR`
  asociado a esa empresa, identificado con el correo de contacto.
- **Credencial del administrador inicial**: el formulario exige una contraseña de entre 8 y 100
  caracteres. El servicio la cifra con BCrypt antes de guardarla y marca al usuario como activo, de
  modo que esas credenciales permiten iniciar sesión. La contraseña en claro no se guarda, no se
  devuelve en ningún DTO y no se reimprime en el formulario.
- **Correo no reutilizable**: si el correo ya identifica a otro usuario, el registro se rechaza
  antes de escribir nada en la base de datos.
- **Procesos con el mismo nombre en empresas distintas**: ya estaba garantizado por la restricción
  `uk_proceso_empresa_nombre` sobre `(empresa_id, nombre)` y no se modificó.

## Tests

Salvo `RegistroYLoginIntegracionTest`, todos son tests unitarios que no requieren PostgreSQL ni el
contexto de Spring.

### `EmpresaServiceTest` (20 pruebas, Mockito)

| Prueba | Qué verifica |
|---|---|
| `registrarPersisteLaEmpresaConLosDatosDelFormulario` | Que los datos que llegan al repositorio son los del formulario |
| `registrarNormalizaEspaciosYMayusculasDelCorreoAntesDePersistir` | Que se recortan espacios y el correo se guarda en minúsculas |
| `registrarDevuelveLaEmpresaConElIdentificadorAsignado` | Que la respuesta incluye el id generado y los datos guardados |
| `registrarRechazaUnNitYaRegistrado` | Que un NIT repetido lanza la excepción y no se guarda nada |
| `registrarVerificaLaUnicidadDelNitSobreElValorNormalizado` | Que la unicidad se comprueba sobre el NIT ya recortado |
| `registrarCreaElAdministradorInicialAsociadoALaEmpresaCreada` | Que el administrador queda ligado a la empresa recién creada |
| `registrarCreaElAdministradorInicialConRolAdministrador` | Que el rol asignado es `ADMINISTRADOR` |
| `registrarIdentificaAlAdministradorInicialConElCorreoDeContacto` | Que el administrador se identifica con el correo de contacto |
| `registrarRechazaUnCorreoYaUsadoPorOtroUsuario` | Que un correo ya usado impide el registro completo |
| `registrarGuardaLaContrasenaDelAdministradorComoHashBcrypt` | Que lo guardado es un hash BCrypt que corresponde a la contraseña enviada |
| `registrarNuncaGuardaLaContrasenaDelAdministradorEnClaro` | Que el valor almacenado no es ni contiene la contraseña original |
| `registrarDejaAlAdministradorInicialActivoParaQuePuedaIniciarSesion` | Que el administrador queda activo |
| `registrarRechazaUnFormularioSinContrasenaParaElAdministrador` | Que sin contraseña no se escribe nada en la base de datos |
| `laRespuestaDelRegistroNoExponeNingunCampoDeContrasena` | Que `EmpresaRespuestaDto` no tiene ningún campo de contraseña |
| `obtenerParaUsuarioDevuelveSuPropiaEmpresaJuntoAlAdministradorInicial` | Que el detalle incluye los datos y el administrador |
| `obtenerParaUsuarioRechazaLaConsultaDeUnaEmpresaAjena` | Que pedir otra empresa produce `UsuarioSinPermisoException` sin tocar la base de datos |
| `obtenerParaUsuarioFallaCuandoElUsuarioAutenticadoNoExiste` | Que una sesión que no corresponde a un usuario real se rechaza |
| `obtenerParaUsuarioFallaCuandoLaEmpresaPropiaYaNoExiste` | Que un id inexistente produce `RecursoNoEncontradoException` |
| `listarVisiblesParaDevuelveUnicamenteLaEmpresaDelUsuarioAutenticado` | Que el listado nunca consulta todas las empresas |
| `listarVisiblesParaFallaCuandoElUsuarioAutenticadoNoExiste` | Que el listado también exige un usuario real |

### `RegistroEmpresaDtoTest` (12 pruebas, Bean Validation)

Verifican que un formulario completo no produce errores, que cada campo obligatorio se señala
cuando falta, que el correo con formato inválido se rechaza, que se respetan las longitudes máximas
de nombre y NIT, que la contraseña del administrador es obligatoria y debe medir entre 8 y 100
caracteres, y que un formulario totalmente vacío señala exactamente los cuatro campos.

### `RegistroYLoginIntegracionTest` (6 pruebas, requiere PostgreSQL)

Registra una empresa de verdad contra la base de datos y comprueba que el administrador queda
persistido con hash, activo y con rol `ADMINISTRADOR`, que **esas credenciales permiten iniciar
sesión**, que una contraseña incorrecta no autentica, y que un usuario de una empresa no puede
consultar ni listar la empresa de otra.

## Cómo demostrarla

1. Levantar PostgreSQL y definir `DB_PASSWORD` en el archivo `.env`. Si la base de desarrollo ya
   existía, aplicar antes la migración descrita en
   [HU-03 · Migración de base de datos](HU-03-inicio-sesion.md#migración-de-base-de-datos).
2. Arrancar la aplicación con `./mvnw spring-boot:run`.
3. Abrir `http://localhost:8080/empresas/nueva`.
4. Enviar el formulario vacío: se muestran los cuatro mensajes de campo obligatorio.
5. Escribir un correo sin formato válido: se muestra el error junto al campo del correo.
6. Escribir una contraseña de menos de 8 caracteres: el error aparece junto a ese campo y el valor
   escrito no se reimprime.
7. Registrar una empresa con datos correctos. Como el detalle exige sesión, la aplicación lleva al
   login; iniciar sesión con el correo de contacto y la contraseña recién elegida y se vuelve
   automáticamente al detalle de la empresa, con su administrador inicial.
8. Registrar una segunda empresa con el mismo NIT: el formulario vuelve con el error de NIT
   duplicado sobre ese campo, sin página de error.
9. Registrar una segunda empresa con NIT distinto pero el mismo correo: el error aparece sobre el
   campo del correo.
10. Abrir `http://localhost:8080/empresas`: aparece únicamente la empresa del usuario autenticado.

## Qué quedó pendiente

### Resuelto en el bloque de HU-03

- **Credencial del administrador inicial.** El registro exige la contraseña, la guarda cifrada con
  BCrypt y deja al usuario activo. HU-01 queda completa.
- **Indicador de activo en `Usuario`.** Existe como columna `activo`, obligatoria.
- **Errores en JSON desde el navegador.** `ApiExceptionHandler` se limitó a los `@RestController` y
  se añadió `MvcExceptionHandler`, que devuelve una página de error para las vistas.

### Sigue pendiente

- **Campo de correo propio en `Usuario` (HU-02).** Se decidió **no** añadirlo: `username` ya es el
  correo normalizado y su restricción `uk_usuario_username` es la unicidad global que HU-02
  necesita. Duplicar el mismo valor en dos columnas obligaría a mantener dos restricciones
  sincronizadas sin aportar información nueva. Si en el futuro hiciera falta un nombre de acceso
  distinto del correo, se añadiría `correo` en ese momento sin cambiar el significado de la
  restricción actual.
- **Alta de usuarios dentro de la empresa (HU-02).** Solo existe el administrador inicial. No hay
  invitación de usuarios, cambio de rol ni activación/desactivación desde la interfaz.
- **Pruebas de repositorio contra PostgreSQL.** La unicidad del NIT está verificada en el servicio,
  pero la restricción `uk_empresa_nit` a nivel de tabla se validará en la máquina virtual del curso.
