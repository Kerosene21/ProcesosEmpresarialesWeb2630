package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.MessageThrow;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.MessageThrowRepository;

@Service
public class MessageThrowService {

    static final String THROW_NO_EXISTE = "El Message Throw no existe en este proceso";
    static final String THROW_ELIMINADO = "El Message Throw ya fue eliminado";
    static final String SIN_PERMISO_ESCRITURA = "Solo un administrador o editor puede crear o modificar Message Throw";
    static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar Message Throw";

    private MessageThrowRepository messageThrowRepository;
    private AccesoProcesoService accesoProcesoService;
    private PoolService poolService;
    private EventoService eventoService;
    private CorrelacionMensajeService correlacionMensajeService;

    @Autowired
    public MessageThrowService(MessageThrowRepository messageThrowRepository,
            AccesoProcesoService accesoProcesoService, PoolService poolService, EventoService eventoService,
            CorrelacionMensajeService correlacionMensajeService) {
        this.messageThrowRepository = messageThrowRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.poolService = poolService;
        this.eventoService = eventoService;
        this.correlacionMensajeService = correlacionMensajeService;
    }

    @Transactional
    public MessageThrowRespuestaDto crear(Long procesoId, CrearMessageThrowDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Pool destino = poolService.poolDestinoDeMensaje(proceso, dto.getPoolDestinoId());
        eventoService.exigirPoolsDistintos(dto.getPoolOrigenId(), destino);
        Pool origen = poolService.poolParaNodo(proceso, dto.getPoolOrigenId());

        MessageThrow messageThrow = new MessageThrow();
        messageThrow.setProceso(proceso);
        messageThrow.setPool(origen);
        messageThrow.setPoolDestino(destino);
        messageThrow.setNombreMensaje(dto.getNombreMensaje().trim());
        messageThrow.setContenido(dto.getContenido().trim());
        messageThrow.setClaveCorrelacion(textoONulo(dto.getClaveCorrelacion()));
        messageThrow.setComportamientoSinCaso(dto.getComportamientoSinCaso());
        messageThrow.setPosicionX(dto.getPosicionX());
        messageThrow.setPosicionY(dto.getPosicionY());
        messageThrow.setActivo(true);

        MessageThrow guardado = messageThrowRepository.save(messageThrow);
        eventoService.registrarCreacion(proceso, usuario, guardado,
                "pool '" + origen.getNombre() + "' -> pool '" + destino.getNombre() + "'");
        return conCorrelacion(guardado);
    }

    @Transactional(readOnly = true)
    public MessageThrowRespuestaDto obtener(Long procesoId, Long throwId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return conCorrelacion(throwActivoDelProceso(throwId, proceso));
    }

    @Transactional(readOnly = true)
    public List<MessageThrowRespuestaDto> listar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId()).stream()
                .map(this::conCorrelacion)
                .toList();
    }

    @Transactional
    public MessageThrowRespuestaDto editar(Long procesoId, Long throwId, EditarMessageThrowDto dto,
            String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageThrow messageThrow = throwActivoDelProceso(throwId, proceso);
        Pool destino = poolService.poolDestinoDeMensaje(proceso, dto.getPoolDestinoId());
        eventoService.exigirPoolsDistintos(messageThrow.getPool().getId(), destino);

        String nombreNuevo = dto.getNombreMensaje().trim();
        String contenidoNuevo = dto.getContenido().trim();
        String claveNueva = textoONulo(dto.getClaveCorrelacion());
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", messageThrow.getNombreMensaje(), nombreNuevo);
        agregarCambio(cambios, "contenido", messageThrow.getContenido(), contenidoNuevo);
        if (!messageThrow.getPoolDestino().getId().equals(destino.getId())) {
            cambios.add("pool destino: '" + messageThrow.getPoolDestino().getNombre() + "' -> '" + destino.getNombre()
                    + "'");
        }
        agregarCambio(cambios, "clave de correlacion", messageThrow.getClaveCorrelacion(), claveNueva);
        agregarCambio(cambios, "comportamiento sin caso", messageThrow.getComportamientoSinCaso(),
                dto.getComportamientoSinCaso());
        if (cambios.isEmpty()) {
            return conCorrelacion(messageThrow);
        }

        String nombreAnterior = messageThrow.getNombreMensaje();
        messageThrow.setNombreMensaje(nombreNuevo);
        messageThrow.setContenido(contenidoNuevo);
        messageThrow.setPoolDestino(destino);
        messageThrow.setClaveCorrelacion(claveNueva);
        messageThrow.setComportamientoSinCaso(dto.getComportamientoSinCaso());
        messageThrowRepository.save(messageThrow);
        eventoService.registrarEdicion(proceso, usuario, messageThrow, nombreAnterior, cambios);
        return conCorrelacion(messageThrow);
    }

    @Transactional(readOnly = true)
    public MessageThrowRespuestaDto obtenerParaEliminar(Long procesoId, Long throwId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageThrow messageThrow = throwActivoDelProceso(throwId, proceso);

        List<String> advertencias = new ArrayList<>(eventoService.advertenciasSiSeElimina(proceso, messageThrow));
        advertencias.addAll(correlacionMensajeService.advertenciasSiSeEliminaThrow(messageThrow));
        MessageThrowRespuestaDto respuesta = toDto(messageThrow);
        respuesta.setAdvertencias(advertencias);
        return respuesta;
    }

    @Transactional
    public MessageThrowRespuestaDto eliminar(Long procesoId, Long throwId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageThrow messageThrow = throwActivoDelProceso(throwId, proceso);

        List<String> sinHomologo = correlacionMensajeService.advertenciasSiSeEliminaThrow(messageThrow);
        EliminacionEvento eliminacion = eventoService.eliminar(proceso, usuario, messageThrow);

        MessageThrowRespuestaDto respuesta = toDto(messageThrow);
        respuesta.setArcosDesactivados(eliminacion.arcosDesactivados());
        List<String> advertencias = new ArrayList<>(eliminacion.advertencias());
        advertencias.addAll(sinHomologo);
        respuesta.setAdvertencias(advertencias);
        return respuesta;
    }

    private MessageThrow throwActivoDelProceso(Long throwId, Proceso proceso) {
        MessageThrow messageThrow = messageThrowRepository.findByIdAndProcesoId(throwId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(THROW_NO_EXISTE));
        if (!messageThrow.isActivo()) {
            throw new RecursoNoEncontradoException(THROW_ELIMINADO);
        }
        return messageThrow;
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

    private MessageThrowRespuestaDto conCorrelacion(MessageThrow messageThrow) {
        AnalisisCorrelacion analisis = correlacionMensajeService.analizarThrow(messageThrow);
        MessageThrowRespuestaDto respuesta = toDto(messageThrow);
        respuesta.setCatchHomologoId(analisis.homologoId());
        respuesta.setAdvertencias(analisis.advertencias());
        return respuesta;
    }

    private MessageThrowRespuestaDto toDto(MessageThrow messageThrow) {
        MessageThrowRespuestaDto respuesta = new MessageThrowRespuestaDto();
        respuesta.setId(messageThrow.getId());
        respuesta.setProcesoId(messageThrow.getProceso().getId());
        respuesta.setNombreMensaje(messageThrow.getNombreMensaje());
        respuesta.setEtiqueta(messageThrow.etiqueta());
        respuesta.setContenido(messageThrow.getContenido());
        respuesta.setPoolOrigenId(messageThrow.getPool().getId());
        respuesta.setPoolOrigenNombre(messageThrow.getPool().getNombre());
        respuesta.setPoolDestinoId(messageThrow.getPoolDestino().getId());
        respuesta.setPoolDestinoNombre(messageThrow.getPoolDestino().getNombre());
        respuesta.setClaveCorrelacion(messageThrow.getClaveCorrelacion());
        respuesta.setComportamientoSinCaso(messageThrow.getComportamientoSinCaso());
        respuesta.setPosicionX(messageThrow.getPosicionX());
        respuesta.setPosicionY(messageThrow.getPosicionY());
        respuesta.setActivo(messageThrow.isActivo());
        return respuesta;
    }
}
