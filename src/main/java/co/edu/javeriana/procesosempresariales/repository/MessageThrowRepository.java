package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.MessageThrow;

public interface MessageThrowRepository extends JpaRepository<MessageThrow, Long> {

    Optional<MessageThrow> findByIdAndProcesoId(Long id, Long procesoId);

    List<MessageThrow> findByProcesoIdAndActivoTrueOrderByIdAsc(Long procesoId);
}
