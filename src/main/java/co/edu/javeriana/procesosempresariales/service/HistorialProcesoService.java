package co.edu.javeriana.procesosempresariales.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;

@Service
public class HistorialProcesoService {

    private HistorialProcesoRepository historialProcesoRepository;

    @Autowired
    public HistorialProcesoService(HistorialProcesoRepository historialProcesoRepository) {
        this.historialProcesoRepository = historialProcesoRepository;
    }

    @Transactional
    public void registrar(Proceso proceso, Usuario usuario, String cambios) {
        registrar(proceso, usuario, cambios, proceso.getEstado().name());
    }

    @Transactional
    public void registrar(Proceso proceso, Usuario usuario, String cambios, String estadoAnterior) {
        historialProcesoRepository.save(new HistorialProceso(null, proceso, usuario,
                LocalDateTime.now(ZoneId.systemDefault()), cambios, estadoAnterior));
    }

    @Transactional(readOnly = true)
    public List<HistorialProcesoRespuestaDto> consultarDelProceso(Long procesoId, Long empresaId) {
        return historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(procesoId, empresaId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private HistorialProcesoRespuestaDto toDto(HistorialProceso historial) {
        HistorialProcesoRespuestaDto respuesta = new HistorialProcesoRespuestaDto();
        respuesta.setFecha(historial.getFecha());
        respuesta.setUsuarioCorreo(historial.getUsuario().getUsername());
        respuesta.setEstadoAnterior(historial.getEstadoAnterior());
        respuesta.setCambiosRealizados(historial.getCambiosRealizados());
        return respuesta;
    }
}
