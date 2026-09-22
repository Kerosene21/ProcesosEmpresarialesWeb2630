package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;

public interface ArcoRepository extends JpaRepository<Arco, Long> {

    Optional<Arco> findByIdAndProcesoId(Long id, Long procesoId);

    List<Arco> findByProcesoIdAndActivoTrueOrderByIdAsc(Long procesoId);

    List<Arco> findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(Long procesoId,
            TipoNodoFlujo origenTipo, Long origenId);

    List<Arco> findByProcesoIdAndDestinoTipoAndDestinoIdAndActivoTrueOrderByIdAsc(Long procesoId,
            TipoNodoFlujo destinoTipo, Long destinoId);

    boolean existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrue(Long procesoId,
            TipoNodoFlujo origenTipo, Long origenId, TipoNodoFlujo destinoTipo, Long destinoId);

    boolean existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrueAndIdNot(Long procesoId,
            TipoNodoFlujo origenTipo, Long origenId, TipoNodoFlujo destinoTipo, Long destinoId, Long id);

    @Query("""
            select a from Arco a
            where a.proceso.id = :procesoId and a.activo = true
              and ((a.origenTipo = :tipo and a.origenId = :nodoId)
                or (a.destinoTipo = :tipo and a.destinoId = :nodoId))
            order by a.id asc
            """)
    List<Arco> conectadosAlNodo(@Param("procesoId") Long procesoId, @Param("tipo") TipoNodoFlujo tipo,
            @Param("nodoId") Long nodoId);
}
