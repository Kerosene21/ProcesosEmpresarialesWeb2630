package co.edu.javeriana.procesosempresariales.service;

import java.time.Duration;
import java.time.Instant;

public record TokenAccesoEmitido(String valor, Instant emitidoEn, Instant expiraEn) {

    public long duracionEnSegundos() {
        return Duration.between(emitidoEn, expiraEn).toSeconds();
    }

    @Override
    public String toString() {
        return "TokenAccesoEmitido[emitidoEn=" + emitidoEn + ", expiraEn=" + expiraEn + "]";
    }
}
