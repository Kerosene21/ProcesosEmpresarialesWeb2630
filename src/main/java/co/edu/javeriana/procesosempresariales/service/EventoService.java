package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Evento;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.exception.MensajeEntreMismoPoolException;
import co.edu.javeriana.procesosempresariales.repository.EventoRepository;

@Service
public class EventoService {

    static final String MISMO_POOL =
            "' es a la vez origen y destino: un flujo de mensaje solo es válido entre pools distintos";

    private EventoRepository eventoRepository;
    private ConexionesService conexionesService;
    private HistorialProcesoService historialProcesoService;

    @Autowired
    public EventoService(EventoRepository eventoRepository, ConexionesService conexionesService,
            HistorialProcesoService historialProcesoService) {
        this.eventoRepository = eventoRepository;
        this.conexionesService = conexionesService;
        this.historialProcesoService = historialProcesoService;
    }

    public void exigirPoolsDistintos(Long poolOrigenId, Pool poolDestino) {
        if (poolDestino.getId().equals(poolOrigenId)) {
            throw new MensajeEntreMismoPoolException("El pool '" + poolDestino.getNombre() + MISMO_POOL);
        }
    }

    @Transactional(readOnly = true)
    public int entradasActivas(Proceso proceso, Evento evento) {
        return conexionesService.entrantesActivos(proceso.getId(), TipoNodoFlujo.EVENTO, evento.getId()).size();
    }

    @Transactional
    public void registrarCreacion(Proceso proceso, Usuario usuario, Evento evento, String detalle) {
        historialProcesoService.registrar(proceso, usuario, prefijo(evento) + " creado: '"
                + evento.getNombreMensaje() + "' (" + detalle + ")");
    }

    @Transactional
    public void registrarEdicion(Proceso proceso, Usuario usuario, Evento evento, String nombreAnterior,
            List<String> cambios) {
        historialProcesoService.registrar(proceso, usuario, prefijo(evento) + " '" + nombreAnterior + "': "
                + String.join("; ", cambios));
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasSiSeElimina(Proceso proceso, Evento evento) {
        List<Arco> conectados = conexionesService.conectadosActivos(proceso, TipoNodoFlujo.EVENTO, evento.getId());
        List<String> advertencias = new ArrayList<>();
        if (!conectados.isEmpty()) {
            advertencias.add((conectados.size() == 1 ? "Se desactivará 1 arco conectado a "
                    : "Se desactivarán " + conectados.size() + " arcos conectados a ") + evento.etiqueta() + ".");
        }
        advertencias.addAll(conexionesService.advertenciasSiSeEliminaNodo(proceso, TipoNodoFlujo.EVENTO,
                evento.getId(), conectados));
        return advertencias;
    }

    @Transactional
    public EliminacionEvento eliminar(Proceso proceso, Usuario usuario, Evento evento) {
        evento.setActivo(false);
        eventoRepository.save(evento);

        List<Arco> desactivados = conexionesService.desactivarConectadosA(proceso, TipoNodoFlujo.EVENTO,
                evento.getId());
        historialProcesoService.registrar(proceso, usuario, prefijo(evento) + " eliminado: '"
                + evento.getNombreMensaje() + "'" + resumenDeConexiones(desactivados));
        return new EliminacionEvento(desactivados.size(), conexionesService.advertenciasTrasDesactivar(proceso,
                desactivados, TipoNodoFlujo.EVENTO, evento.getId()));
    }

    private String prefijo(Evento evento) {
        return evento.getTipo().getDescripcion().toLowerCase(Locale.ROOT);
    }

    private String resumenDeConexiones(List<Arco> desactivados) {
        if (desactivados.isEmpty()) {
            return "";
        }
        return "; arcos desactivados: " + desactivados.size();
    }
}
