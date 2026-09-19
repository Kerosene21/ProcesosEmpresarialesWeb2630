package co.edu.javeriana.procesosempresariales.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;

class CrearUsuarioDtoTest {

    private static final String CORREO = "editor@alpes.com";
    private static final String PASSWORD = "Clave-Usuario-2026";

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void abrirValidador() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        validatorFactory.close();
    }

    private Set<String> camposConError(CrearUsuarioDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        CrearUsuarioDto dto = new CrearUsuarioDto(CORREO, PASSWORD, RolUsuario.EDITOR);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void elCorreoEsObligatorio() {
        CrearUsuarioDto dto = new CrearUsuarioDto("   ", PASSWORD, RolUsuario.EDITOR);

        assertThat(camposConError(dto)).contains("correo");
    }

    @Test
    void elCorreoDebeTenerFormatoDeEmail() {
        CrearUsuarioDto dto = new CrearUsuarioDto("editor-sin-arroba", PASSWORD, RolUsuario.EDITOR);

        assertThat(camposConError(dto)).contains("correo");
    }

    @Test
    void elCorreoNoPuedeSuperarLaLongitudPermitida() {
        CrearUsuarioDto dto = new CrearUsuarioDto("e".repeat(175) + "@alpes.com", PASSWORD, RolUsuario.EDITOR);

        assertThat(camposConError(dto)).contains("correo");
    }

    @Test
    void laContrasenaEsObligatoria() {
        CrearUsuarioDto dto = new CrearUsuarioDto(CORREO, "", RolUsuario.EDITOR);

        assertThat(camposConError(dto)).contains("password");
    }

    @Test
    void laContrasenaDebeTenerLaMismaLongitudMinimaQueEnElRegistroDeEmpresa() {
        CrearUsuarioDto dto = new CrearUsuarioDto(CORREO, "corta12", RolUsuario.EDITOR);

        assertThat(camposConError(dto)).contains("password");
    }

    @Test
    void laContrasenaAceptaLaLongitudMinimaExacta() {
        CrearUsuarioDto dto = new CrearUsuarioDto(CORREO, "Clave123", RolUsuario.EDITOR);

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void laContrasenaNoPuedeSuperarLaLongitudPermitida() {
        CrearUsuarioDto dto = new CrearUsuarioDto(CORREO, "C".repeat(101), RolUsuario.EDITOR);

        assertThat(camposConError(dto)).contains("password");
    }

    @Test
    void elRolEsObligatorio() {
        CrearUsuarioDto dto = new CrearUsuarioDto(CORREO, PASSWORD, null);

        assertThat(camposConError(dto)).contains("rol");
    }

    @Test
    void losTresRolesDeAccesoSonAceptados() {
        for (RolUsuario rol : RolUsuario.values()) {
            assertThat(validator.validate(new CrearUsuarioDto(CORREO, PASSWORD, rol))).isEmpty();
        }
    }

    @Test
    void unFormularioVacioSenalaLosTresCamposObligatorios() {
        CrearUsuarioDto dto = new CrearUsuarioDto("", "", null);

        assertThat(camposConError(dto)).containsExactlyInAnyOrder("correo", "password", "rol");
    }
}
