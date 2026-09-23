package co.edu.javeriana.procesosempresariales.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import co.edu.javeriana.procesosempresariales.domain.Proceso;

// Repositorio de procesos concentra la persistencia 
public interface ProcesoRepository extends JpaRepository<Proceso, Long> {
    // evitar nombres duplicados con mayusculas y minusculas
    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);
    Page<Proceso> findByEmpresaIdAndEliminadoFalse(Long empresaId, Pageable pageable);

    @Query("SELECT DISTINCT p.categoria FROM Proceso p WHERE p.empresa.id = :empresaId AND p.categoria IS NOT NULL AND p.eliminado = false ORDER BY p.categoria ASC")
    List<String> categoriasDeLaEmpresa(@Param("empresaId") Long empresaId);

}



