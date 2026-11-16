package co.edu.javeriana.procesosempresariales.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "familia_refresco", indexes = {
        @Index(name = "ix_familia_refresco_usuario", columnList = "usuario_id"),
        @Index(name = "ix_familia_refresco_expira", columnList = "expira_en") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FamiliaRefresco {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_familia_refresco_usuario"))
    private Usuario usuario;

    @Column(nullable = false)
    private Instant creadaEn;

    @Column(nullable = false)
    private Instant expiraEn;

    private Instant revocadaEn;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private MotivoRevocacion motivoRevocacion;
}
