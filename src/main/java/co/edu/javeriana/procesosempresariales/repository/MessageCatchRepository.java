package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.procesosempresariales.domain.MessageCatch;

@Repository
public interface MessageCatchRepository extends JpaRepository<MessageCatch, Long> {

    List<MessageCatch> findByProcesoIdOrderByIdAsc(Long procesoId);

    Optional<MessageCatch> findByIdAndProcesoId(Long id, Long procesoId);
}
