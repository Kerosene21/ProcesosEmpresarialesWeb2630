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

## Qué implementamos

Se completó la vertical de registro de empresas siguiendo la arquitectura del curso
(Entity → Repository → DTO → Service → Controller → Thymeleaf):

- La entidad `Empresa` pasó de tener únicamente `id` y `nombre` a incluir también `nit` y
  `correoContacto`, con restricciones declaradas en el modelo JPA.
- La unicidad global del NIT se expresa en dos capas: una restricción única en la tabla
  (`uk_empresa_nit`) y una verificación previa en el servicio que produce un mensaje entendible
  para el usuario en lugar de un error de base de datos.
- El registro crea, dentro de la misma transacción, el usuario administrador inicial de la empresa.
- El formulario valida los campos obligatorios y muestra cada error junto a su campo.

La creación y edición de procesos (HU-04 y HU-05) no se modificó.

## Flujo

```
GET  /empresas/nueva   → formulario.html          (RegistroEmpresaDto vacío)
POST /empresas         → EmpresaController.registrar
                          ├─ BindingResult con errores → vuelve a formulario.html
                          └─ sin errores → EmpresaService.registrar(dto)
                                            ├─ normaliza nombre, NIT y correo
                                            ├─ EmpresaRepository.existsByNit(nit)
                                            │    └─ si existe → NitEmpresaDuplicadoException
                                            ├─ UsuarioRepository.existsByUsername(correo)
                                            │    └─ si existe → CorreoAdministradorEnUsoException
                                            ├─ EmpresaRepository.save(empresa)      → PostgreSQL
                                            └─ UsuarioRepository.save(administrador) → PostgreSQL
                          → redirect (Post/Redirect/Get) a /empresas/{id}
GET  /empresas/{id}    → detalle.html
GET  /empresas         → lista.html
```

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
- **Correo no reutilizable**: si el correo ya identifica a otro usuario, el registro se rechaza
  antes de escribir nada en la base de datos.
- **Procesos con el mismo nombre en empresas distintas**: ya estaba garantizado por la restricción
  `uk_proceso_empresa_nombre` sobre `(empresa_id, nombre)` y no se modificó.

## Tests

Todos son tests unitarios que no requieren PostgreSQL ni el contexto de Spring.

### `EmpresaServiceTest` (12 pruebas, Mockito)

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
| `obtenerDevuelveLaEmpresaJuntoASuAdministradorInicial` | Que el detalle incluye los datos y el administrador |
| `obtenerFallaCuandoLaEmpresaNoExiste` | Que un id inexistente produce `RecursoNoEncontradoException` |
| `listarDevuelveTodasLasEmpresasRegistradas` | Que el listado devuelve las empresas en orden y con sus datos |

### `RegistroEmpresaDtoTest` (8 pruebas, Bean Validation)

Verifican que un formulario completo no produce errores, que cada campo obligatorio se señala
cuando falta, que el correo con formato inválido se rechaza, que se respetan las longitudes máximas
de nombre y NIT, y que un formulario totalmente vacío señala exactamente los tres campos.

## Cómo demostrarla

1. Levantar PostgreSQL y definir `DB_PASSWORD` en el archivo `.env`.
2. Arrancar la aplicación con `./mvnw spring-boot:run`.
3. Abrir `http://localhost:8080/empresas/nueva`.
4. Enviar el formulario vacío: se muestran los tres mensajes de campo obligatorio.
5. Escribir un correo sin formato válido: se muestra el error junto al campo del correo.
6. Registrar una empresa con datos correctos: la aplicación redirige al detalle y muestra el
   mensaje de éxito junto al administrador inicial creado.
7. Registrar una segunda empresa con el mismo NIT: el formulario vuelve con el error de NIT
   duplicado sobre ese campo, sin página de error.
8. Registrar una segunda empresa con NIT distinto pero el mismo correo: el error aparece sobre el
   campo del correo.
9. Abrir `http://localhost:8080/empresas` para ver el listado con las empresas registradas.

## Qué quedó pendiente

- **Credencial del administrador inicial (bloqueado por HU-03).** El administrador se crea con su
  identidad (el correo de contacto), su rol `ADMINISTRADOR` y su empresa, pero **no tiene
  contraseña**, porque la entidad `Usuario` todavía no tiene ese campo y la estrategia de
  autenticación y cifrado se define en HU-03. No se inventó ninguna contraseña por defecto ni se
  guardó nada en texto plano. Por este motivo **HU-01 no se considera 100 % completa**.
- **Campo de correo propio en `Usuario` (HU-02).** Hoy el administrador se identifica reutilizando
  `username`. Cuando HU-02 incorpore la invitación de usuarios por correo, conviene evaluar si
  `Usuario` debe tener un campo `correo` separado de `username`.
- **Desactivación de usuarios (HU-02).** `Usuario` todavía no tiene un indicador de activo.
- **Consulta de una empresa inexistente desde el navegador.** `ApiExceptionHandler` es un
  `@RestControllerAdvice`, así que `GET /empresas/{id}` con un id que no existe responde en JSON en
  lugar de una página de error. Corregirlo implica revisar la estrategia global de errores, que es
  compartida con HU-04 y HU-05 y se abordará en un bloque propio.
- **Pruebas de repositorio contra PostgreSQL.** La unicidad del NIT está verificada en el servicio,
  pero la restricción `uk_empresa_nit` a nivel de tabla se validará en la máquina virtual del curso.
