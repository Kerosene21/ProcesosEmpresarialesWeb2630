package co.edu.javeriana.procesosempresariales.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.dto.UsoRolProceso;

public interface RolProcesoRepository extends JpaRepository<RolProceso, Long>, JpaSpecificationExecutor<RolProceso> {

    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);

    @Query("""
            select new co.edu.javeriana.procesosempresariales.dto.UsoRolProceso(
                l.rolProceso.id, p.id, p.nombre, l.id,
                (select count(a) from Actividad a where a.lane = l and a.activo = true))
            from Lane l join Proceso p on p.pool = l.pool
            where l.rolProceso.id in :rolesProceso and p.eliminado = false
            order by p.nombre asc, l.id asc
            """)
    List<UsoRolProceso> usosEnProcesosActivos(@Param("rolesProceso") Collection<Long> rolesProceso);
}
