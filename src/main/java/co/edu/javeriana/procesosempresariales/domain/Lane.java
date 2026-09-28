package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.CheckConstraint;
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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lane", indexes = {
        @Index(name = "ix_lane_rol_proceso", columnList = "rol_proceso_id"),
        @Index(name = "ix_lane_pool", columnList = "pool_id, activo, orden") },
        check = @CheckConstraint(name = "ck_lane_nombre_o_rol_proceso",
                constraint = "nombre is not null or rol_proceso_id is not null"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Lane {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 150)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_proceso_id")
    private RolProceso rolProceso;

    @Column(nullable = false, columnDefinition = "integer default 1")
    private int orden;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean activo;

    public String nombreFuncional() {
        return rolProceso == null ? nombre : rolProceso.getNombre();
    }
}
