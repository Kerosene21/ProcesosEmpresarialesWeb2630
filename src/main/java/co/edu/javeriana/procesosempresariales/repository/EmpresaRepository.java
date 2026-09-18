package co.edu.javeriana.procesosempresariales.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import co.edu.javeriana.procesosempresariales.domain.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    boolean existsByNit(String nit);
}
