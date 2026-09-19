package co.edu.javeriana.procesosempresariales.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

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

    private ModelAndView vista(String titulo, String detalle) {
        ModelAndView modelAndView = new ModelAndView(VISTA_PROBLEMA);
        modelAndView.addObject("titulo", titulo);
        modelAndView.addObject("detalle", detalle);
        return modelAndView;
    }
}
