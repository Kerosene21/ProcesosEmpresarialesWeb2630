package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.EnvioExterno;

public interface EnvioExternoRepository extends JpaRepository<EnvioExterno, Long> {

    Optional<EnvioExterno> findByIdAndProcesoId(Long id, Long procesoId);

    List<EnvioExterno> findByProcesoIdAndActivoTrueOrderByIdAsc(Long procesoId);
}
