package co.edu.javeriana.procesosempresariales.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioNoAutorizadoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NombreProcesoDuplicadoException.class)
    ResponseEntity<Map<String, String>> duplicado(NombreProcesoDuplicadoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "PROCESO_NOMBRE_DUPLICADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(NitEmpresaDuplicadoException.class)
    ResponseEntity<Map<String, String>> nitDuplicado(NitEmpresaDuplicadoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "EMPRESA_NIT_DUPLICADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(CorreoAdministradorEnUsoException.class)
    ResponseEntity<Map<String, String>> correoEnUso(CorreoAdministradorEnUsoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "USUARIO_CORREO_EN_USO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(NombreActividadDuplicadoException.class)
    ResponseEntity<Map<String, String>> actividadDuplicada(NombreActividadDuplicadoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "ACTIVIDAD_NOMBRE_DUPLICADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(LaneNoValidaException.class)
    ResponseEntity<Map<String, String>> laneNoValida(LaneNoValidaException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("codigo", "LANE_NO_VALIDA", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(ArcoDuplicadoException.class)
    ResponseEntity<Map<String, String>> arcoDuplicado(ArcoDuplicadoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "ARCO_DUPLICADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(NodoFlujoNoValidoException.class)
    ResponseEntity<Map<String, String>> nodoNoValido(NodoFlujoNoValidoException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("codigo", "NODO_NO_VALIDO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(CondicionArcoNoValidaException.class)
    ResponseEntity<Map<String, String>> condicionNoValida(CondicionArcoNoValidaException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("codigo", "CONDICION_NO_VALIDA", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(ModeloDeProcesoNoValidoException.class)
    ResponseEntity<Map<String, String>> modeloNoValido(ModeloDeProcesoNoValidoException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("codigo", "MODELO_NO_VALIDO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("codigo", "RECURSO_NO_ENCONTRADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(UsuarioNoAutorizadoException.class)
    ResponseEntity<Map<String, String>> noAutorizado(UsuarioNoAutorizadoException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("codigo", "USUARIO_NO_AUTORIZADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(UsuarioSinPermisoException.class)
    ResponseEntity<Map<String, String>> sinPermiso(UsuarioSinPermisoException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("codigo", "USUARIO_SIN_PERMISO", "mensaje", exception.getMessage()));
    }
}



