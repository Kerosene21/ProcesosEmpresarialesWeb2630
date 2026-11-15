package co.edu.javeriana.procesosempresariales.config;

import java.util.List;
import java.util.Set;

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
import io.swagger.v3.oas.models.media.MapSchema;
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
    private static final String ESQUEMA_BEARER = "bearerAuth";
    private static final String ESQUEMA_CSRF = "csrf";
    private static final String ESQUEMA_COOKIE_REFRESCO = "cookieRefresco";
    private static final String RUTA_CSRF = "/api/auth/csrf";
    private static final String RUTA_LOGIN = "/api/auth/login";
    private static final String RUTA_REFRESH = "/api/auth/refresh";
    private static final String RUTA_LOGOUT = "/api/auth/logout";
    private static final String RUTA_REGISTRO_EMPRESA = "/api/empresas";
    private static final Set<String> RUTAS_CON_COOKIES = Set.of(RUTA_LOGIN, RUTA_REFRESH, RUTA_LOGOUT);
    private static final String ERROR_API = "ErrorApi";
    private static final String ERROR_VALIDACION = "ErrorValidacion";

    private static final String REFERENCIA_ESQUEMA = "#/components/schemas/";
    private static final String JSON = "application/json";
    private static final String DESCRIPCION = """
            API REST para la gestión y modelado de procesos empresariales BPMN multiempresa.

            El sistema **modela** procesos: no ejecuta procesos BPMN, no envía mensajes reales \
            ni invoca sistemas externos.

            **Autenticación.** La API es *stateless* y usa JWT. Cada operación protegida exige el encabezado \
            `Authorization: Bearer <token>` (botón *Authorize*, esquema `bearerAuth`). El token de acceso dura \
            15 minutos y se obtiene con `POST /api/auth/login`; `POST /api/auth/refresh` lo renueva con la \
            cookie HttpOnly `PE_REFRESH` y `POST /api/auth/logout` revoca la sesión. La cookie `JSESSIONID` \
            de las vistas web no autentica la API.

            **CSRF.** Solo login, refresh y logout, que dependen de cookies, exigen el encabezado \
            `X-XSRF-TOKEN` (esquema `csrf`) con el valor de la cookie `XSRF-TOKEN` que emite \
            `GET /api/auth/csrf`. Las demás operaciones no usan CSRF porque no se autentican con cookies.

            **Roles y estado.** El token no contiene roles ni empresa: en cada petición se consultan en la \
            base de datos el usuario, su estado activo, su rol y la sesión, así que un cambio de rol, una \
            desactivación o un cierre de sesión se aplican de inmediato.

            **Endpoints públicos.** `GET /api/auth/csrf` y el registro de empresas (`POST /api/empresas`) no \
            requieren autenticación.

            **Multiempresa.** La empresa se toma del usuario autenticado. Cada empresa solo ve y \
            modifica sus propios recursos; los procesos que otra empresa le comparte son de solo \
            lectura y la empresa propietaria conserva la edición.

            **Errores.** Las reglas de negocio, la autenticación y los permisos responden \
            `{ "codigo", "mensaje" }` (`ErrorApi`). Bean Validation responde `DATOS_NO_VALIDOS` con el \
            mensaje de cada campo en `campos`. JSON mal formado o parámetros con tipo inválido responden \
            el error estándar de Spring Boot (`ErrorValidacion`).
            """;

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Procesos Empresariales Web API")
                        .description(DESCRIPCION)
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes(ESQUEMA_BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token de acceso del campo `tokenAcceso` que responden "
                                        + "`POST /api/auth/login` y `POST /api/auth/refresh`. Se escribe sin el "
                                        + "prefijo `Bearer`."))
                        .addSecuritySchemes(ESQUEMA_CSRF, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-XSRF-TOKEN")
                                .description("Token CSRF, obligatorio solo en login, refresh y logout. Es el valor "
                                        + "de la cookie `XSRF-TOKEN`, que emite `GET /api/auth/csrf`."))
                        .addSecuritySchemes(ESQUEMA_COOKIE_REFRESCO, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("PE_REFRESH")
                                .description("Refresh token opaco en una cookie HttpOnly, SameSite=Strict, limitada "
                                        + "a `/api/auth`. La emite el login y el navegador la envía "
                                        + "automáticamente; JavaScript no puede leerla.")));
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

    private Acceso acceso(String ruta, PathItem.HttpMethod metodo) {
        if (RUTAS_CON_COOKIES.contains(ruta)) {
            return Acceso.CSRF_Y_COOKIES;
        }
        if (RUTA_CSRF.equals(ruta) || (RUTA_REGISTRO_EMPRESA.equals(ruta) && metodo == PathItem.HttpMethod.POST)) {
            return Acceso.PUBLICO;
        }
        return Acceso.BEARER;
    }

    private void documentar(String ruta, PathItem.HttpMethod metodo, Operation operacion) {
        Acceso acceso = acceso(ruta, metodo);
        boolean escritura = metodo != PathItem.HttpMethod.GET;
        boolean recursoIdentificado = ruta.contains("{");
        ApiResponses respuestas = operacion.getResponses();
        if (metodo == PathItem.HttpMethod.DELETE) {
            respuestas.remove("200");
            agregarSiFalta(respuestas, "204", "Recurso eliminado; sin contenido.");
        }
        if (acceso == Acceso.BEARER) {
            agregarSiFalta(respuestas, "401", "Falta el token de acceso o no es válido: ausente, mal formado, "
                    + "expirado, de una sesión revocada o de un usuario desactivado (`USUARIO_NO_AUTORIZADO`, "
                    + "con `WWW-Authenticate: Bearer`).");
        }
        if (operacion.getRequestBody() != null) {
            agregarSiFalta(respuestas, "400", "Datos no válidos: Bean Validation (`DATOS_NO_VALIDOS`, "
                    + "`ErrorApi` con `campos`), JSON mal formado (`ErrorValidacion`) o una regla del modelo "
                    + "incumplida (`ErrorApi`).");
        }
        if (tieneFiltros(operacion)) {
            agregarSiFalta(respuestas, "400", "Un filtro tiene un valor no válido (`ErrorValidacion`).");
        }
        if (acceso == Acceso.BEARER && (escritura || recursoIdentificado)) {
            agregarSiFalta(respuestas, "403", "Sin permiso (`USUARIO_SIN_PERMISO`): el rol del usuario no "
                    + "permite la operación o el recurso pertenece a otra empresa.");
        }
        if (acceso == Acceso.CSRF_Y_COOKIES) {
            agregarSiFalta(respuestas, "403", "Falta el encabezado `X-XSRF-TOKEN` o no coincide con la cookie "
                    + "`XSRF-TOKEN` (`USUARIO_SIN_PERMISO`).");
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
        operacion.setSecurity(requisitos(ruta, acceso));
    }

    private List<SecurityRequirement> requisitos(String ruta, Acceso acceso) {
        if (acceso == Acceso.PUBLICO) {
            return List.of();
        }
        if (acceso == Acceso.BEARER) {
            return List.of(new SecurityRequirement().addList(ESQUEMA_BEARER));
        }
        if (RUTA_LOGIN.equals(ruta)) {
            return List.of(new SecurityRequirement().addList(ESQUEMA_CSRF));
        }
        SecurityRequirement conCookie = new SecurityRequirement().addList(ESQUEMA_CSRF)
                .addList(ESQUEMA_COOKIE_REFRESCO);
        return RUTA_LOGOUT.equals(ruta)
                ? List.of(conCookie, new SecurityRequirement().addList(ESQUEMA_CSRF))
                : List.of(conCookie);
    }

    private enum Acceso {
        PUBLICO, CSRF_Y_COOKIES, BEARER
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
                .addProperty("campos", new MapSchema().additionalProperties(new StringSchema())
                        .description("Solo en `DATOS_NO_VALIDOS`: mensaje de validación de cada campo."))
                .required(List.of("codigo", "mensaje"));
    }

    private Schema<?> errorValidacion() {
        return new ObjectSchema()
                .description("Error estándar de Spring Boot para JSON mal formado o parámetros con tipo "
                        + "inválido.")
                .addProperty("timestamp", new DateTimeSchema())
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("error", new StringSchema().example("Bad Request"))
                .addProperty("path", new StringSchema().example("/api/procesos"));
    }
}
