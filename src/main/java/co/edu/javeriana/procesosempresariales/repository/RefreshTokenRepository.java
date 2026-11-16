package co.edu.javeriana.procesosempresariales.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.familia where t.tokenHash = :hash")
    Optional<RefreshToken> buscarParaRotar(@Param("hash") String hash);

    boolean existsByFamiliaIdAndUsadoEnIsNullAndEmitidoEnGreaterThanEqual(UUID familiaId, Instant emitidoDesde);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RefreshToken t where t.expiraEn < :limite")
    int eliminarExpiradosAntesDe(@Param("limite") Instant limite);
}
