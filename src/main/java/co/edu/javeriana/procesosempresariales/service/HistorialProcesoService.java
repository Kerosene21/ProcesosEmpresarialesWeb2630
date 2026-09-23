package co.edu.javeriana.procesosempresariales.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;

@Service
public class HistorialProcesoService {

    
    private HistorialProcesoRepository historialProcesoRepository;

    // 
    @Autowired
    public HistorialProcesoService(HistorialProcesoRepository historialProcesoRepository) {
        this.historialProcesoRepository = historialProcesoRepository;
    }

    // 3. Método de negocio para registrar en el historial
    public HistorialProceso registrarHistorial(HistorialProceso historial) {
        return this.historialProcesoRepository.save(historial);
    }
    public List<HistorialProcesoRespuestaDto> consultarPorProcesoYEmpresa(Long procesoId, Long empresaId) {
        return historialProcesoRepository
            .findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(procesoId, empresaId)
            .stream()
            .map(h -> modelMapper.map(h, HistorialProcesoRespuestaDto.class))
            .toList();
    }
}
