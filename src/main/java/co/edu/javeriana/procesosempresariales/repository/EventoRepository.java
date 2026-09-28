package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    Optional<Evento> findByIdAndProcesoId(Long id, Long procesoId);

    List<Evento> findByProcesoIdAndActivoTrueOrderByIdAsc(Long procesoId);
}
