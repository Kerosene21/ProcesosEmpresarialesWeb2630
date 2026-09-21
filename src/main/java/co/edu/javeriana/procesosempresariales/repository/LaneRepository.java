package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.Lane;

public interface LaneRepository extends JpaRepository<Lane, Long> {
    Optional<Lane> findByIdAndPoolId(Long id, Long poolId);

    List<Lane> findByPoolIdOrderByIdAsc(Long poolId);
}
