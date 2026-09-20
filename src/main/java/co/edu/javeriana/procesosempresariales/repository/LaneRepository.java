package co.edu.javeriana.procesosempresariales.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.Lane;

public interface LaneRepository extends JpaRepository<Lane, Long> {
    // Se usa para validar que la lane elegida pertenece al pool del proceso.
    Optional<Lane> findByIdAndPoolId(Long id, Long poolId);
}
