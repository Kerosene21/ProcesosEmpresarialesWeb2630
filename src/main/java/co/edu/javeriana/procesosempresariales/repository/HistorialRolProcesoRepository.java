package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.HistorialRolProceso;

public interface HistorialRolProcesoRepository extends JpaRepository<HistorialRolProceso, Long> {
    List<HistorialRolProceso> findByRolProcesoIdAndRolProcesoEmpresaIdOrderByFechaDescIdDesc(Long rolProcesoId,
            Long empresaId);
}
