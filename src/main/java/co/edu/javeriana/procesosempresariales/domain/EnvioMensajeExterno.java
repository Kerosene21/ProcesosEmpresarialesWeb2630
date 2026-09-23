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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;


 //entidad propia porque estos datos no pertenecen al proceso general ni a Message Throw.

@Entity
@Table(name = "envio_mensaje_externo")
@SQLRestriction("status = 0")
@SQLDelete(sql = "UPDATE envio_mensaje_externo SET status = 1 WHERE id = ?")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvioMensajeExterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_sistema_externo", nullable = false, length = 150)
    private String nombreSistemaExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destino", nullable = false, length = 30)
    private TipoDestinoExterno tipoDestino;

    @Column(name = "datos_enviados", nullable = false, columnDefinition = "text")
    private String datosEnviados;

    @Column(name = "momento_proceso", nullable = false, length = 150)
    private String momentoProceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "comportamiento_fallo", nullable = false, length = 30)
    private ComportamientoFallo comportamientoFallo;

    // 0 significa activo y 1 inactivo @SQLDelete conserva el historial
    @Column(nullable = false)
    private Integer status;

    // La relacion es lazy porque el envio solo necesita identificar al proceso propietario
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;
}
