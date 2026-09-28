package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;

public interface HistorialProcesoRepository extends JpaRepository<HistorialProceso, Long> {
    List<HistorialProceso> findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(Long procesoId, Long empresaId);
}
