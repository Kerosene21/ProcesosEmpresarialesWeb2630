package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.ModelAndView;

import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;

class MvcExceptionHandlerTest {

    private final MvcExceptionHandler manejador = new MvcExceptionHandler();

    @Test
    void unRecursoInexistenteSeMuestraEnLaPaginaDeProblemaYNoEnJson() {
        ModelAndView vista = manejador.noEncontrado(new RecursoNoEncontradoException("La empresa no existe"));

        assertThat(vista.getViewName()).isEqualTo("error/problema");
        assertThat(vista.getModel()).containsEntry("detalle", "La empresa no existe");
        assertThat(vista.getModel().get("titulo")).isNotNull();
    }

    @Test
    void laFaltaDePermisosSeMuestraEnLaPaginaDeProblemaYNoEnJson() {
        ModelAndView vista = manejador.sinPermiso(new UsuarioSinPermisoException("No pertenece a tu empresa"));

        assertThat(vista.getViewName()).isEqualTo("error/problema");
        assertThat(vista.getModel()).containsEntry("detalle", "No pertenece a tu empresa");
        assertThat(vista.getModel().get("titulo")).isNotNull();
    }
}
