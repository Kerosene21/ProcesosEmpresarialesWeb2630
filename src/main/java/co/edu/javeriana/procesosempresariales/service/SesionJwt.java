package co.edu.javeriana.procesosempresariales.service;

import java.time.Instant;

import co.edu.javeriana.procesosempresariales.dto.SesionDto;

public record SesionJwt(TokenAccesoEmitido acceso, SesionDto usuario, String codigoRefresco,
        Instant refrescoExpiraEn) {

    public boolean renuevaRefresco() {
        return codigoRefresco != null;
    }

    @Override
    public String toString() {
        return "SesionJwt[acceso=" + acceso + ", renuevaRefresco=" + renuevaRefresco() + ", refrescoExpiraEn="
                + refrescoExpiraEn + "]";
    }
}
