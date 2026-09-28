package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.Lane;

public interface LaneRepository extends JpaRepository<Lane, Long> {

    Optional<Lane> findByIdAndPoolIdAndActivoTrue(Long id, Long poolId);

    Optional<Lane> findByIdAndPoolProcesoIdAndActivoTrueAndPoolActivoTrue(Long id, Long procesoId);

    List<Lane> findByPoolIdAndActivoTrueOrderByOrdenAscIdAsc(Long poolId);

    boolean existsByPoolIdAndRolProcesoIdAndActivoTrue(Long poolId, Long rolProcesoId);

    boolean existsByPoolIdAndRolProcesoIdAndActivoTrueAndIdNot(Long poolId, Long rolProcesoId, Long id);

    @Query("""
            select l from Lane l join l.pool p
            where p.proceso.id = :procesoId and l.activo = true and p.activo = true
            order by p.orden asc, p.id asc, l.orden asc, l.id asc
            """)
    List<Lane> activasDelProceso(@Param("procesoId") Long procesoId);

    @Query("select count(a) from Actividad a where a.lane.id = :laneId and a.activo = true")
    long actividadesActivas(@Param("laneId") Long laneId);
}
