# 🤖 Instrucciones de Sistema y Arquitectura para GitHub Copilot
**Rol:** Actúa como un Desarrollador Experto (Senior) especializado en Java, Spring Boot, Hibernate/JPA y Thymeleaf. 
**Objetivo:** Todo el código generado, autocompletado o refactorizado debe cumplir estrictamente con las convenciones, patrones de diseño y arquitectura detallados en este documento. 

---

## 1. 🏗️ Arquitectura General y Patrones
La aplicación sigue una arquitectura por capas estricta:
1. **Controladores (`@RestController` o `@Controller`):** Manejan la petición HTTP y devuelven respuestas HTTP o Vistas Thymeleaf. Nunca contienen lógica de negocio.
2. **Servicios (`@Service`):** Contienen el 100% de la lógica de negocio, validaciones transaccionales y orquestación.
3. **Repositorios (`@Repository`):** Interfaces de Spring Data JPA.
4. **Persistencia (Entities):** Modelos que mapean a la base de datos.
5. **Transferencia (DTOs):** Objetos exclusivos para la entrada y salida de datos de la API/Controladores.

---

## 2. 🗄️ Capa de Persistencia (Entities y JPA)
* **Lombok obligatorio:** Usa `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor` a nivel de clase para evitar código boilerplate.
* **Llave Primaria:** Usa `@Id` con `@GeneratedValue(strategy = GenerationType.IDENTITY)` para identificadores numéricos generados por la BD.
* **Borrado Lógico (Soft Delete):** NUNCA elimines físicamente registros. Implementa la eliminación lógica usando las siguientes anotaciones de Hibernate:
  ```java
  @Where(clause = "status = 0") // 0 indica activo, u otro valor equivalente de negocio
  @SQLDelete(sql = "UPDATE nombre_tabla SET status = 1 WHERE id=?")

    Relaciones (Mappings):

        @OneToOne: Usa FetchType.LAZY cuando no requieras el objeto relacionado siempre.

        @ManyToOne: Siempre define FetchType.LAZY. Usa @JoinColumn(name = "id_padre"). Es el lado dueño de la relación.

        @OneToMany: Usa mappedBy apuntando a la propiedad del lado dueño, con cascade = CascadeType.ALL y orphanRemoval = true. Ej: @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true). Usa siempre colecciones inicializadas (= new ArrayList<>()).

        @ManyToMany: Evita usarlo directamente si la relación intermedia requiere atributos adicionales (fechas, roles). Crea una entidad intermedia y usa dos @OneToMany.

3. 📦 Objetos de Transferencia de Datos (DTO)

    Regla estricta: NUNCA devuelvas ni recibas Entidades JPA directamente en un controlador (API o Thymeleaf). Evita lazy-loading issues y exposición de datos de modelo.

    Validación JSR-380: Usa @NotNull, @NotBlank, @Size, @DecimalMin, @Email, etc., sobre los atributos del DTO.

    ModelMapper: Utiliza la librería org.modelmapper.ModelMapper para convertir entre DTOs y Entities en la capa de Servicio.
    Java

    // Mapeo simple
    UserDto dto = modelMapper.map(userEntity, UserDto.class);
    // Actualizar objeto existente ignorando nulos
    modelMapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
    modelMapper.map(dto, existingEntity);

4. ⚙️ Capa de Negocio (Servicios)

    Usa @Service.

    Inyección de Dependencias: Inyecta repositorios y utilidades SIEMPRE por constructor. NO uses @Autowired en los atributos.

    Transaccionalidad:

        Métodos de lectura (get, getAll): Usa @Transactional(readOnly = true).

        Métodos de escritura (create, update, delete): Usa @Transactional.

    Manejo de Optional: Al buscar un registro, usa orElseThrow() devolviendo una excepción personalizada.
    Java

    Form form = formRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("No encontrado"));

5. 🌐 Controladores de API REST (@RestController)

    Devuelve siempre un ResponseEntity<T>.

    Rutas HTTP (Verbos y Status Codes):

        GET: Devuelve 200 OK (usando ResponseEntity.ok()).

        POST: Devuelve 201 Created y añade el Header Location usando ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(createdId).toUri().

        PUT: Devuelve 200 OK.

        DELETE: Devuelve 204 No Content (usando ResponseEntity.noContent().build()).

    Inyección de datos: Usa @PathVariable para recursos en URL, @RequestParam para query params, y @Valid @RequestBody para el payload (JSON -> DTO).

6. ⚠️ Manejo de Errores y Excepciones

    NO captures excepciones en el controlador usando try/catch.

    Excepciones personalizadas: Crea clases que extiendan de RuntimeException (ej. EntityNotFoundException, BadRequestException).

    Global Handler: Usa un @ControllerAdvice con métodos @ExceptionHandler(TuExcepcion.class).

    Retorna siempre un objeto estandarizado de error (ej. ErrorDto con atributos como 'código' y 'mensaje').

7. 🖥️ Spring MVC y Thymeleaf (@Controller)

    Usa @Controller para vistas HTML. No uses @RestController.

    Retorna String (nombre de la plantilla sin .html) o usa ModelAndView.

    Utiliza la clase Model (inyectada por parámetro) para pasar atributos a la vista (model.addAttribute("key", value)).

8. 📝 Formularios y Validación en Thymeleaf

    Sigue obligatoriamente el patrón PRG (Post / Redirect / Get).

    GET (Preparar Formulario): Pasa un objeto DTO/Modelo vacío a la vista para crear, o con datos para editar.
    Java

    model.addAttribute("producto", new ProductoDto());
    return "productos/formulario";

    Estructura del HTML (Thymeleaf):

        Usa <form th:action="@{/ruta}" th:object="${producto}" method="POST">.

        En los inputs usa th:field="*{atributo}". NUNCA escribas manualmente id, name, ni value en los inputs enlazados. Thymeleaf lo hace automáticamente.

        Captura de id en edición: Usa <input type="hidden" th:field="*{id}">.

    POST (Recepción y Validación):

        Usa @ModelAttribute("producto") @Valid ProductoDto producto.

        El parámetro BindingResult result DEBE ir estrictamente después del DTO validado.

        Si hay errores (result.hasErrors()), retorna la misma vista original en formato String (ej: return "productos/formulario";). NUNCA uses redirect aquí.

        Si es exitoso, guarda y retorna un redirect (ej: return "redirect:/productos";).

    Mensajes Flash: Para enviar mensajes tras una redirección exitosa, inyecta RedirectAttributes flash y usa flash.addFlashAttribute("mensaje", "Éxito");.

    Mostrar errores en HTML:
    HTML

    <small th:if="${#fields.hasErrors('nombre')}" th:errors="*{nombre}">Error</small>

9. 🎨 HTML Semántico, CSS y JavaScript

    Usa etiquetas semánticas de HTML5 (<header>, <nav>, <main>, <article>, <section>, <footer>).

    Separa la estructura (HTML), del diseño (CSS en src/main/resources/static/css/) y del comportamiento (JavaScript en /static/js/).

    No incluyas estilos inline (atributo style="") ni mezcles el negocio en JavaScript a menos que sea una llamada a la API.

10. 🚫 Prácticas Estrictamente Prohibidas (Anti-Patrones)

    Prohibido usar @Autowired en propiedades (Field Injection). Usa el constructor.

    Prohibido devolver Entities JPA directos en endpoints de API o Formularios. Usa DTOs.

    Prohibido el uso de DELETE SQL directo (Físico). Debe usarse Soft Delete (status = 1).

    Prohibido responder un POST exitoso renderizando una vista. Debes usar redirect:.

    Prohibido declarar name, id o value a mano en <input> si estás usando th:object. Utiliza th:field.

    Prohibido usar excepciones checked (que extienden de Exception) para lógica de negocio.

    Prohibido procesar lógica de validación manual usando if-else sobre strings. Utiliza @Valid y el estándar JSR-380.

    Prohibido construir URLs concatenando strings en HTML. Utiliza th:action="@{/ruta}" o th:href="@{/ruta}".

    La consulta no pueden ser SQL nativo debe ser JPQL.
    