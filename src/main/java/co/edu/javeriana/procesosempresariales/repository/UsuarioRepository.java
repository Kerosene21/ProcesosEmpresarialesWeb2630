package co.edu.javeriana.procesosempresariales.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import co.edu.javeriana.procesosempresariales.domain.Usuario;

// Repositorio de usuarios -> Spring Data genera su implementación automáticamente
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Busca la empresa a partir de la identidad del usuario
    Optional<Usuario> findByUsername(String username);
}