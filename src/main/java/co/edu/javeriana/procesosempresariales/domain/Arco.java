package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "arco", indexes = {
        @Index(name = "ix_arco_proceso_activo", columnList = "proceso_id, activo"),
        @Index(name = "ix_arco_origen", columnList = "proceso_id, origen_tipo, origen_id, activo"),
        @Index(name = "ix_arco_destino", columnList = "proceso_id, destino_tipo, destino_id, activo") })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Arco {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_tipo", nullable = false, length = 20)
    private TipoNodoFlujo origenTipo;

    @Column(name = "origen_id", nullable = false)
    private Long origenId;

    @Enumerated(EnumType.STRING)
    @Column(name = "destino_tipo", nullable = false, length = 20)
    private TipoNodoFlujo destinoTipo;

    @Column(name = "destino_id", nullable = false)
    private Long destinoId;

    @Column(length = 150)
    private String etiqueta;

    @Column(length = 200)
    private String condicion;

    @Column(nullable = false)
    private boolean activo;
}
