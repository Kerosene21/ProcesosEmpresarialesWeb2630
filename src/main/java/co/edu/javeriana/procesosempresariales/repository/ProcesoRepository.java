package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.Proceso;

// Repositorio de procesos concentra la persistencia
public interface ProcesoRepository extends JpaRepository<Proceso, Long>, JpaSpecificationExecutor<Proceso> {
    // evitar nombres duplicados con mayusculas y minusculas
    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);

    @Query("select distinct p.categoria from Proceso p where p.empresa.id = :empresaId order by p.categoria")
    List<String> categoriasDeLaEmpresa(@Param("empresaId") Long empresaId);
}
