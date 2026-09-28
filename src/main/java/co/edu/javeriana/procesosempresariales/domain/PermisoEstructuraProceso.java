package co.edu.javeriana.procesosempresariales.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permiso_estructura_proceso", uniqueConstraints = @UniqueConstraint(
        name = "uk_permiso_estructura_proceso_rol", columnNames = { "proceso_id", "rol" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PermisoEstructuraProceso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolUsuario rol;

    @Column(name = "crear_pool", nullable = false)
    private boolean crearPool;

    @Column(name = "editar_pool", nullable = false)
    private boolean editarPool;

    @Column(name = "eliminar_pool", nullable = false)
    private boolean eliminarPool;

    @Column(name = "crear_lane", nullable = false)
    private boolean crearLane;

    @Column(name = "editar_lane", nullable = false)
    private boolean editarLane;

    @Column(name = "eliminar_lane", nullable = false)
    private boolean eliminarLane;

    public boolean permite(OperacionEstructura operacion) {
        return switch (operacion) {
            case CREAR_POOL -> crearPool;
            case EDITAR_POOL -> editarPool;
            case ELIMINAR_POOL -> eliminarPool;
            case CREAR_LANE -> crearLane;
            case EDITAR_LANE -> editarLane;
            case ELIMINAR_LANE -> eliminarLane;
        };
    }
}
