package co.edu.javeriana.procesosempresariales.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;

// Persistencia del historial cada edición debe crear un registro independiente
public interface HistorialProcesoRepository extends JpaRepository<HistorialProceso, Long> {
}