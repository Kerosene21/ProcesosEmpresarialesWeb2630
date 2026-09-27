package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.EditarMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CatchInicioConEntradaException;
import co.edu.javeriana.procesosempresariales.exception.CorrelacionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.MessageCatchRepository;

@Service
public class MessageCatchService {

    static final String CATCH_NO_EXISTE = "El Message Catch no existe en este proceso";
    static final String CATCH_ELIMINADO = "El Message Catch ya fue eliminado";
    static final String SIN_PERMISO_ESCRITURA = "Solo un administrador o editor puede crear o modificar Message Catch";
    static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar Message Catch";
    static final String INICIO_SIEMPRE_INICIA = "Un Message Catch de inicio siempre inicia un caso nuevo:"
            + " no puede descartar mensajes sin caso en espera";
    static final String INICIO_CON_ENTRADAS = " flujos de secuencia entrantes: un Message Catch de inicio no admite"
            + " entradas";

    private MessageCatchRepository messageCatchRepository;
    private AccesoProcesoService accesoProcesoService;
    private PoolService poolService;
    private EventoService eventoService;
    private CorrelacionMensajeService correlacionMensajeService;

    @Autowired
    public MessageCatchService(MessageCatchRepository messageCatchRepository,
            AccesoProcesoService accesoProcesoService, PoolService poolService, EventoService eventoService,
            CorrelacionMensajeService correlacionMensajeService) {
        this.messageCatchRepository = messageCatchRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.poolService = poolService;
        this.eventoService = eventoService;
        this.correlacionMensajeService = correlacionMensajeService;
    }

    @Transactional
    public MessageCatchRespuestaDto crear(Long procesoId, CrearMessageCatchDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Pool pool = poolService.poolParaNodo(proceso, dto.getPoolId());
        ComportamientoSinCaso comportamiento = comportamientoValidado(dto.getVariante(),
                dto.getComportamientoSinCaso());

        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setProceso(proceso);
        messageCatch.setPool(pool);
        messageCatch.setNombreMensaje(dto.getNombreMensaje().trim());
        messageCatch.setVariante(dto.getVariante());
        messageCatch.setDatosEsperados(dto.getDatosEsperados().trim());
        messageCatch.setActividadesUso(dto.getActividadesUso().trim());
        messageCatch.setOrigenExterno(dto.esOrigenExterno());
        messageCatch.setClaveCorrelacion(textoONulo(dto.getClaveCorrelacion()));
        messageCatch.setComportamientoSinCaso(comportamiento);
        messageCatch.setPosicionX(dto.getPosicionX());
        messageCatch.setPosicionY(dto.getPosicionY());
        messageCatch.setActivo(true);

        MessageCatch guardado = messageCatchRepository.save(messageCatch);
        eventoService.registrarCreacion(proceso, usuario, guardado,
                guardado.getVariante() + " en el pool '" + pool.getNombre() + "'"
                        + (guardado.esOrigenExterno() ? ", origen externo" : ""));
        return conCorrelacion(guardado);
    }

    @Transactional(readOnly = true)
    public MessageCatchRespuestaDto obtener(Long procesoId, Long catchId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return conCorrelacion(catchActivoDelProceso(catchId, proceso));
    }

    @Transactional(readOnly = true)
    public List<MessageCatchRespuestaDto> listar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId()).stream()
                .map(this::conCorrelacion)
                .toList();
    }

    @Transactional
    public MessageCatchRespuestaDto editar(Long procesoId, Long catchId, EditarMessageCatchDto dto,
            String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageCatch messageCatch = catchActivoDelProceso(catchId, proceso);
        ComportamientoSinCaso comportamiento = comportamientoValidado(dto.getVariante(),
                dto.getComportamientoSinCaso());
        if (dto.getVariante() == VarianteMessageCatch.INICIO) {
            exigirSinEntradas(proceso, messageCatch);
        }

        String nombreNuevo = dto.getNombreMensaje().trim();
        String datosNuevos = dto.getDatosEsperados().trim();
        String actividadesNuevas = dto.getActividadesUso().trim();
        String claveNueva = textoONulo(dto.getClaveCorrelacion());
        List<String> cambios = new ArrayList<>();
        agregarCambio(cambios, "nombre", messageCatch.getNombreMensaje(), nombreNuevo);
        agregarCambio(cambios, "variante", messageCatch.getVariante(), dto.getVariante());
        agregarCambio(cambios, "datos esperados", messageCatch.getDatosEsperados(), datosNuevos);
        agregarCambio(cambios, "actividades que usan los datos", messageCatch.getActividadesUso(), actividadesNuevas);
        agregarCambio(cambios, "origen externo", messageCatch.esOrigenExterno(), dto.esOrigenExterno());
        agregarCambio(cambios, "clave de correlacion", messageCatch.getClaveCorrelacion(), claveNueva);
        agregarCambio(cambios, "comportamiento sin caso", messageCatch.getComportamientoSinCaso(), comportamiento);
        if (cambios.isEmpty()) {
            return conCorrelacion(messageCatch);
        }

        String nombreAnterior = messageCatch.getNombreMensaje();
        messageCatch.setNombreMensaje(nombreNuevo);
        messageCatch.setVariante(dto.getVariante());
        messageCatch.setDatosEsperados(datosNuevos);
        messageCatch.setActividadesUso(actividadesNuevas);
        messageCatch.setOrigenExterno(dto.esOrigenExterno());
        messageCatch.setClaveCorrelacion(claveNueva);
        messageCatch.setComportamientoSinCaso(comportamiento);
        messageCatchRepository.save(messageCatch);
        eventoService.registrarEdicion(proceso, usuario, messageCatch, nombreAnterior, cambios);
        return conCorrelacion(messageCatch);
    }

    @Transactional(readOnly = true)
    public MessageCatchRespuestaDto obtenerParaEliminar(Long procesoId, Long catchId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageCatch messageCatch = catchActivoDelProceso(catchId, proceso);

        List<String> advertencias = new ArrayList<>(eventoService.advertenciasSiSeElimina(proceso, messageCatch));
        advertencias.addAll(correlacionMensajeService.advertenciasSiSeEliminaCatch(messageCatch));
        MessageCatchRespuestaDto respuesta = toDto(messageCatch);
        respuesta.setAdvertencias(advertencias);
        return respuesta;
    }

    @Transactional
    public MessageCatchRespuestaDto eliminar(Long procesoId, Long catchId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        MessageCatch messageCatch = catchActivoDelProceso(catchId, proceso);

        List<String> sinHomologo = correlacionMensajeService.advertenciasSiSeEliminaCatch(messageCatch);
        EliminacionEvento eliminacion = eventoService.eliminar(proceso, usuario, messageCatch);

        MessageCatchRespuestaDto respuesta = toDto(messageCatch);
        respuesta.setArcosDesactivados(eliminacion.arcosDesactivados());
        List<String> advertencias = new ArrayList<>(eliminacion.advertencias());
        advertencias.addAll(sinHomologo);
        respuesta.setAdvertencias(advertencias);
        return respuesta;
    }

    private ComportamientoSinCaso comportamientoValidado(VarianteMessageCatch variante,
            ComportamientoSinCaso comportamiento) {
        if (variante != VarianteMessageCatch.INICIO) {
            return comportamiento;
        }
        if (comportamiento == ComportamientoSinCaso.DESCARTAR) {
            throw new CorrelacionNoValidaException(INICIO_SIEMPRE_INICIA);
        }
        return ComportamientoSinCaso.INICIAR_NUEVO_CASO;
    }

    private void exigirSinEntradas(Proceso proceso, MessageCatch messageCatch) {
        int entradas = eventoService.entradasActivas(proceso, messageCatch);
        if (entradas > 0) {
            throw new CatchInicioConEntradaException("El Message Catch '" + messageCatch.getNombreMensaje()
                    + "' tiene " + entradas + INICIO_CON_ENTRADAS);
        }
    }

    private MessageCatch catchActivoDelProceso(Long catchId, Proceso proceso) {
        MessageCatch messageCatch = messageCatchRepository.findByIdAndProcesoId(catchId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(CATCH_NO_EXISTE));
        if (!messageCatch.isActivo()) {
            throw new RecursoNoEncontradoException(CATCH_ELIMINADO);
        }
        return messageCatch;
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

    private MessageCatchRespuestaDto conCorrelacion(MessageCatch messageCatch) {
        AnalisisCorrelacion analisis = correlacionMensajeService.analizarCatch(messageCatch);
        MessageCatchRespuestaDto respuesta = toDto(messageCatch);
        respuesta.setThrowHomologoId(analisis.homologoId());
        respuesta.setAdvertencias(analisis.advertencias());
        return respuesta;
    }

    private MessageCatchRespuestaDto toDto(MessageCatch messageCatch) {
        MessageCatchRespuestaDto respuesta = new MessageCatchRespuestaDto();
        respuesta.setId(messageCatch.getId());
        respuesta.setProcesoId(messageCatch.getProceso().getId());
        respuesta.setNombreMensaje(messageCatch.getNombreMensaje());
        respuesta.setEtiqueta(messageCatch.etiqueta());
        respuesta.setVariante(messageCatch.getVariante());
        respuesta.setDatosEsperados(messageCatch.getDatosEsperados());
        respuesta.setActividadesUso(messageCatch.getActividadesUso());
        respuesta.setPoolId(messageCatch.getPool().getId());
        respuesta.setPoolNombre(messageCatch.getPool().getNombre());
        respuesta.setOrigenExterno(messageCatch.esOrigenExterno());
        respuesta.setClaveCorrelacion(messageCatch.getClaveCorrelacion());
        respuesta.setComportamientoSinCaso(messageCatch.getComportamientoSinCaso());
        respuesta.setPosicionX(messageCatch.getPosicionX());
        respuesta.setPosicionY(messageCatch.getPosicionY());
        respuesta.setActivo(messageCatch.isActivo());
        return respuesta;
    }
}
