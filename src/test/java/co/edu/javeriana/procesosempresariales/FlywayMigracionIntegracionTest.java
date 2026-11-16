package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlywayMigracionIntegracionTest {

    private static final String ESQUEMA = "flyway_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    private static final List<String> TABLAS = List.of("actividad", "arco", "empresa", "evento", "familia_refresco",
            "gateway", "historial_proceso", "historial_rol_proceso", "lane", "permiso_estructura_proceso", "pool",
            "proceso", "proceso_compartido_empresa", "refresh_token", "rol_proceso", "usuario");
    private static final List<String> INDICES = List.of("ix_arco_proceso_activo", "ix_arco_origen",
            "ix_arco_destino", "ix_evento_proceso", "ix_evento_pool", "ix_evento_pool_destino",
            "ix_familia_refresco_usuario", "ix_familia_refresco_expira", "ix_gateway_proceso", "ix_gateway_pool",
            "ix_historial_rol_proceso_rol", "ix_lane_rol_proceso", "ix_lane_pool", "ix_pool_proceso",
            "ix_proceso_compartido_invitada", "ix_refresh_token_familia", "ix_refresh_token_expira");
    private static final List<String> UNICAS = List.of("uk_empresa_nit", "uk_usuario_username",
            "uk_proceso_empresa_nombre", "uk_rol_proceso_empresa_nombre", "uk_actividad_proceso_nombre",
            "uk_permiso_estructura_proceso_rol", "uk_proceso_compartido_empresa", "uk_refresh_token_hash");
    private static final String CORREO = "admin@flyway-esquema.com";
    private static final String PASSWORD = "Clave-Flyway-2026";

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment entorno;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void esquemaDesechable(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/procesos_empresariales_test?currentSchema=" + ESQUEMA);
        registro.add("spring.flyway.enabled", () -> "true");
        registro.add("spring.flyway.schemas", () -> ESQUEMA);
        registro.add("spring.flyway.default-schema", () -> ESQUEMA);
        registro.add("spring.flyway.baseline-on-migrate", () -> "false");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.jpa.properties.hibernate.default_schema", () -> ESQUEMA);
    }

    @AfterAll
    void eliminarEsquema() {
        jdbcTemplate.execute("drop schema if exists " + ESQUEMA + " cascade");
    }

    private List<String> constraints(char tipo) {
        return jdbcTemplate.queryForList("select c.conname from pg_constraint c join pg_namespace n "
                + "on n.oid = c.connamespace where n.nspname = ? and c.contype = ?", String.class, ESQUEMA,
                String.valueOf(tipo));
    }

    @Test
    @Order(1)
    void laPrimeraEjecucionCreaTodasLasTablasYRegistraV1() {
        List<MigrationInfo> versionadas = Arrays.stream(flyway.info().applied())
                .filter(migracion -> migracion.getVersion() != null)
                .toList();

        assertThat(versionadas).hasSize(1);
        assertThat(versionadas.get(0).getVersion().getVersion()).isEqualTo("1");
        assertThat(versionadas.get(0).getDescription()).isEqualTo("esquema inicial");
        assertThat(versionadas.get(0).getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(jdbcTemplate.queryForList("select type || ':' || coalesce(version, '-') || ':' || success from "
                + ESQUEMA + ".flyway_schema_history order by installed_rank", String.class))
                .containsExactly("SCHEMA:-:true", "SQL:1:true");
        List<String> esperadas = new ArrayList<>(TABLAS);
        esperadas.add("flyway_schema_history");
        assertThat(jdbcTemplate.queryForList("select table_name from information_schema.tables "
                + "where table_schema = ? and table_type = 'BASE TABLE'", String.class, ESQUEMA))
                .containsExactlyInAnyOrderElementsOf(esperadas);
    }

    @Test
    @Order(2)
    void creaLosIndicesLlavesYRestriccionesDelModelo() {
        assertThat(jdbcTemplate.queryForList("select indexname from pg_indexes where schemaname = ?", String.class,
                ESQUEMA)).containsAll(INDICES);
        assertThat(constraints('u')).containsExactlyInAnyOrderElementsOf(UNICAS);
        assertThat(constraints('f')).hasSize(24)
                .contains("fk_familia_refresco_usuario", "fk_refresh_token_familia");
        assertThat(constraints('p')).hasSize(TABLAS.size() + 1);
        assertThat(constraints('c')).hasSize(16).contains("ck_lane_nombre_o_rol_proceso", "pool_tipo_check",
                "familia_refresco_motivo_revocacion_check");
        assertThat(jdbcTemplate.queryForObject("select c.confdeltype from pg_constraint c join pg_namespace n "
                + "on n.oid = c.connamespace where n.nspname = ? and c.conname = 'fk_refresh_token_familia'",
                String.class, ESQUEMA)).isEqualTo("c");
    }

    @Test
    @Order(3)
    void laColumnaTipoDePoolConservaSuLongitudValorPorDefectoYDominio() {
        Map<String, Object> tipo = jdbcTemplate.queryForMap("select data_type, character_maximum_length, "
                + "column_default, is_nullable from information_schema.columns where table_schema = ? "
                + "and table_name = 'pool' and column_name = 'tipo'", ESQUEMA);

        assertThat(tipo).containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 20)
                .containsEntry("is_nullable", "NO");
        assertThat((String) tipo.get("column_default")).startsWith("'PROPIETARIO'");
    }

    @Test
    @Order(4)
    void unaSegundaEjecucionNoAplicaNadaYLaValidacionPasa() {
        MigrateResult resultado = flyway.migrate();

        assertThat(resultado.migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + ESQUEMA + ".flyway_schema_history",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @Order(5)
    void hibernateValidaElEsquemaCreadoPorFlyway() {
        assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");

        entityManagerFactory.unwrap(SessionFactory.class).getSchemaManager().validateMappedObjects();
    }

    @Test
    @Order(6)
    void losFlujosJwtYCrudFuncionanSobreElEsquemaMigrado() throws Exception {
        ClientePruebaJwt cliente = new ClientePruebaJwt(mockMvc);
        mockMvc.perform(post("/api/empresas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Flyway Esquema\",\"nit\":\"906000001-1\",\"correoContacto\":\"" + CORREO
                        + "\",\"passwordAdministrador\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        AccesoJwt acceso = cliente.iniciarSesion(CORREO, PASSWORD);

        String creado = mockMvc.perform(post("/api/procesos").with(acceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Compras\",\"descripcion\":\"Proceso migrado\",\"categoria\":\"Flyway\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long procesoId = ((Number) JsonPath.read(creado, "$.id")).longValue();
        mockMvc.perform(put("/api/procesos/" + procesoId).with(acceso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Compras editado\",\"descripcion\":\"Proceso migrado\","
                        + "\"categoria\":\"Flyway\",\"estado\":\"PUBLICADO\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/procesos/" + procesoId).with(acceso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Compras editado"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
        assertThat(cliente.intentarRefrescar(acceso.refresco()).getResponse().getStatus()).isEqualTo(200);

        assertThat(jdbcTemplate.queryForList("select tipo from " + ESQUEMA + ".pool where proceso_id = ?",
                String.class, procesoId)).containsExactly("PROPIETARIO");
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + ESQUEMA + ".historial_proceso "
                + "where proceso_id = ?", Integer.class, procesoId)).isPositive();
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + ESQUEMA + ".refresh_token",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select count(*) from " + ESQUEMA + ".empresa where nit = ?",
                Integer.class, "906000001-1")).isEqualTo(1);
    }
}
