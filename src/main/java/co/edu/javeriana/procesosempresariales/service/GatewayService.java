package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@Service
public class GatewayService {

    static final String GATEWAY_NO_EXISTE = "El gateway no existe en este proceso";
    static final String GATEWAY_ELIMINADO = "El gateway ya fue eliminado";
    static final String SALIDA_SIN_CONDICION =
            "Cada arco de salida de un gateway exclusivo o inclusivo necesita condición";
    static final String SIN_PERMISO_ESCRITURA = "Solo un administrador o editor puede crear o modificar gateways";

    private GatewayRepository gatewayRepository;
    private AccesoProcesoService accesoProcesoService;
    private HistorialProcesoService historialProcesoService;
    private ConexionesService conexionesService;
    private NodoFlujoResolver nodoFlujoResolver;

    @Autowired
    public GatewayService(GatewayRepository gatewayRepository, AccesoProcesoService accesoProcesoService,
            HistorialProcesoService historialProcesoService, ConexionesService conexionesService,
            NodoFlujoResolver nodoFlujoResolver) {
        this.gatewayRepository = gatewayRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.historialProcesoService = historialProcesoService;
        this.conexionesService = conexionesService;
        this.nodoFlujoResolver = nodoFlujoResolver;
    }

    @Transactional
    public GatewayRespuestaDto crear(Long procesoId, CrearGatewayDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);

        Gateway gateway = new Gateway();
        gateway.setTipo(dto.getTipo());
        gateway.setProceso(proceso);
        gateway.setPosicionX(dto.getPosicionX());
        gateway.setPosicionY(dto.getPosicionY());
        gateway.setActivo(true);

        Gateway guardado = gatewayRepository.save(gateway);
        historialProcesoService.registrar(proceso, usuario, "gateway creado: " + guardado.getTipo() + " #"
                + guardado.getId() + " en (" + guardado.getPosicionX() + ", " + guardado.getPosicionY() + ")");

        return conAdvertencias(toDto(guardado), proceso, guardado);
    }

    @Transactional(readOnly = true)
    public GatewayRespuestaDto obtener(Long procesoId, Long gatewayId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        Gateway gateway = gatewayActivoDelProceso(gatewayId, proceso);
        return conAdvertencias(toDto(gateway), proceso, gateway);
    }

    @Transactional(readOnly = true)
    public List<GatewayRespuestaDto> consultarActivos(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        return activosDelProceso(proceso).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Gateway> activosDelProceso(Proceso proceso) {
        return gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId());
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasDelProceso(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        List<String> advertencias = new ArrayList<>();
        for (Gateway gateway : activosDelProceso(proceso)) {
            advertencias.addAll(advertenciasDe(proceso, gateway));
        }
        return advertencias;
    }

    @Transactional
    public GatewayRespuestaDto editar(Long procesoId, Long gatewayId, EditarGatewayDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Gateway gateway = gatewayActivoDelProceso(gatewayId, proceso);

        List<Arco> salientes = conexionesService.salientesActivos(proceso.getId(), TipoNodoFlujo.GATEWAY,
                gateway.getId());
        List<String> cambios = new ArrayList<>();
        if (gateway.getTipo() != dto.getTipo()) {
            cambios.add("tipo: '" + gateway.getTipo() + "' -> '" + dto.getTipo() + "'");
        }
        List<Arco> modificados = dto.getTipo() == TipoGateway.PARALELO
                ? limpiarCondiciones(salientes)
                : aplicarCondiciones(salientes, dto);
        agregarCambioDeCondiciones(cambios, modificados, dto.getTipo());

        if (cambios.isEmpty()) {
            return conAdvertencias(toDto(gateway), proceso, gateway);
        }

        gateway.setTipo(dto.getTipo());
        gatewayRepository.save(gateway);
        conexionesService.guardarCambios(modificados);
        historialProcesoService.registrar(proceso, usuario,
                "gateway #" + gateway.getId() + ": " + String.join("; ", cambios));

        return conAdvertencias(toDto(gateway), proceso, gateway);
    }

    private List<Arco> limpiarCondiciones(List<Arco> salientes) {
        List<Arco> modificados = new ArrayList<>();
        for (Arco arco : salientes) {
            if (arco.getCondicion() != null) {
                arco.setCondicion(null);
                modificados.add(arco);
            }
        }
        return modificados;
    }

    private List<Arco> aplicarCondiciones(List<Arco> salientes, EditarGatewayDto dto) {
        List<Arco> modificados = new ArrayList<>();
        for (Arco arco : salientes) {
            String condicion = textoONulo(dto.getCondiciones().get(arco.getId()));
            if (condicion == null) {
                if (arco.getCondicion() == null) {
                    throw new CondicionArcoNoValidaException(SALIDA_SIN_CONDICION);
                }
            } else if (!condicion.equals(arco.getCondicion())) {
                arco.setCondicion(condicion);
                modificados.add(arco);
            }
        }
        return modificados;
    }

    private void agregarCambioDeCondiciones(List<String> cambios, List<Arco> modificados, TipoGateway tipo) {
        if (modificados.isEmpty()) {
            return;
        }
        String sufijo = modificados.size() == 1 ? " arco de salida" : " arcos de salida";
        String accion = tipo == TipoGateway.PARALELO ? "condiciones eliminadas en " : "condiciones actualizadas en ";
        cambios.add(accion + modificados.size() + sufijo);
    }

    private Gateway gatewayActivoDelProceso(Long gatewayId, Proceso proceso) {
        Gateway gateway = gatewayRepository.findByIdAndProcesoId(gatewayId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(GATEWAY_NO_EXISTE));
        if (!gateway.isActivo()) {
            throw new RecursoNoEncontradoException(GATEWAY_ELIMINADO);
        }
        return gateway;
    }

    private List<String> advertenciasDe(Proceso proceso, Gateway gateway) {
        return conexionesService.advertenciasDeGateway(proceso, nodoFlujoResolver.desdeGateway(gateway));
    }

    private GatewayRespuestaDto conAdvertencias(GatewayRespuestaDto respuesta, Proceso proceso, Gateway gateway) {
        respuesta.setAdvertencias(advertenciasDe(proceso, gateway));
        return respuesta;
    }

    private String textoONulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private GatewayRespuestaDto toDto(Gateway gateway) {
        GatewayRespuestaDto respuesta = new GatewayRespuestaDto();
        respuesta.setId(gateway.getId());
        respuesta.setProcesoId(gateway.getProceso().getId());
        respuesta.setTipo(gateway.getTipo());
        respuesta.setSimbolo(gateway.getTipo().getSimbolo());
        respuesta.setEtiqueta(gateway.etiqueta());
        respuesta.setPosicionX(gateway.getPosicionX());
        respuesta.setPosicionY(gateway.getPosicionY());
        respuesta.setActivo(gateway.isActivo());
        return respuesta;
    }
}
