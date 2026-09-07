package co.edu.javeriana.procesosempresariales.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RegistroEmpresaDtoTest {

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

    private Set<String> camposConError(RegistroEmpresaDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("Alpes Logistica", "900123456-7", "contacto@alpes.com");

        assertThat(validator.validate(dto)).isEmpty();
    }

    @Test
    void elNombreEsObligatorio() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("   ", "900123456-7", "contacto@alpes.com");

        assertThat(camposConError(dto)).contains("nombre");
    }

    @Test
    void elNitEsObligatorio() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("Alpes Logistica", "", "contacto@alpes.com");

        assertThat(camposConError(dto)).contains("nit");
    }

    @Test
    void elCorreoDeContactoEsObligatorio() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("Alpes Logistica", "900123456-7", "");

        assertThat(camposConError(dto)).contains("correoContacto");
    }

    @Test
    void elCorreoDeContactoDebeTenerFormatoDeEmail() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("Alpes Logistica", "900123456-7", "alpes-punto-com");

        assertThat(camposConError(dto)).contains("correoContacto");
    }

    @Test
    void elNombreNoPuedeSuperarLaLongitudPermitida() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("A".repeat(151), "900123456-7", "contacto@alpes.com");

        assertThat(camposConError(dto)).contains("nombre");
    }

    @Test
    void elNitNoPuedeSuperarLaLongitudPermitida() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("Alpes Logistica", "9".repeat(21), "contacto@alpes.com");

        assertThat(camposConError(dto)).contains("nit");
    }

    @Test
    void unFormularioVacioSenalaLosTresCamposObligatorios() {
        RegistroEmpresaDto dto = new RegistroEmpresaDto("", "", "");

        assertThat(camposConError(dto)).containsExactlyInAnyOrder("nombre", "nit", "correoContacto");
    }
}
