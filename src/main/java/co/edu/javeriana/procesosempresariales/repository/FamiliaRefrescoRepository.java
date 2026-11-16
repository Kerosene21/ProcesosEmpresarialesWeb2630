package co.edu.javeriana.procesosempresariales.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.javeriana.procesosempresariales.domain.FamiliaRefresco;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;

public interface FamiliaRefrescoRepository extends JpaRepository<FamiliaRefresco, UUID> {

    boolean existsByIdAndRevocadaEnIsNull(UUID id);

    boolean existsByIdAndUsuarioUsernameAndRevocadaEnIsNull(UUID id, String username);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FamiliaRefresco f set f.revocadaEn = :ahora, f.motivoRevocacion = :motivo "
            + "where f.id = :id and f.revocadaEn is null")
    int revocar(@Param("id") UUID id, @Param("motivo") MotivoRevocacion motivo, @Param("ahora") Instant ahora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update FamiliaRefresco f set f.revocadaEn = :ahora, f.motivoRevocacion = :motivo "
            + "where f.usuario.id = :usuarioId and f.revocadaEn is null")
    int revocarDelUsuario(@Param("usuarioId") Long usuarioId, @Param("motivo") MotivoRevocacion motivo,
            @Param("ahora") Instant ahora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from FamiliaRefresco f where f.expiraEn < :limite")
    int eliminarExpiradasAntesDe(@Param("limite") Instant limite);

    @Query(value = "select pg_try_advisory_xact_lock(:clave)", nativeQuery = true)
    boolean bloquearPurga(@Param("clave") long clave);
}
