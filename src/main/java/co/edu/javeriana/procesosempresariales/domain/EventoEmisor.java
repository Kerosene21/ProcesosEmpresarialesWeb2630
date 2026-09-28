package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
public abstract class EventoEmisor extends Evento {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pool_destino_id")
    private Pool poolDestino;
}
