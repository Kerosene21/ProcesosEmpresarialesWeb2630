package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.MessageCatch;

public interface MessageCatchRepository extends JpaRepository<MessageCatch, Long> {

    Optional<MessageCatch> findByIdAndProcesoId(Long id, Long procesoId);

    List<MessageCatch> findByProcesoIdAndActivoTrueOrderByIdAsc(Long procesoId);
}
