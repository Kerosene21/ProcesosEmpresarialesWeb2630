package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.Actividad;

public interface ActividadRepository extends JpaRepository<Actividad, Long> {
    boolean existsByProcesoIdAndNombreIgnoreCase(Long procesoId, String nombre);

    Optional<Actividad> findByIdAndProcesoId(Long id, Long procesoId);

    @Query("""
            select a from Actividad a
            where a.proceso.id = :procesoId and a.activo = true
            order by a.lane.id asc, a.posicionY asc, a.posicionX asc, a.id asc
            """)
    List<Actividad> activasDelProceso(@Param("procesoId") Long procesoId);
}
