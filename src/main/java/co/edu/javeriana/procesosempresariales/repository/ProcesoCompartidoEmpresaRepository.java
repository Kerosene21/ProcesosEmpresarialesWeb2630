package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.javeriana.procesosempresariales.domain.ProcesoCompartidoEmpresa;

public interface ProcesoCompartidoEmpresaRepository extends JpaRepository<ProcesoCompartidoEmpresa, Long> {

    Optional<ProcesoCompartidoEmpresa> findByProcesoIdAndEmpresaInvitadaId(Long procesoId, Long empresaInvitadaId);

    List<ProcesoCompartidoEmpresa> findByProcesoIdAndActivoTrueOrderByEmpresaInvitadaNombreAsc(Long procesoId);
}
