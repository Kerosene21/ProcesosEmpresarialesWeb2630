package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Evento;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.PosicionNodoDto;
import co.edu.javeriana.procesosempresariales.dto.RecolocarNodosDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.EventoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;

@Service
public class RecolocacionNodosService {

    static final String SIN_PERMISO_ESCRITURA = "Solo un administrador o editor puede modificar el diagrama";
    static final String NODO_NO_EXISTE = " no existe en este proceso o ya fue eliminado";
    static final String ACTIVIDAD_NO_EXISTE = " no existe en este proceso o ya fue eliminada";

    private ActividadRepository actividadRepository;
    private GatewayRepository gatewayRepository;
    private EventoRepository eventoRepository;
    private AccesoProcesoService accesoProcesoService;
    private HistorialProcesoService historialProcesoService;

    @Autowired
    public RecolocacionNodosService(ActividadRepository actividadRepository, GatewayRepository gatewayRepository,
            EventoRepository eventoRepository, AccesoProcesoService accesoProcesoService,
            HistorialProcesoService historialProcesoService) {
        this.actividadRepository = actividadRepository;
        this.gatewayRepository = gatewayRepository;
        this.eventoRepository = eventoRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.historialProcesoService = historialProcesoService;
    }

    @Transactional
    public void recolocar(Long procesoId, RecolocarNodosDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);

        List<BooleanSupplier> cambios = new ArrayList<>();
        for (PosicionNodoDto posicion : dto.getNodos()) {
            cambios.add(preparar(proceso, posicion));
        }
        int recolocados = 0;
        for (BooleanSupplier cambio : cambios) {
            if (cambio.getAsBoolean()) {
                recolocados++;
            }
        }
        if (recolocados > 0) {
            historialProcesoService.registrar(proceso, usuario, "elementos del diagrama recolocados: " + recolocados);
        }
    }

    private BooleanSupplier preparar(Proceso proceso, PosicionNodoDto posicion) {
        return switch (posicion.getTipo()) {
            case ACTIVIDAD -> {
                Actividad actividad = actividad(proceso, posicion.getId());
                yield () -> actualizar(actividad, posicion);
            }
            case GATEWAY -> {
                Gateway gateway = gateway(proceso, posicion.getId());
                yield () -> actualizar(gateway, posicion);
            }
            case EVENTO -> {
                Evento evento = evento(proceso, posicion.getId());
                yield () -> actualizar(evento, posicion);
            }
        };
    }

    private boolean actualizar(Actividad actividad, PosicionNodoDto posicion) {
        if (mismaPosicion(actividad.getPosicionX(), actividad.getPosicionY(), posicion)) {
            return false;
        }
        actividad.setPosicionX(posicion.getPosicionX());
        actividad.setPosicionY(posicion.getPosicionY());
        actividadRepository.save(actividad);
        return true;
    }

    private boolean actualizar(Gateway gateway, PosicionNodoDto posicion) {
        if (mismaPosicion(gateway.getPosicionX(), gateway.getPosicionY(), posicion)) {
            return false;
        }
        gateway.setPosicionX(posicion.getPosicionX());
        gateway.setPosicionY(posicion.getPosicionY());
        gatewayRepository.save(gateway);
        return true;
    }

    private boolean actualizar(Evento evento, PosicionNodoDto posicion) {
        if (mismaPosicion(evento.getPosicionX(), evento.getPosicionY(), posicion)) {
            return false;
        }
        evento.setPosicionX(posicion.getPosicionX());
        evento.setPosicionY(posicion.getPosicionY());
        eventoRepository.save(evento);
        return true;
    }

    private boolean mismaPosicion(Integer posicionX, Integer posicionY, PosicionNodoDto posicion) {
        return posicion.getPosicionX().equals(posicionX) && posicion.getPosicionY().equals(posicionY);
    }

    private Actividad actividad(Proceso proceso, Long id) {
        return actividadRepository.findByIdAndProcesoId(id, proceso.getId())
                .filter(Actividad::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("La actividad #" + id + ACTIVIDAD_NO_EXISTE));
    }

    private Gateway gateway(Proceso proceso, Long id) {
        return gatewayRepository.findByIdAndProcesoId(id, proceso.getId())
                .filter(Gateway::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("El gateway #" + id + NODO_NO_EXISTE));
    }

    private Evento evento(Proceso proceso, Long id) {
        return eventoRepository.findByIdAndProcesoId(id, proceso.getId())
                .filter(Evento::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("El evento #" + id + NODO_NO_EXISTE));
    }
}
