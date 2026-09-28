package co.edu.javeriana.procesosempresariales.config;

import java.util.List;

import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
@PropertySource(value = "classpath:openapi.properties", encoding = "UTF-8")
public class OpenApiConfig {

    private static final String GRUPO_API = "api-rest";
    private static final String ESQUEMA_SESION = "sesion";
    private static final String ESQUEMA_CSRF = "csrf";
    private static final String ERROR_API = "ErrorApi";
    private static final String ERROR_VALIDACION = "ErrorValidacion";

    private static final String REFERENCIA_ESQUEMA = "#/components/schemas/";
    private static final String JSON = "application/json";
    private static final String DESCRIPCION = """
            API REST para la gestión y modelado de procesos empresariales BPMN multiempresa.

            El sistema **modela** procesos: no ejecuta procesos BPMN, no envía mensajes reales \
            ni invoca sistemas externos.

            **Autenticación.** La API usa la misma sesión que la aplicación web. Inicia sesión en \
            `/login` en este navegador y Swagger UI reutilizará la cookie `JSESSIONID`.

            **Operaciones de escritura.** `POST`, `PUT` y `DELETE` exigen el token CSRF de la sesión \
            en el encabezado `X-CSRF-TOKEN` (botón *Authorize*, esquema `csrf`).

            **Multiempresa.** La empresa se toma del usuario autenticado. Cada empresa solo ve y \
            modifica sus propios recursos; los procesos que otra empresa le comparte son de solo \
            lectura y la empresa propietaria conserva la edición.

            **Errores.** Las reglas de negocio, la autenticación y los permisos responden \
            `{ "codigo", "mensaje" }` (`ErrorApi`). Bean Validation, JSON mal formado o parámetros \
            con tipo inválido responden el error estándar de Spring Boot (`ErrorValidacion`).
            """;

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Procesos Empresariales Web API")
                        .description(DESCRIPCION)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_SESION, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Sesión creada por el formulario de `/login`. El navegador la envía "
                                        + "automáticamente; no hace falta escribir ningún valor aquí."))
                        .addSecuritySchemes(ESQUEMA_CSRF, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-CSRF-TOKEN")
                                .description("Token CSRF de la sesión, obligatorio en POST, PUT y DELETE. Se "
                                        + "obtiene del campo oculto `_csrf` de cualquier formulario de la aplicación "
                                        + "después de iniciar sesión (por ejemplo, el de cerrar sesión en "
                                        + "`/empresas`).")));
    }

    @Bean
    GroupedOpenApi apiRest() {
        return GroupedOpenApi.builder()
                .group(GRUPO_API)
                .displayName("API REST")
                .pathsToMatch("/api/**")
                .build();
    }

    @Bean
    GlobalOperationCustomizer identificadorDeOperacion() {
        return (operacion, metodo) -> operacion.operationId(metodo.getMethod().getName()
                + metodo.getBeanType().getSimpleName().replace("RestController", ""));
    }

    @Bean
    GlobalOpenApiCustomizer respuestasYSeguridad() {
        return openApi -> {
            openApi.getComponents()
                    .addSchemas(ERROR_API, errorApi())
                    .addSchemas(ERROR_VALIDACION, errorValidacion());
            openApi.getPaths().forEach((ruta, operaciones) -> operaciones.readOperationsMap()
                    .forEach((metodo, operacion) -> documentar(ruta, metodo, operacion)));
        };
    }

    private void documentar(String ruta, PathItem.HttpMethod metodo, Operation operacion) {
        boolean escritura = metodo != PathItem.HttpMethod.GET;
        boolean recursoIdentificado = ruta.contains("{");
        ApiResponses respuestas = operacion.getResponses();
        if (metodo == PathItem.HttpMethod.DELETE) {
            respuestas.remove("200");
            agregarSiFalta(respuestas, "204", "Recurso eliminado; sin contenido.");
        }
        agregarSiFalta(respuestas, "401", "No hay una sesión autenticada (`USUARIO_NO_AUTORIZADO`).");
        if (operacion.getRequestBody() != null) {
            agregarSiFalta(respuestas, "400", "Datos no válidos: Bean Validation o JSON mal formado "
                    + "(`ErrorValidacion`), o una regla del modelo incumplida (`ErrorApi`).");
        }
        if (tieneFiltros(operacion)) {
            agregarSiFalta(respuestas, "400", "Un filtro tiene un valor no válido (`ErrorValidacion`).");
        }
        if (escritura || recursoIdentificado) {
            agregarSiFalta(respuestas, "403", "Sin permiso (`USUARIO_SIN_PERMISO`): el rol del usuario no "
                    + "permite la operación o el recurso pertenece a otra empresa.");
        }
        if (recursoIdentificado) {
            agregarSiFalta(respuestas, "404", "El proceso o el recurso no existe o ya fue eliminado "
                    + "(`RECURSO_NO_ENCONTRADO`).");
        }
        respuestas.forEach((codigo, respuesta) -> {
            if (codigo.startsWith("4")) {
                respuesta.setContent(contenidoDeError(codigo));
            }
        });
        SecurityRequirement requisito = new SecurityRequirement().addList(ESQUEMA_SESION);
        operacion.setSecurity(List.of(escritura ? requisito.addList(ESQUEMA_CSRF) : requisito));
    }

    private boolean tieneFiltros(Operation operacion) {
        return operacion.getParameters() != null
                && operacion.getParameters().stream().anyMatch(parametro -> "query".equals(parametro.getIn()));
    }

    private void agregarSiFalta(ApiResponses respuestas, String codigo, String descripcion) {
        respuestas.putIfAbsent(codigo, new ApiResponse().description(descripcion));
    }

    private Content contenidoDeError(String codigo) {
        Schema<?> esquema = "400".equals(codigo)
                ? new ComposedSchema().oneOf(List.of(referencia(ERROR_API), referencia(ERROR_VALIDACION)))
                : referencia(ERROR_API);
        return new Content().addMediaType(JSON, new MediaType().schema(esquema));
    }

    private Schema<?> referencia(String esquema) {
        return new Schema<>().$ref(REFERENCIA_ESQUEMA + esquema);
    }

    private Schema<?> errorApi() {
        return new ObjectSchema()
                .description("Error de la API: código estable y mensaje legible.")
                .addProperty("codigo", new StringSchema().description("Código estable del error.")
                        .example("RECURSO_NO_ENCONTRADO"))
                .addProperty("mensaje", new StringSchema().description("Mensaje para el usuario.")
                        .example("El proceso no existe"))
                .addProperty("procesos", new ArraySchema().items(new StringSchema())
                        .description("Solo en `ROL_PROCESO_EN_USO`: procesos que usan el rol."))
                .required(List.of("codigo", "mensaje"));
    }

    private Schema<?> errorValidacion() {
        return new ObjectSchema()
                .description("Error estándar de Spring Boot para Bean Validation, JSON mal formado o "
                        + "parámetros con tipo inválido.")
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("error", new StringSchema().example("Bad Request"))
                .addProperty("path", new StringSchema().example("/api/procesos"));
    }
}
