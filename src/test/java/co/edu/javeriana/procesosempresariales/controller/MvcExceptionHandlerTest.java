package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.ModelAndView;

import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
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

    @Test
    void unNodoDeFlujoNoValidoSeMuestraEnLaPaginaDeProblema() {
        ModelAndView vista = manejador.conexionNoValida(
                new NodoFlujoNoValidoException("El nodo indicado no existe en este proceso"));

        assertThat(vista.getViewName()).isEqualTo("error/problema");
        assertThat(vista.getModel()).containsEntry("detalle", "El nodo indicado no existe en este proceso");
        assertThat(vista.getModel().get("titulo")).isNotNull();
    }

    @Test
    void unArcoDuplicadoSeMuestraEnLaPaginaDeProblema() {
        ModelAndView vista = manejador.conexionNoValida(
                new ArcoDuplicadoException("Ya existe un arco activo entre ese origen y ese destino"));

        assertThat(vista.getModel())
                .containsEntry("detalle", "Ya existe un arco activo entre ese origen y ese destino");
    }

    @Test
    void unaCondicionNoValidaSeMuestraEnLaPaginaDeProblema() {
        ModelAndView vista = manejador.conexionNoValida(
                new CondicionArcoNoValidaException("Un arco que sale de un gateway paralelo no lleva condición"));

        assertThat(vista.getModel())
                .containsEntry("detalle", "Un arco que sale de un gateway paralelo no lleva condición");
    }

    @Test
    void unModeloDeProcesoIncompletoSeMuestraEnLaPaginaDeProblema() {
        ModelAndView vista = manejador.modeloNoValido(new ModeloDeProcesoNoValidoException(
                "El proceso no puede salir de borrador: Gateway EXCLUSIVO #12 se usa como divergencia con una"
                        + " sola salida: necesita al menos dos"));

        assertThat(vista.getViewName()).isEqualTo("error/problema");
        assertThat(vista.getModel().get("detalle").toString()).contains("necesita al menos dos");
        assertThat(vista.getModel().get("titulo")).isNotNull();
    }
}
