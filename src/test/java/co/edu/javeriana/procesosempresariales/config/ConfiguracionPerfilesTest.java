package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class ConfiguracionPerfilesTest {

    private Properties cargar(String recurso, boolean deLasPruebas) throws IOException {
        URL ubicacion = Collections.list(getClass().getClassLoader().getResources(recurso)).stream()
                .filter(url -> url.toString().contains("test-classes") == deLasPruebas)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontro " + recurso));
        Properties propiedades = new Properties();
        try (InputStream entrada = ubicacion.openStream()) {
            propiedades.load(entrada);
        }
        return propiedades;
    }

    @Test
    void elPerfilProdMigraConFlywayYSoloValidaElEsquema() throws IOException {
        Properties prod = cargar("application-prod.properties", false);

        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(prod.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(prod.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
        assertThat(prod.getProperty("spring.flyway.validate-on-migrate")).isEqualTo("true");
        assertThat(prod.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(prod.getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }

    @Test
    void elPerfilProdTomaLaConexionYLosSecretosSoloDeVariablesDeEntorno() throws IOException {
        Properties prod = cargar("application-prod.properties", false);

        assertThat(prod.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(prod.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(prod.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(prod.getProperty("seguridad.jwt.secreto")).isEqualTo("${JWT_SECRET}");
        assertThat(prod.getProperty("spring.profiles.active")).isNull();
        assertThat(prod.stringPropertyNames()).noneMatch(clave -> clave.contains("demo"));
    }

    @Test
    void elPerfilPorDefectoYElDePruebasNoEjecutanFlyway() throws IOException {
        Properties principal = cargar("application.properties", false);
        Properties pruebas = cargar("application-test.properties", true);

        assertThat(principal.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(principal.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(pruebas.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(pruebas.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(pruebas.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/procesos_empresariales_test");
    }
}
