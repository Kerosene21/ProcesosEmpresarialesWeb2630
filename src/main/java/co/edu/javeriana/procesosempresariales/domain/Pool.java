package co.edu.javeriana.procesosempresariales.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pool", indexes = @Index(name = "ix_pool_proceso", columnList = "proceso_id, activo, orden"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Pool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'PROPIETARIO'")
    private TipoPool tipo;

    @Column(nullable = false, columnDefinition = "integer default 1")
    private int orden;

    @Column(name = "caja_negra", nullable = false, columnDefinition = "boolean default false")
    private boolean cajaNegra;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_participante_id")
    private Empresa empresaParticipante;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc, id asc")
    private List<Lane> lanes = new ArrayList<>();

    public void agregarLane(Lane lane) {
        lane.setPool(this);
        lanes.add(lane);
    }

    public boolean esPropietario() {
        return tipo == TipoPool.PROPIETARIO;
    }
}
