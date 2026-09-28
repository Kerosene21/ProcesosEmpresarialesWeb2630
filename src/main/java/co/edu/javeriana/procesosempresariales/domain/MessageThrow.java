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
@DiscriminatorValue("MESSAGE_THROW")
@Getter @Setter @NoArgsConstructor
public class MessageThrow extends EventoEmisor {
    @Column(length = 2000)
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(name = "comportamiento_sin_caso", length = 20)
    private ComportamientoSinCaso comportamientoSinCaso;

    @Override
    public TipoEvento getTipo() {
        return TipoEvento.MESSAGE_THROW;
    }
}
