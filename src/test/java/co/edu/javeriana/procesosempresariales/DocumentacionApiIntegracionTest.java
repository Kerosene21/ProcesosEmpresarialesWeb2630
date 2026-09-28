package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentacionApiIntegracionTest {

    private static final String API_DOCS = "/v3/api-docs";
    private static final String API_DOCS_GRUPO = "/v3/api-docs/api-rest";
    private static final String CODIGO = "$.codigo";
    private static final String RUTAS = "$.paths";
    private static final String MESSAGE_THROWS = "/api/procesos/{procesoId}/message-throws";
    private static final String MESSAGE_CATCHES = "/api/procesos/{procesoId}/message-catches";
    private static final String ENVIOS_EXTERNOS = "/api/procesos/{procesoId}/envios-externos";
    private static final String DIAGRAMA = "/api/procesos/{procesoId}/diagrama";
    private static final String ESQUEMAS = "$.components.schemas.";

    @Autowired
    private MockMvc mockMvc;

    private String documento(String ruta) throws Exception {
        return mockMvc.perform(get(ruta))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void laDefinicionOpenApiEsPublicaYDescribeLaApi() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.openapi").value(startsWith("3.")))
                .andExpect(jsonPath("$.info.title").value("Procesos Empresariales Web API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.info.description").value(startsWith(
                        "API REST para la gestión y modelado de procesos empresariales BPMN multiempresa.")))
                .andExpect(jsonPath("$.info.description").value(containsString("no ejecuta procesos BPMN")))
                .andExpect(jsonPath(RUTAS).isMap());
    }

    @Test
    void laDefinicionDocumentaLasRutasRealesDeTodasLasHistorias() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{id}/historial")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{procesoId}/actividades/{actividadId}")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{procesoId}/arcos")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{procesoId}/gateways/{gatewayId}/eliminacion")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/roles-proceso/{rolProcesoId}/historial")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{procesoId}/pools/{poolId}/lanes/orden")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{procesoId}/compartido-con/{empresaId}")))
                .andExpect(jsonPath(RUTAS, hasKey("/api/procesos/{procesoId}/permisos-estructura/{rol}")))
                .andExpect(jsonPath(RUTAS, hasKey(MESSAGE_THROWS)))
                .andExpect(jsonPath(RUTAS, hasKey(MESSAGE_CATCHES)))
                .andExpect(jsonPath(RUTAS, hasKey(ENVIOS_EXTERNOS)))
                .andExpect(jsonPath(RUTAS, hasKey(DIAGRAMA)));
    }

    @Test
    void soloSeDocumentaLaApiRestYNoLasVistasMvc() throws Exception {
        String json = documento(API_DOCS);
        Map<String, Object> rutas = JsonPath.read(json, RUTAS);

        assertThat(rutas.keySet()).allMatch(ruta -> ruta.startsWith("/api/"));
        assertThat(rutas).doesNotContainKeys("/procesos", "/empresas", "/login");
        assertThat(json).doesNotContain("ModelAndView");
    }

    @Test
    void elGrupoApiRestPublicaLaMismaDefinicion() throws Exception {
        String porDefecto = documento(API_DOCS);
        String grupo = documento(API_DOCS_GRUPO);

        Map<String, Object> rutasPorDefecto = JsonPath.read(porDefecto, RUTAS);
        Map<String, Object> rutasDelGrupo = JsonPath.read(grupo, RUTAS);
        assertThat(rutasDelGrupo.keySet()).isEqualTo(rutasPorDefecto.keySet());
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[0].url").value(API_DOCS_GRUPO));
    }

    @Test
    void laDefinicionEnYamlEsPublica() throws Exception {
        mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("openapi: 3.")))
                .andExpect(content().string(containsString(DIAGRAMA)));
        mockMvc.perform(get("/v3/api-docs.yaml/api-rest"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(ENVIOS_EXTERNOS)));
    }

    @Test
    void swaggerUiEsPublicoSinAutenticacion() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger-ui")));
    }

    @Test
    void laDocumentacionPublicaNoAbreLasOperacionesDeLaApi() throws Exception {
        mockMvc.perform(get("/api/procesos/1/diagrama"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));
        mockMvc.perform(get("/api/procesos/1/message-throws"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));
        mockMvc.perform(get("/api/roles-proceso"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lasRutasDeDocumentacionSoloSonPublicasParaConsultas() throws Exception {
        mockMvc.perform(post(API_DOCS).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "admin@documentacion.test", roles = "ADMINISTRADOR")
    void laProteccionCsrfDeLaApiSigueActiva() throws Exception {
        mockMvc.perform(post("/api/procesos/1/message-throws")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void laSeguridadDocumentadaEsLaSesionDelFormularioYElTokenCsrf() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath("$.components.securitySchemes.sesion.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.sesion.in").value("cookie"))
                .andExpect(jsonPath("$.components.securitySchemes.sesion.name").value("JSESSIONID"))
                .andExpect(jsonPath("$.components.securitySchemes.csrf.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.csrf.name").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.components.securitySchemes", not(hasKey("bearerAuth"))))
                .andExpect(jsonPath("$.paths['" + DIAGRAMA + "'].get.security[0]", hasKey("sesion")))
                .andExpect(jsonPath("$.paths['" + DIAGRAMA + "'].get.security[0]", not(hasKey("csrf"))))
                .andExpect(jsonPath("$.paths['" + MESSAGE_THROWS + "'].post.security[0]", hasKey("sesion")))
                .andExpect(jsonPath("$.paths['" + MESSAGE_THROWS + "'].post.security[0]", hasKey("csrf")));
    }

    @Test
    void cadaOperacionTieneResumenEtiquetaIdentificadorUnicoY401() throws Exception {
        String json = documento(API_DOCS);
        List<String> identificadores = JsonPath.read(json, "$.paths.*.*.operationId");
        List<String> resumenes = JsonPath.read(json, "$.paths.*.*.summary");
        List<String> etiquetas = JsonPath.read(json, "$.paths.*.*.tags[0]");
        List<Map<String, Object>> respuestas = JsonPath.read(json, "$.paths.*.*.responses");

        assertThat(identificadores).hasSize(64).doesNotHaveDuplicates().noneMatch(id -> id.contains("_"));
        assertThat(resumenes).hasSize(64).allMatch(resumen -> !resumen.isBlank());
        assertThat(etiquetas).hasSize(64);
        assertThat(respuestas).allSatisfy(respuesta -> assertThat(respuesta).containsKey("401"));
    }

    @Test
    void lasRespuestasDocumentanLosCodigosRealesDeCadaOperacion() throws Exception {
        String procesos = "$.paths['/api/procesos']";
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath(procesos + ".post.responses", hasKey("201")))
                .andExpect(jsonPath(procesos + ".post.responses", not(hasKey("200"))))
                .andExpect(jsonPath(procesos + ".post.responses", hasKey("409")))
                .andExpect(jsonPath(procesos + ".post.responses.201.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ProcesoRespuestaDto"))
                .andExpect(jsonPath(procesos + ".get.responses", hasKey("400")))
                .andExpect(jsonPath(procesos + ".get.responses", not(hasKey("404"))))
                .andExpect(jsonPath("$.paths['/api/procesos/{id}'].delete.responses", hasKey("204")))
                .andExpect(jsonPath("$.paths['/api/procesos/{id}'].delete.responses", not(hasKey("200"))))
                .andExpect(jsonPath("$.paths['/api/procesos/{id}'].delete.responses", hasKey("404")))
                .andExpect(jsonPath("$.paths['/api/roles-proceso/{rolProcesoId}'].delete.responses", hasKey("409")))
                .andExpect(jsonPath("$.paths['" + MESSAGE_CATCHES + "'].post.responses", hasKey("201")))
                .andExpect(jsonPath("$.paths['" + MESSAGE_CATCHES + "'].post.responses", hasKey("403")))
                .andExpect(jsonPath("$.paths['" + DIAGRAMA + "'].get.responses", hasKey("200")))
                .andExpect(jsonPath("$.paths['" + DIAGRAMA + "'].get.responses.200.content['application/json']"
                        + ".schema.$ref").value("#/components/schemas/DiagramaProcesoDto"));
    }

    @Test
    void losErroresUsanElContratoCodigoYMensaje() throws Exception {
        String crearThrow = "$.paths['" + MESSAGE_THROWS + "'].post.responses";
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath(ESQUEMAS + "ErrorApi.properties", hasKey("codigo")))
                .andExpect(jsonPath(ESQUEMAS + "ErrorApi.properties", hasKey("mensaje")))
                .andExpect(jsonPath(ESQUEMAS + "ErrorApi.required", containsInAnyOrder("codigo", "mensaje")))
                .andExpect(jsonPath(ESQUEMAS + "ErrorValidacion.properties", hasKey("timestamp")))
                .andExpect(jsonPath(crearThrow + ".409.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorApi"))
                .andExpect(jsonPath(crearThrow + ".401.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorApi"))
                .andExpect(jsonPath(crearThrow + ".400.content['application/json'].schema.oneOf[*].$ref",
                        contains("#/components/schemas/ErrorApi", "#/components/schemas/ErrorValidacion")))
                .andExpect(jsonPath(crearThrow + ".400.description", containsString("MENSAJE_ENTRE_MISMO_POOL")));
    }

    @Test
    void losDtosReflejanValidacionesYEnumeraciones() throws Exception {
        mockMvc.perform(get(API_DOCS))
                .andExpect(jsonPath(ESQUEMAS + "CrearMessageThrowDto.required",
                        hasItems("nombreMensaje", "contenido", "poolOrigenId", "poolDestinoId")))
                .andExpect(jsonPath(ESQUEMAS + "CrearMessageThrowDto.properties.nombreMensaje.maxLength").value(150))
                .andExpect(jsonPath(ESQUEMAS + "CrearMessageThrowDto.properties.claveCorrelacion.example")
                        .value("numeroRadicado"))
                .andExpect(jsonPath(ESQUEMAS + "CrearMessageCatchDto.properties.variante.enum",
                        contains("INICIO", "INTERMEDIO")))
                .andExpect(jsonPath(ESQUEMAS + "CrearMessageCatchDto.properties.comportamientoSinCaso.enum",
                        contains("DESCARTAR", "INICIAR_NUEVO_CASO")))
                .andExpect(jsonPath(ESQUEMAS + "CrearEnvioExternoDto.properties.tipoDestino.enum",
                        contains("CORREO", "SERVICIO_WEB", "COLA")))
                .andExpect(jsonPath(ESQUEMAS + "CrearEnvioExternoDto.properties.comportamientoFallo.enum",
                        contains("CONTINUAR", "RUTA_ERROR", "FINALIZAR")))
                .andExpect(jsonPath(ESQUEMAS + "CrearEnvioExternoDto.description",
                        containsString("no se hace ninguna llamada real")))
                .andExpect(jsonPath(ESQUEMAS + "CrearPoolDto.properties.tipo.enum",
                        contains("PROPIETARIO", "PARTICIPANTE", "EXTERNO")))
                .andExpect(jsonPath(ESQUEMAS + "CrearGatewayDto.properties.tipo.enum",
                        contains("EXCLUSIVO", "PARALELO", "INCLUSIVO")))
                .andExpect(jsonPath(ESQUEMAS + "CrearArcoDto.properties.origenTipo.enum",
                        contains("ACTIVIDAD", "GATEWAY", "EVENTO")))
                .andExpect(jsonPath("$.paths['/api/procesos/{procesoId}/permisos-estructura/{rol}'].put"
                        + ".parameters[?(@.name == 'rol')].schema.enum[*]",
                        contains("ADMINISTRADOR", "EDITOR", "SOLO_LECTURA")))
                .andExpect(jsonPath("$.paths['/api/procesos'].get.parameters[?(@.name == 'alcance')].schema.enum[*]",
                        contains("PROPIOS", "COMPARTIDOS", "TODOS")))
                .andExpect(jsonPath("$.paths['/api/roles-proceso'].get.parameters[?(@.name == 'visibilidad')]"
                        + ".schema.enum[*]", contains("ACTIVOS", "INACTIVOS", "TODOS")));
    }

    @Test
    void laEmpresaDelUsuarioNoSeRecibeComoParametro() throws Exception {
        String json = documento(API_DOCS);
        Map<String, Object> rutas = JsonPath.read(json, RUTAS);
        List<String> ubicacionesDeEmpresa = JsonPath.read(json,
                "$.paths.*.*.parameters[?(@.name == 'empresaId')].in");
        List<String> cuerpos = JsonPath.read(json, "$.paths.*.*.requestBody.content['application/json'].schema.$ref");

        assertThat(rutas.keySet().stream().filter(ruta -> ruta.contains("empresa")).toList())
                .containsExactly("/api/procesos/{procesoId}/compartido-con/{empresaId}");
        assertThat(ubicacionesDeEmpresa).containsExactly("path", "path");
        assertThat(cuerpos).isNotEmpty();
        for (String referencia : cuerpos) {
            String esquema = referencia.substring(referencia.lastIndexOf('/') + 1);
            Map<String, Object> propiedades = JsonPath.read(json, ESQUEMAS + esquema + ".properties");
            assertThat(propiedades).doesNotContainKey("empresaId");
        }
    }
}
