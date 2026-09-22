package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.Gateway;

public interface GatewayRepository extends JpaRepository<Gateway, Long> {

    Optional<Gateway> findByIdAndProcesoId(Long id, Long procesoId);

    List<Gateway> findByProcesoIdAndActivoTrueOrderByIdAsc(Long procesoId);
}
