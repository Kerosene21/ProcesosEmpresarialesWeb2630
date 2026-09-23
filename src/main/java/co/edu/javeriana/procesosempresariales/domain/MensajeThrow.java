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
// Hibernate filtra lo inactivo y convierte delete() en una actualizacion 
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "mensaje_throw", uniqueConstraints = @UniqueConstraint(
        name = "uk_mensaje_throw_proceso_codigo", columnNames = {"proceso_id", "codigo_referencia"}))
@SQLRestriction("estado = 'ACTIVO'")
@SQLDelete(sql = "UPDATE mensaje_throw SET estado = 'INACTIVO' WHERE id = ?")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MensajeThrow {

    // Identifica la configuracion que tenia Message Throw.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(name = "codigo_referencia", nullable = false, length = 100)
    private String codigoReferencia;

    @Column(name = "contenido", nullable = false, columnDefinition = "text")
    private String contenido;

    @Column(name = "pool_destino", nullable = false, length = 150)
    private String poolDestino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoMensaje estado;

    // Cada mensaje pertenece a un proceso y la carga diferida evita traer el proceso completo
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;
}
