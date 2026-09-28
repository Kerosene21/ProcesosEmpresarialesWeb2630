package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.Proceso;

public interface ProcesoRepository extends JpaRepository<Proceso, Long>, JpaSpecificationExecutor<Proceso> {
    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);

    @Query("select distinct p.categoria from Proceso p where p.empresa.id = :empresaId order by p.categoria")
    List<String> categoriasDeLaEmpresa(@Param("empresaId") Long empresaId);

    @Query("""
            select case when count(c) > 0 then true else false end
            from ProcesoCompartidoEmpresa c
            where c.proceso.id = :procesoId and c.empresaInvitada.id = :empresaId and c.activo = true
            """)
    boolean estaCompartidoCon(@Param("procesoId") Long procesoId, @Param("empresaId") Long empresaId);
}
