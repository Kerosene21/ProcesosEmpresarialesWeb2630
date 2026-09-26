package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.Pool;

public interface PoolRepository extends JpaRepository<Pool, Long> {

    Optional<Pool> findByIdAndProcesoId(Long id, Long procesoId);

    List<Pool> findByProcesoIdAndActivoTrueOrderByOrdenAscIdAsc(Long procesoId);

    @Query("select count(l) from Lane l where l.pool.id = :poolId and l.activo = true")
    long lanesActivas(@Param("poolId") Long poolId);

    @Query("select count(a) from Actividad a where a.lane.pool.id = :poolId and a.activo = true")
    long actividadesActivas(@Param("poolId") Long poolId);

    @Query("select count(g) from Gateway g where g.pool.id = :poolId and g.activo = true")
    long gatewaysActivos(@Param("poolId") Long poolId);
}
