package co.edu.javeriana.procesosempresariales.service;

import java.util.List;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;

@Service
public class HistorialProcesoService {

    private HistorialProcesoRepository historialProcesoRepository;
    private ModelMapper modelMapper;

    @Autowired
    public HistorialProcesoService(HistorialProcesoRepository historialProcesoRepository, ModelMapper modelMapper) {
        this.historialProcesoRepository = historialProcesoRepository;
        this.modelMapper = modelMapper;
    }

    public HistorialProceso registrarHistorial(HistorialProceso historial) {
        return this.historialProcesoRepository.save(historial);
    }

    public List<HistorialProcesoRespuestaDto> consultarPorProcesoYEmpresa(Long procesoId, Long empresaId) {
        return historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(procesoId, empresaId)
                .stream()
                .map(h -> modelMapper.map(h, HistorialProcesoRespuestaDto.class))
                .toList();
    }
}
