package co.edu.javeriana.procesosempresariales.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import co.edu.javeriana.procesosempresariales.domain.Proceso;

// Repositorio de procesos concentra la persistencia 
public interface ProcesoRepository extends JpaRepository<Proceso, Long> {
    // evitar nombres duplicados con mayusculas y minusculas
    boolean existsByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);
}