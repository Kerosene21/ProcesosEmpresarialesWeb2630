package co.edu.javeriana.procesosempresariales.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.PermisoEstructuraProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;

public interface PermisoEstructuraProcesoRepository extends JpaRepository<PermisoEstructuraProceso, Long> {

    Optional<PermisoEstructuraProceso> findByProcesoIdAndRol(Long procesoId, RolUsuario rol);
}
