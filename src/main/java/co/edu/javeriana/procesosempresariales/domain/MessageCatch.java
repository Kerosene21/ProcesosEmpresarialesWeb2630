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
@DiscriminatorValue("MESSAGE_CATCH")
@Getter @Setter @NoArgsConstructor
public class MessageCatch extends Evento {
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VarianteMessageCatch variante;

    @Column(name = "datos_esperados", length = 2000)
    private String datosEsperados;

    @Column(name = "actividades_uso", length = 1000)
    private String actividadesUso;

    @Column(name = "origen_externo")
    private Boolean origenExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "comportamiento_sin_caso", length = 20)
    private ComportamientoSinCaso comportamientoSinCaso;

    @Override
    public TipoEvento getTipo() {
        return TipoEvento.MESSAGE_CATCH;
    }

    @Override
    public boolean admiteEntradas() {
        return variante != VarianteMessageCatch.INICIO;
    }

    public boolean esOrigenExterno() {
        return Boolean.TRUE.equals(origenExterno);
    }
}
