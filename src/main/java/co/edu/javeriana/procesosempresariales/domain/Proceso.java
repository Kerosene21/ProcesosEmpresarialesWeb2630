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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "proceso", uniqueConstraints = @UniqueConstraint(name = "uk_proceso_empresa_nombre", columnNames = {"empresa_id", "nombre"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Proceso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false, length = 100)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProceso estado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @OneToMany(mappedBy = "proceso", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc, id asc")
    private List<Pool> pools = new ArrayList<>();

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean eliminado;

    public void agregarPool(Pool pool) {
        pool.setProceso(this);
        pools.add(pool);
    }

    public Pool poolPropietario() {
        return pools.stream()
                .filter(pool -> pool.getTipo() == TipoPool.PROPIETARIO)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("El proceso " + id + " no tiene pool propietario"));
    }
}
