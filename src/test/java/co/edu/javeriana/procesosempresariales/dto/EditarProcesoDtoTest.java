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

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;

class EditarProcesoDtoTest {

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

    private Set<String> camposConError(EditarProcesoDto dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private EditarProcesoDto formularioValido() {
        return new EditarProcesoDto("Ventas Corporativas", "Descripcion actualizada", "Operaciones",
                EstadoProceso.BORRADOR);
    }

    private String texto(int longitud) {
        return "a".repeat(longitud);
    }

    @Test
    void unFormularioCompletoNoProduceErrores() {
        assertThat(camposConError(formularioValido())).isEmpty();
    }

    @Test
    void elEstadoPublicadoTambienEsValido() {
        EditarProcesoDto dto = formularioValido();
        dto.setEstado(EstadoProceso.PUBLICADO);

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elNombreEsObligatorio() {
        EditarProcesoDto dto = formularioValido();
        dto.setNombre("   ");

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void laDescripcionEsObligatoria() {
        EditarProcesoDto dto = formularioValido();
        dto.setDescripcion("   ");

        assertThat(camposConError(dto)).containsExactly("descripcion");
    }

    @Test
    void laCategoriaEsObligatoria() {
        EditarProcesoDto dto = formularioValido();
        dto.setCategoria("   ");

        assertThat(camposConError(dto)).containsExactly("categoria");
    }

    @Test
    void elEstadoEsObligatorio() {
        EditarProcesoDto dto = formularioValido();
        dto.setEstado(null);

        assertThat(camposConError(dto)).containsExactly("estado");
    }

    @Test
    void elNombreAdmiteHastaCientoCincuentaCaracteres() {
        EditarProcesoDto dto = formularioValido();
        dto.setNombre(texto(150));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void elNombreRechazaMasDeCientoCincuentaCaracteres() {
        EditarProcesoDto dto = formularioValido();
        dto.setNombre(texto(151));

        assertThat(camposConError(dto)).containsExactly("nombre");
    }

    @Test
    void laCategoriaAdmiteHastaCienCaracteres() {
        EditarProcesoDto dto = formularioValido();
        dto.setCategoria(texto(100));

        assertThat(camposConError(dto)).isEmpty();
    }

    @Test
    void laCategoriaRechazaMasDeCienCaracteres() {
        EditarProcesoDto dto = formularioValido();
        dto.setCategoria(texto(101));

        assertThat(camposConError(dto)).containsExactly("categoria");
    }

    @Test
    void unFormularioVacioReportaLosCuatroCamposObligatorios() {
        assertThat(camposConError(new EditarProcesoDto()))
                .containsExactlyInAnyOrder("nombre", "descripcion", "categoria", "estado");
    }
}
