package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EnvioExterno;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.EnvioExternoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.EnvioExternoRepository;

@Service
public class EnvioExternoService {

    static final String ENVIO_NO_EXISTE = "El envío externo no existe en este proceso";
    static final String ENVIO_ELIMINADO = "El envío externo ya fue eliminado";
    static final String SIN_PERMISO_ESCRITURA =
            "Solo un administrador o editor puede crear o modificar envíos externos";
    static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar envíos externos";
    static final String DESTINO_NO_EXTERNO = ": un envío externo solo se dirige a un pool EXTERNO que represente"
            + " al sistema de terceros";
    static final String DESTINO_SIN_CAJA_NEGRA = "' no es una caja negra: el sistema externo se modela sin"
            + " actividades internas";

    private EnvioExternoRepository envioExternoRepository;
    private AccesoProcesoService accesoProcesoService;
    private PoolService poolService;
    private EventoService eventoService;

    @Autowired
    public EnvioExternoService(EnvioExternoRepository envioExternoRepository,
            AccesoProcesoService accesoProcesoService, PoolService poolService, EventoService eventoService) {
        this.envioExternoRepository = envioExternoRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.poolService = poolService;
        this.eventoService = eventoService;
    }

    @Transactional
    public EnvioExternoRespuestaDto crear(Long procesoId, CrearEnvioExternoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Pool destino = sistemaExterno(proceso, dto.getPoolDestinoId());
        eventoService.exigirPoolsDistintos(dto.getPoolOrigenId(), destino);
        Pool origen = poolService.poolParaNodo(proceso, dto.getPoolOrigenId());

        EnvioExterno envio = new EnvioExterno();
        envio.setProceso(proceso);
        envio.setPool(origen);
        envio.setPoolDestino(destino);
        envio.setNombreMensaje(dto.getNombreMensaje().trim());
        envio.setTipoDestino(dto.getTipoDestino());
        envio.setDatosEnviados(dto.getDatosEnviados().trim());
        envio.setMomentoProceso(dto.getMomentoProceso().trim());
        envio.setComportamientoFallo(dto.getComportamientoFallo());
        envio.setClaveCorrelacion(textoONulo(dto.getClaveCorrelacion()));
        envio.setPosicionX(dto.getPosicionX());
        envio.setPosicionY(dto.getPosicionY());
        envio.setActivo(true);

        EnvioExterno guardado = envioExternoRepository.save(envio);
        eventoService.registrarCreacion(proceso, usuario, guardado, "pool '" + origen.getNombre() + "' -> pool '"
                + destino.getNombre() + "', " + guardado.getTipoDestino() + ", ante fallo "
                + guardado.getComportamientoFallo());
        return toDto(guardado);
    }

    @Transactional(readOnly = true)
    public EnvioExternoRespuestaDto obtener(Long procesoId, Long envioId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return toDto(envioActivoDelProceso(envioId, proceso));
    }

    @Transactional(readOnly = true)
    public List<EnvioExternoRespuestaDto> listar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return envioExternoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public EnvioExternoRespuestaDto editar(Long procesoId, Long envioId, EditarEnvioExternoDto dto,
            String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        EnvioExterno envio = envioActivoDelProceso(envioId, proceso);
        Pool destino = sistemaExterno(proceso, dto.getPoolDestinoId());
        eventoService.exigirPoolsDistintos(envio.getPool().getId(), destino);

        String nombreNuevo = dto.getNombreMensaje().trim();
        String datosNuevos = dto.getDatosEnviados().trim();
        String momentoNuevo = dto.getMomentoProceso().trim();
        String claveNueva = textoONulo(dto.getClaveCorrelacion());
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", envio.getNombreMensaje(), nombreNuevo);
        if (!envio.getPoolDestino().getId().equals(destino.getId())) {
            cambios.add("pool destino: '" + envio.getPoolDestino().getNombre() + "' -> '" + destino.getNombre() + "'");
        }
        agregarCambio(cambios, "tipo de destino", envio.getTipoDestino(), dto.getTipoDestino());
        agregarCambio(cambios, "datos enviados", envio.getDatosEnviados(), datosNuevos);
        agregarCambio(cambios, "momento del proceso", envio.getMomentoProceso(), momentoNuevo);
        agregarCambio(cambios, "comportamiento ante fallo", envio.getComportamientoFallo(),
                dto.getComportamientoFallo());
        agregarCambio(cambios, "clave de correlacion", envio.getClaveCorrelacion(), claveNueva);
        if (cambios.isEmpty()) {
            return toDto(envio);
        }

        String nombreAnterior = envio.getNombreMensaje();
        envio.setNombreMensaje(nombreNuevo);
        envio.setPoolDestino(destino);
        envio.setTipoDestino(dto.getTipoDestino());
        envio.setDatosEnviados(datosNuevos);
        envio.setMomentoProceso(momentoNuevo);
        envio.setComportamientoFallo(dto.getComportamientoFallo());
        envio.setClaveCorrelacion(claveNueva);
        envioExternoRepository.save(envio);
        eventoService.registrarEdicion(proceso, usuario, envio, nombreAnterior, cambios);
        return toDto(envio);
    }

    @Transactional(readOnly = true)
    public EnvioExternoRespuestaDto obtenerParaEliminar(Long procesoId, Long envioId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        EnvioExterno envio = envioActivoDelProceso(envioId, proceso);

        EnvioExternoRespuestaDto respuesta = toDto(envio);
        respuesta.setAdvertencias(eventoService.advertenciasSiSeElimina(proceso, envio));
        return respuesta;
    }

    @Transactional
    public EnvioExternoRespuestaDto eliminar(Long procesoId, Long envioId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        EnvioExterno envio = envioActivoDelProceso(envioId, proceso);

        EliminacionEvento eliminacion = eventoService.eliminar(proceso, usuario, envio);
        EnvioExternoRespuestaDto respuesta = toDto(envio);
        respuesta.setArcosDesactivados(eliminacion.arcosDesactivados());
        respuesta.setAdvertencias(eliminacion.advertencias());
        return respuesta;
    }

    private Pool sistemaExterno(Proceso proceso, Long poolId) {
        Pool destino = poolService.poolDestinoDeMensaje(proceso, poolId);
        if (destino.getTipo() != TipoPool.EXTERNO) {
            throw new EnvioExternoNoValidoException("El pool '" + destino.getNombre() + "' es de tipo "
                    + destino.getTipo() + DESTINO_NO_EXTERNO);
        }
        if (!destino.isCajaNegra()) {
            throw new EnvioExternoNoValidoException("El pool externo '" + destino.getNombre() + DESTINO_SIN_CAJA_NEGRA);
        }
        return destino;
    }

    private EnvioExterno envioActivoDelProceso(Long envioId, Proceso proceso) {
        EnvioExterno envio = envioExternoRepository.findByIdAndProcesoId(envioId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ENVIO_NO_EXISTE));
        if (!envio.isActivo()) {
            throw new RecursoNoEncontradoException(ENVIO_ELIMINADO);
        }
        return envio;
    }

    private void agregarCambio(List<String> cambios, String campo, Object anterior, Object nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": '" + textoVisible(anterior) + "' -> '" + textoVisible(nuevo) + "'");
        }
    }

    private String textoVisible(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private String textoONulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private EnvioExternoRespuestaDto toDto(EnvioExterno envio) {
        EnvioExternoRespuestaDto respuesta = new EnvioExternoRespuestaDto();
        respuesta.setId(envio.getId());
        respuesta.setProcesoId(envio.getProceso().getId());
        respuesta.setNombreMensaje(envio.getNombreMensaje());
        respuesta.setEtiqueta(envio.etiqueta());
        respuesta.setPoolOrigenId(envio.getPool().getId());
        respuesta.setPoolOrigenNombre(envio.getPool().getNombre());
        respuesta.setPoolDestinoId(envio.getPoolDestino().getId());
        respuesta.setPoolDestinoNombre(envio.getPoolDestino().getNombre());
        respuesta.setTipoDestino(envio.getTipoDestino());
        respuesta.setDatosEnviados(envio.getDatosEnviados());
        respuesta.setMomentoProceso(envio.getMomentoProceso());
        respuesta.setComportamientoFallo(envio.getComportamientoFallo());
        respuesta.setClaveCorrelacion(envio.getClaveCorrelacion());
        respuesta.setPosicionX(envio.getPosicionX());
        respuesta.setPosicionY(envio.getPosicionY());
        respuesta.setActivo(envio.isActivo());
        return respuesta;
    }
}
