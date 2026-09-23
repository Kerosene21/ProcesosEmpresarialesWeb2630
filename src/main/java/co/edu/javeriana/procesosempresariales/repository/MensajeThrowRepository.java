package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.procesosempresariales.domain.MensajeThrow;

@Repository
public interface MensajeThrowRepository extends JpaRepository<MensajeThrow, Long> {

    // Las consultas derivadas respetan la restriccion de estado de la entidad.
    List<MensajeThrow> findByProcesoIdOrderByIdAsc(Long procesoId);

    Optional<MensajeThrow> findByIdAndProcesoId(Long id, Long procesoId);

    // HU-27 usa esta consulta para verificar el origen del mensaje dentro del mismo proceso.
    boolean existsByProcesoIdAndNombreIgnoreCase(Long procesoId, String nombre);
}
