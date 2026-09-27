package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evento", indexes = {
        @Index(name = "ix_evento_proceso", columnList = "proceso_id, activo"),
        @Index(name = "ix_evento_pool", columnList = "pool_id, activo"),
        @Index(name = "ix_evento_pool_destino", columnList = "pool_destino_id, activo") })
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING, length = 20)
@Getter @Setter @NoArgsConstructor
public abstract class Evento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;

    @Column(name = "nombre_mensaje", nullable = false, length = 150)
    private String nombreMensaje;

    @Column(name = "clave_correlacion", length = 100)
    private String claveCorrelacion;

    @Column(name = "posicion_x", nullable = false)
    private Integer posicionX;

    @Column(name = "posicion_y", nullable = false)
    private Integer posicionY;

    @Column(nullable = false)
    private boolean activo;

    public abstract TipoEvento getTipo();

    public boolean admiteEntradas() {
        return true;
    }

    public String etiqueta() {
        return getTipo().getDescripcion() + ": " + nombreMensaje;
    }
}
