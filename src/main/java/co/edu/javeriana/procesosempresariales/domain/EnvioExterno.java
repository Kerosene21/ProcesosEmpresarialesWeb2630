package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("ENVIO_EXTERNO")
@Getter @Setter @NoArgsConstructor
public class EnvioExterno extends EventoEmisor {
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_destino", length = 20)
    private TipoDestinoExterno tipoDestino;

    @Column(name = "datos_enviados", length = 2000)
    private String datosEnviados;

    @Column(name = "momento_proceso", length = 300)
    private String momentoProceso;

    @Enumerated(EnumType.STRING)
    @Column(name = "comportamiento_fallo", length = 20)
    private ComportamientoFallo comportamientoFallo;

    @Override
    public TipoEvento getTipo() {
        return TipoEvento.ENVIO_EXTERNO;
    }
}
