package co.edu.javeriana.procesosempresariales.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.FlujoEntrePoolsException;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.PoolNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;

@ControllerAdvice
@Order(20)
public class MvcExceptionHandler {

    private static final String VISTA_PROBLEMA = "error/problema";

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ModelAndView noEncontrado(RecursoNoEncontradoException exception) {
        return vista("No encontramos lo que buscabas", exception.getMessage());
    }

    @ExceptionHandler(UsuarioSinPermisoException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ModelAndView sinPermiso(UsuarioSinPermisoException exception) {
        return vista("No tienes permiso para ver esta informacion", exception.getMessage());
    }

    @ExceptionHandler(ModeloDeProcesoNoValidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ModelAndView modeloNoValido(ModeloDeProcesoNoValidoException exception) {
        return vista("El modelo del proceso todavia no esta completo", exception.getMessage());
    }

    @ExceptionHandler({ NodoFlujoNoValidoException.class, ArcoDuplicadoException.class,
            CondicionArcoNoValidaException.class, FlujoEntrePoolsException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ModelAndView conexionNoValida(RuntimeException exception) {
        return vista("La conexion del diagrama no es valida", exception.getMessage());
    }

    @ExceptionHandler({ PoolNoValidoException.class, PoolCajaNegraException.class })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ModelAndView poolNoValido(RuntimeException exception) {
        return vista("El pool indicado no admite ese elemento", exception.getMessage());
    }

    private ModelAndView vista(String titulo, String detalle) {
        ModelAndView modelAndView = new ModelAndView(VISTA_PROBLEMA);
        modelAndView.addObject("titulo", titulo);
        modelAndView.addObject("detalle", detalle);
        return modelAndView;
    }
}
