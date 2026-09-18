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

class CrearProcesoDtoTest {

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

    private Set<String> camposConError(CrearProcesoDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private CrearProcesoDto formularioValido() {
        return new CrearProcesoDto("Ventas", "Proceso comercial de la compania", "Comercial");
    }

    private String texto(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        assertThat(camposConError(formularioValido())).isEmpty();
    }

    @Test
    void elNombreEsObligatorio() {
        CrearProcesoDto dto = formularioValido();
        dto.setNombre("   ");

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void elNombreNoPuedeSerNulo() {
        CrearProcesoDto dto = formularioValido();
        dto.setNombre(null);

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void laDescripcionEsObligatoria() {
        CrearProcesoDto dto = formularioValido();
        dto.setDescripcion("   ");

        assertThat(camposConError(dto)).containsExactly("descripcion");
    }

    @Test
    void laCategoriaEsObligatoria() {
        CrearProcesoDto dto = formularioValido();
        dto.setCategoria("   ");

        assertThat(camposConError(dto)).containsExactly("categoria");
    }

    @Test
    void elNombreAdmiteHastaCientoCincuentaCaracteres() {
        CrearProcesoDto dto = formularioValido();
        dto.setNombre(texto(150));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elNombreRechazaMasDeCientoCincuentaCaracteres() {
        CrearProcesoDto dto = formularioValido();
        dto.setNombre(texto(151));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void laCategoriaAdmiteHastaCienCaracteres() {
        CrearProcesoDto dto = formularioValido();
        dto.setCategoria(texto(100));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void laCategoriaRechazaMasDeCienCaracteres() {
        CrearProcesoDto dto = formularioValido();
        dto.setCategoria(texto(101));

        assertThat(camposConError(dto)).containsExactly("categoria");
    }

    @Test
    void laDescripcionNoTieneLimiteDeLongitud() {
        CrearProcesoDto dto = formularioValido();
        dto.setDescripcion(texto(5000));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void unFormularioVacioReportaLosTresCamposObligatorios() {
        assertThat(camposConError(new CrearProcesoDto()))
                .containsExactlyInAnyOrder("nombre", "descripcion", "categoria");
    }
}
