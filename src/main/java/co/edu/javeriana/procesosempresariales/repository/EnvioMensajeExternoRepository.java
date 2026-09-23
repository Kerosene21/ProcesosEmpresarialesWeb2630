package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import co.edu.javeriana.procesosempresariales.domain.EnvioMensajeExterno;


@Repository
public interface EnvioMensajeExternoRepository extends JpaRepository<EnvioMensajeExterno, Long> {

    List<EnvioMensajeExterno> findByProcesoIdOrderByIdAsc(Long procesoId);

    Optional<EnvioMensajeExterno> findByIdAndProcesoId(Long id, Long procesoId);
}
