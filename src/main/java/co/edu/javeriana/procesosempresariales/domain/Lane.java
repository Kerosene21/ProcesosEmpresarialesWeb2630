package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Versión mínima de Lane: solo lo necesario para que Actividad (HU-08/09/10)
// pueda asignarse a una banda dentro del pool. Cuando se implemente la
// historia de "pools y lanes" (rol de proceso responsable, HU-17/HU-22),
// esta entidad probablemente necesite un campo adicional para el rol de
// proceso responsable de la lane; por ahora solo guarda el nombre.
@Entity
@Table(name = "lane")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Lane {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre; // nombre del rol responsable de esta banda

    // Toda lane pertenece a un pool: es la división interna de ese pool.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool;
}
