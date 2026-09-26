package co.edu.javeriana.procesosempresariales.controller;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.ComparticionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.FlujoEntrePoolsException;
import co.edu.javeriana.procesosempresariales.exception.LaneConActividadesException;
import co.edu.javeriana.procesosempresariales.exception.LaneDuplicadaException;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NombreActividadDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.NombreRolProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.PermisoEstructuraNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.PoolCajaNegraException;
import co.edu.javeriana.procesosempresariales.exception.PoolConContenidoException;
import co.edu.javeriana.procesosempresariales.exception.PoolNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.ProcesoYaCompartidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.RolProcesoEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioNoAutorizadoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;

@RestControllerAdvice(annotations = RestController.class)
@Order(10)
public class ApiExceptionHandler {

    @ExceptionHandler(PoolNoValidoException.class)
    ResponseEntity<Map<String, String>> poolNoValido(PoolNoValidoException exception) {
        return respuesta(HttpStatus.BAD_REQUEST, "POOL_NO_VALIDO", exception);
    }

    @ExceptionHandler(PoolCajaNegraException.class)
    ResponseEntity<Map<String, String>> poolCajaNegra(PoolCajaNegraException exception) {
        return respuesta(HttpStatus.CONFLICT, "POOL_CAJA_NEGRA", exception);
    }

    @ExceptionHandler(PoolConContenidoException.class)
    ResponseEntity<Map<String, String>> poolConContenido(PoolConContenidoException exception) {
        return respuesta(HttpStatus.CONFLICT, "POOL_CON_CONTENIDO", exception);
    }

    @ExceptionHandler(LaneConActividadesException.class)
    ResponseEntity<Map<String, String>> laneConActividades(LaneConActividadesException exception) {
        return respuesta(HttpStatus.CONFLICT, "LANE_CON_ACTIVIDADES", exception);
    }

    @ExceptionHandler(LaneDuplicadaException.class)
    ResponseEntity<Map<String, String>> laneDuplicada(LaneDuplicadaException exception) {
        return respuesta(HttpStatus.CONFLICT, "LANE_DUPLICADA", exception);
    }

    @ExceptionHandler(FlujoEntrePoolsException.class)
    ResponseEntity<Map<String, String>> flujoEntrePools(FlujoEntrePoolsException exception) {
        return respuesta(HttpStatus.BAD_REQUEST, "SECUENCIA_ENTRE_POOLS", exception);
    }

    @ExceptionHandler(ComparticionNoValidaException.class)
    ResponseEntity<Map<String, String>> comparticionNoValida(ComparticionNoValidaException exception) {
        return respuesta(HttpStatus.BAD_REQUEST, "COMPARTICION_NO_VALIDA", exception);
    }

    @ExceptionHandler(ProcesoYaCompartidoException.class)
    ResponseEntity<Map<String, String>> procesoYaCompartido(ProcesoYaCompartidoException exception) {
        return respuesta(HttpStatus.CONFLICT, "PROCESO_YA_COMPARTIDO", exception);
    }

    @ExceptionHandler(PermisoEstructuraNoValidoException.class)
    ResponseEntity<Map<String, String>> permisoEstructuraNoValido(PermisoEstructuraNoValidoException exception) {
        return respuesta(HttpStatus.BAD_REQUEST, "PERMISO_ESTRUCTURA_NO_VALIDO", exception);
    }

    private ResponseEntity<Map<String, String>> respuesta(HttpStatus estado, String codigo,
            RuntimeException exception) {
        return ResponseEntity.status(estado).body(Map.of("codigo", codigo, "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(NombreRolProcesoDuplicadoException.class)
    ResponseEntity<Map<String, String>> rolDuplicado(NombreRolProcesoDuplicadoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "ROL_PROCESO_NOMBRE_DUPLICADO", "mensaje", exception.getMessage()));
    }

    @ExceptionHandler(RolProcesoEnUsoException.class)
    ResponseEntity<Map<String, Object>> rolEnUso(RolProcesoEnUsoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("codigo", "ROL_PROCESO_EN_USO", "mensaje", exception.getMessage(),
                        "procesos", exception.getProcesos()));
    }

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