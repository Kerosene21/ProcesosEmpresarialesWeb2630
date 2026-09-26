package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import co.edu.javeriana.procesosempresariales.exception.ComparticionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.FlujoEntrePoolsException;
import co.edu.javeriana.procesosempresariales.exception.LaneConActividadesException;
import co.edu.javeriana.procesosempresariales.exception.LaneDuplicadaException;
import co.edu.javeriana.procesosempresariales.exception.LaneNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
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

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler manejador = new ApiExceptionHandler();

    private void verificar(ResponseEntity<Map<String, String>> respuesta, HttpStatus estado, String codigo,
            String mensaje) {
        assertThat(respuesta.getStatusCode()).isEqualTo(estado);
        assertThat(respuesta.getBody()).containsEntry("codigo", codigo).containsEntry("mensaje", mensaje);
    }

    @Test
    void unNombreDeProcesoDuplicadoSeReportaComoConflicto() {
        verificar(manejador.duplicado(new NombreProcesoDuplicadoException("Nombre ya usado")),
                HttpStatus.CONFLICT, "PROCESO_NOMBRE_DUPLICADO", "Nombre ya usado");
    }

    @Test
    void unNitDeEmpresaDuplicadoSeReportaComoConflicto() {
        verificar(manejador.nitDuplicado(new NitEmpresaDuplicadoException("NIT ya registrado")),
                HttpStatus.CONFLICT, "EMPRESA_NIT_DUPLICADO", "NIT ya registrado");
    }

    @Test
    void unCorreoDeAdministradorEnUsoSeReportaComoConflicto() {
        verificar(manejador.correoEnUso(new CorreoAdministradorEnUsoException("Correo en uso")),
                HttpStatus.CONFLICT, "USUARIO_CORREO_EN_USO", "Correo en uso");
    }

    @Test
    void unRecursoInexistenteSeReportaComoNoEncontrado() {
        verificar(manejador.noEncontrado(new RecursoNoEncontradoException("El proceso no existe")),
                HttpStatus.NOT_FOUND, "RECURSO_NO_ENCONTRADO", "El proceso no existe");
    }

    @Test
    void laFaltaDeAutenticacionSeReportaComoNoAutorizado() {
        verificar(manejador.noAutorizado(new UsuarioNoAutorizadoException("Se requiere autenticacion")),
                HttpStatus.UNAUTHORIZED, "USUARIO_NO_AUTORIZADO", "Se requiere autenticacion");
    }

    @Test
    void laFaltaDePermisosSeReportaComoProhibido() {
        verificar(manejador.sinPermiso(new UsuarioSinPermisoException("Sin permiso para modificar")),
                HttpStatus.FORBIDDEN, "USUARIO_SIN_PERMISO", "Sin permiso para modificar");
    }

    @Test
    void unNombreDeActividadDuplicadoSeReportaComoConflicto() {
        verificar(manejador.actividadDuplicada(new NombreActividadDuplicadoException("Nombre ya usado")),
                HttpStatus.CONFLICT, "ACTIVIDAD_NOMBRE_DUPLICADO", "Nombre ya usado");
    }

    @Test
    void unaLaneQueNoPerteneceAlProcesoSeReportaComoPeticionIncorrecta() {
        verificar(manejador.laneNoValida(new LaneNoValidaException("La lane no pertenece a este proceso")),
                HttpStatus.BAD_REQUEST, "LANE_NO_VALIDA", "La lane no pertenece a este proceso");
    }

    @Test
    void unModeloDeProcesoIncompletoSeReportaComoPeticionInvalida() {
        verificar(manejador.modeloNoValido(new ModeloDeProcesoNoValidoException(
                "El proceso no puede salir de borrador: Gateway EXCLUSIVO #12 se usa como divergencia con una"
                        + " sola salida: necesita al menos dos")),
                HttpStatus.BAD_REQUEST, "MODELO_NO_VALIDO",
                "El proceso no puede salir de borrador: Gateway EXCLUSIVO #12 se usa como divergencia con una"
                        + " sola salida: necesita al menos dos");
    }

    @Test
    void unNombreDeRolDeProcesoDuplicadoSeReportaComoConflicto() {
        verificar(manejador.rolDuplicado(new NombreRolProcesoDuplicadoException("Rol ya registrado")),
                HttpStatus.CONFLICT, "ROL_PROCESO_NOMBRE_DUPLICADO", "Rol ya registrado");
    }

    @Test
    void unRolDeProcesoEnUsoSeReportaComoConflictoConLosProcesosAfectados() {
        ResponseEntity<Map<String, Object>> respuesta = manejador.rolEnUso(
                new RolProcesoEnUsoException("El rol se usa en Ventas, Compras", List.of("Ventas", "Compras")));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody())
                .containsEntry("codigo", "ROL_PROCESO_EN_USO")
                .containsEntry("mensaje", "El rol se usa en Ventas, Compras")
                .containsEntry("procesos", List.of("Ventas", "Compras"));
    }
    @Test
    void unPoolNoValidoSeReportaComoPeticionIncorrecta() {
        verificar(manejador.poolNoValido(new PoolNoValidoException("El pool indicado no existe")),
                HttpStatus.BAD_REQUEST, "POOL_NO_VALIDO", "El pool indicado no existe");
    }

    @Test
    void unPoolDeCajaNegraSeReportaComoConflicto() {
        verificar(manejador.poolCajaNegra(new PoolCajaNegraException("El pool es una caja negra")),
                HttpStatus.CONFLICT, "POOL_CAJA_NEGRA", "El pool es una caja negra");
    }

    @Test
    void unPoolConContenidoSeReportaComoConflicto() {
        verificar(manejador.poolConContenido(new PoolConContenidoException("El pool tiene lanes")),
                HttpStatus.CONFLICT, "POOL_CON_CONTENIDO", "El pool tiene lanes");
    }

    @Test
    void unaLaneConActividadesSeReportaComoConflicto() {
        verificar(manejador.laneConActividades(new LaneConActividadesException("La lane tiene actividades")),
                HttpStatus.CONFLICT, "LANE_CON_ACTIVIDADES", "La lane tiene actividades");
    }

    @Test
    void unaLaneDuplicadaSeReportaComoConflicto() {
        verificar(manejador.laneDuplicada(new LaneDuplicadaException("El rol ya tiene lane")),
                HttpStatus.CONFLICT, "LANE_DUPLICADA", "El rol ya tiene lane");
    }

    @Test
    void unFlujoDeSecuenciaEntrePoolsSeReportaComoPeticionIncorrecta() {
        verificar(manejador.flujoEntrePools(new FlujoEntrePoolsException("No cruza pools")),
                HttpStatus.BAD_REQUEST, "SECUENCIA_ENTRE_POOLS", "No cruza pools");
    }

    @Test
    void unaComparticionNoValidaSeReportaComoPeticionIncorrecta() {
        verificar(manejador.comparticionNoValida(new ComparticionNoValidaException("No se comparte consigo")),
                HttpStatus.BAD_REQUEST, "COMPARTICION_NO_VALIDA", "No se comparte consigo");
    }

    @Test
    void unProcesoYaCompartidoSeReportaComoConflicto() {
        verificar(manejador.procesoYaCompartido(new ProcesoYaCompartidoException("Ya compartido")),
                HttpStatus.CONFLICT, "PROCESO_YA_COMPARTIDO", "Ya compartido");
    }

    @Test
    void unPermisoDeEstructuraNoValidoSeReportaComoPeticionIncorrecta() {
        verificar(manejador.permisoEstructuraNoValido(new PermisoEstructuraNoValidoException("Rol fijo")),
                HttpStatus.BAD_REQUEST, "PERMISO_ESTRUCTURA_NO_VALIDO", "Rol fijo");
    }
}
