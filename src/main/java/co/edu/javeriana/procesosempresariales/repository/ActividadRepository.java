package co.edu.javeriana.procesosempresariales.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.Actividad;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    // Evita nombres duplicados dentro del mismo proceso (mayúsculas/minúsculas).
    boolean existsByProcesoIdAndNombreIgnoreCase(Long procesoId, String nombre);

    // La actividad siempre se consulta en el contexto de su proceso.
    Optional<Actividad> findByIdAndProcesoId(Long id, Long procesoId);
}
