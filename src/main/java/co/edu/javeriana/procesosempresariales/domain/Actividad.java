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

// Actividad = una tarea del proceso (HU-08, HU-09, HU-10).
// El nombre no se repite dentro del mismo proceso (igual que Proceso
// respecto a Empresa).
@Entity
@Table(name = "actividad", uniqueConstraints = @UniqueConstraint(name = "uk_actividad_proceso_nombre", columnNames = {
        "proceso_id", "nombre" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Actividad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoActividad tipo;

    // El proceso al que pertenece la actividad.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;

    // La lane define el rol responsable (HU-22): la actividad se dibuja
    // dentro de la banda de ese rol, no se asigna a una persona.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lane_id", nullable = false)
    private Lane lane;

    // Posición en el lienzo donde el usuario ubicó el rectángulo.
    @Column(name = "posicion_x", nullable = false)
    private Integer posicionX;

    @Column(name = "posicion_y", nullable = false)
    private Integer posicionY;

    // Eliminación lógica (HU-10): nunca se borra la fila físicamente.
    @Column(nullable = false)
    private boolean activo;
}
