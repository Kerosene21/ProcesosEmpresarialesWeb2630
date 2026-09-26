package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.NodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@Service
public class NodoFlujoResolver {

    static final String NODO_NO_EXISTE = "El nodo indicado no existe en este proceso";
    static final String NODO_INACTIVO = "El nodo indicado fue eliminado del proceso";
    static final String EVENTO_SIN_MODELO = "Los eventos todavía no existen en el modelo del proceso";

    private ActividadRepository actividadRepository;
    private GatewayRepository gatewayRepository;

    @Autowired
    public NodoFlujoResolver(ActividadRepository actividadRepository, GatewayRepository gatewayRepository) {
        this.actividadRepository = actividadRepository;
        this.gatewayRepository = gatewayRepository;
    }

    @Transactional(readOnly = true)
    public Optional<NodoFlujo> buscar(Proceso proceso, TipoNodoFlujo tipo, Long nodoId) {
        if (tipo == null || nodoId == null) {
            return Optional.empty();
        }
        return switch (tipo) {
            case ACTIVIDAD -> actividadRepository.findByIdAndProcesoId(nodoId, proceso.getId())
                    .map(this::desdeActividad);
            case GATEWAY -> gatewayRepository.findByIdAndProcesoId(nodoId, proceso.getId())
                    .map(this::desdeGateway);
            case EVENTO -> Optional.empty();
        };
    }

    @Transactional(readOnly = true)
    public NodoFlujo resolverActivo(Proceso proceso, TipoNodoFlujo tipo, Long nodoId) {
        if (tipo == TipoNodoFlujo.EVENTO) {
            throw new NodoFlujoNoValidoException(EVENTO_SIN_MODELO);
        }
        NodoFlujo nodo = buscar(proceso, tipo, nodoId)
                .orElseThrow(() -> new NodoFlujoNoValidoException(NODO_NO_EXISTE));
        if (!nodo.activo()) {
            throw new NodoFlujoNoValidoException(NODO_INACTIVO);
        }
        return nodo;
    }

    @Transactional(readOnly = true)
    public List<NodoFlujo> nodosActivos(Proceso proceso) {
        List<NodoFlujo> nodos = new ArrayList<>(actividadRepository.activasDelProceso(proceso.getId()).stream()
                .map(this::desdeActividad)
                .toList());
        nodos.addAll(gatewayRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId()).stream()
                .map(this::desdeGateway)
                .toList());
        return nodos;
    }

    @Transactional(readOnly = true)
    public Map<String, NodoFlujo> indiceDeNodosActivos(Proceso proceso) {
        return nodosActivos(proceso).stream()
                .collect(Collectors.toMap(nodo -> clave(nodo.tipo(), nodo.id()), Function.identity()));
    }

    @Transactional(readOnly = true)
    public String describir(Proceso proceso, TipoNodoFlujo tipo, Long nodoId) {
        return buscar(proceso, tipo, nodoId).map(NodoFlujo::nombre).orElseGet(() -> referencia(tipo, nodoId));
    }

    public String clave(TipoNodoFlujo tipo, Long nodoId) {
        return tipo + ":" + nodoId;
    }

    public String referencia(TipoNodoFlujo tipo, Long nodoId) {
        return tipo + " #" + nodoId;
    }

    public NodoFlujo desdeActividad(Actividad actividad) {
        return new NodoFlujo(TipoNodoFlujo.ACTIVIDAD, actividad.getId(), actividad.getLane().getPool().getId(),
                actividad.getNombre(), actividad.getPosicionX(), actividad.getPosicionY(), actividad.isActivo(), null);
    }

    public NodoFlujo desdeGateway(Gateway gateway) {
        return new NodoFlujo(TipoNodoFlujo.GATEWAY, gateway.getId(), gateway.getPool().getId(), gateway.etiqueta(),
                gateway.getPosicionX(), gateway.getPosicionY(), gateway.isActivo(), gateway.getTipo());
    }
}
