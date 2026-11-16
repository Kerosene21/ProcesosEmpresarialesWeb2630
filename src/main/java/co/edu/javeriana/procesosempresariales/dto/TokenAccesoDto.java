package co.edu.javeriana.procesosempresariales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Token de acceso JWT y datos actuales del usuario. El refresh token viaja solo en la cookie "
        + "HttpOnly `PE_REFRESH`.")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class TokenAccesoDto {
    @Schema(description = "JWT para enviar en `Authorization: Bearer`.")
    private String tokenAcceso;

    @Schema(example = "Bearer")
    private String tipoToken;

    @Schema(description = "Vigencia del token de acceso en segundos, contada desde su emisión.", example = "900")
    private long expiraEnSegundos;

    private SesionDto usuario;

    @Override
    public String toString() {
        return "TokenAccesoDto[tipoToken=" + tipoToken + ", expiraEnSegundos=" + expiraEnSegundos + "]";
    }
}
