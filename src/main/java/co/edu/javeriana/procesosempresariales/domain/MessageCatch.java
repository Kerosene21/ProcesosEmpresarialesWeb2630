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


//modela una espera o un inicio por mensaje sin ejecutar integraciones guardando la configuracion de un evento Message Catch

@Entity
@Table(name = "message_catch")
@SQLRestriction("status = 0")
@SQLDelete(sql = "UPDATE message_catch SET status = 1 WHERE id = ?")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageCatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_mensaje", nullable = false, length = 150)
    private String nombreMensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private VarianteMessageCatch variante;

    @Column(name = "datos_esperados", nullable = false, columnDefinition = "text")
    private String datosEsperados;

    @Column(name = "actividades_uso", nullable = false, columnDefinition = "text")
    private String actividadesUso;

    @Column(name = "origen_externo", nullable = false)
    private boolean origenExterno;

    // 0 es activo y 1 inactivo; @SQLDelete cambia el estado en lugar de borrar.
    @Column(nullable = false)
    private Integer status;

    // El catch pertenece a un proceso
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proceso_id", nullable = false)
    private Proceso proceso;
}
