package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "proceso_compartido_empresa",
        uniqueConstraints = @UniqueConstraint(name = "uk_proceso_compartido_empresa",
                columnNames = { "proceso_id", "empresa_invitada_id" }),
        indexes = @Index(name = "ix_proceso_compartido_invitada", columnList = "empresa_invitada_id, activo"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProcesoCompartidoEmpresa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_invitada_id", nullable = false)
    private Empresa empresaInvitada;

    @Column(nullable = false)
    private boolean activo;
}
