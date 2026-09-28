package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Evento;
import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.MessageThrow;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.repository.MessageCatchRepository;
import co.edu.javeriana.procesosempresariales.repository.MessageThrowRepository;

@Service
public class CorrelacionMensajeService {

    private static final Pattern ESPACIOS_EN_BLANCO = Pattern.compile("\\s+");
    private static final String DEL_POOL = "' del pool '";

    private MessageThrowRepository messageThrowRepository;
    private MessageCatchRepository messageCatchRepository;

    @Autowired
    public CorrelacionMensajeService(MessageThrowRepository messageThrowRepository,
            MessageCatchRepository messageCatchRepository) {
        this.messageThrowRepository = messageThrowRepository;
        this.messageCatchRepository = messageCatchRepository;
    }

    @Transactional(readOnly = true)
    public AnalisisCorrelacion analizarThrow(MessageThrow messageThrow) {
        Long procesoId = messageThrow.getProceso().getId();
        List<MessageCatch> homologos = catchesActivos(procesoId).stream()
                .filter(messageCatch -> sonHomologos(messageThrow, messageCatch))
                .toList();
        MessageCatch coherente = homologos.stream()
                .filter(messageCatch -> mismaClave(messageThrow, messageCatch))
                .findFirst()
                .orElse(null);

        List<String> advertencias = new ArrayList<>();
        if (homologos.isEmpty()) {
            advertencias.add("No existe un Message Catch '" + messageThrow.getNombreMensaje() + "' en el pool destino '"
                    + messageThrow.getPoolDestino().getNombre() + "': el mensaje no tiene un receptor modelado.");
        } else if (coherente == null) {
            MessageCatch primero = homologos.get(0);
            advertencias.add(claveIncoherente(messageThrow, primero));
        } else {
            advertencias.addAll(comportamientoIncoherente(messageThrow, coherente));
        }
        advertencias.addAll(ambiguedad(messageThrow, throwsActivos(procesoId)));
        return new AnalisisCorrelacion(coherente == null ? null : coherente.getId(), advertencias);
    }

    @Transactional(readOnly = true)
    public AnalisisCorrelacion analizarCatch(MessageCatch messageCatch) {
        Long procesoId = messageCatch.getProceso().getId();
        List<MessageThrow> homologos = throwsActivos(procesoId).stream()
                .filter(messageThrow -> sonHomologos(messageThrow, messageCatch))
                .toList();
        MessageThrow coherente = homologos.stream()
                .filter(messageThrow -> mismaClave(messageThrow, messageCatch))
                .findFirst()
                .orElse(null);

        List<String> advertencias = new ArrayList<>();
        if (!messageCatch.esOrigenExterno()) {
            if (homologos.isEmpty()) {
                advertencias.add("No existe un Message Throw '" + messageCatch.getNombreMensaje()
                        + "' dirigido al pool '" + messageCatch.getPool().getNombre()
                        + "': indica el Throw homólogo o declara el origen como externo.");
            } else if (coherente == null) {
                advertencias.add(claveIncoherente(homologos.get(0), messageCatch));
            }
        }
        if (coherente != null) {
            advertencias.addAll(comportamientoIncoherente(coherente, messageCatch));
        }
        if (messageCatch.getVariante() == VarianteMessageCatch.INTERMEDIO) {
            advertencias.addAll(documentacionDelCatchIntermedio(messageCatch));
        }
        advertencias.addAll(ambiguedad(messageCatch, catchesActivos(procesoId)));
        return new AnalisisCorrelacion(coherente == null ? null : coherente.getId(), advertencias);
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasSiSeEliminaThrow(MessageThrow messageThrow) {
        Long procesoId = messageThrow.getProceso().getId();
        List<MessageThrow> restantes = throwsActivos(procesoId).stream()
                .filter(otro -> !otro.getId().equals(messageThrow.getId()))
                .toList();
        return catchesActivos(procesoId).stream()
                .filter(messageCatch -> !messageCatch.esOrigenExterno() && sonHomologos(messageThrow, messageCatch))
                .filter(messageCatch -> restantes.stream().noneMatch(otro -> sonHomologos(otro, messageCatch)))
                .map(messageCatch -> "El Message Catch '" + messageCatch.getNombreMensaje() + DEL_POOL
                        + messageCatch.getPool().getNombre() + "' quedará sin Message Throw homólogo.")
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasSiSeEliminaCatch(MessageCatch messageCatch) {
        Long procesoId = messageCatch.getProceso().getId();
        List<MessageCatch> restantes = catchesActivos(procesoId).stream()
                .filter(otro -> !otro.getId().equals(messageCatch.getId()))
                .toList();
        return throwsActivos(procesoId).stream()
                .filter(messageThrow -> sonHomologos(messageThrow, messageCatch))
                .filter(messageThrow -> restantes.stream().noneMatch(otro -> sonHomologos(messageThrow, otro)))
                .map(messageThrow -> "El Message Throw '" + messageThrow.getNombreMensaje()
                        + "' quedará sin Message Catch homólogo en el pool '"
                        + messageThrow.getPoolDestino().getNombre() + "'.")
                .toList();
    }

    private List<MessageThrow> throwsActivos(Long procesoId) {
        return messageThrowRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(procesoId);
    }

    private List<MessageCatch> catchesActivos(Long procesoId) {
        return messageCatchRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(procesoId);
    }

    private boolean sonHomologos(MessageThrow messageThrow, MessageCatch messageCatch) {
        return messageThrow.getPoolDestino().getId().equals(messageCatch.getPool().getId())
                && mismoNombre(messageThrow, messageCatch);
    }

    private boolean mismoNombre(Evento uno, Evento otro) {
        return Objects.equals(normalizar(uno.getNombreMensaje()), normalizar(otro.getNombreMensaje()));
    }

    private boolean mismaClave(Evento uno, Evento otro) {
        return Objects.equals(normalizar(uno.getClaveCorrelacion()), normalizar(otro.getClaveCorrelacion()));
    }

    private String claveIncoherente(MessageThrow messageThrow, MessageCatch messageCatch) {
        return "Clave de correlación incoherente entre el Message Throw '" + messageThrow.getNombreMensaje()
                + "' y el Message Catch '" + messageCatch.getNombreMensaje() + DEL_POOL
                + messageCatch.getPool().getNombre() + "': el Throw usa " + valorDeClave(messageThrow)
                + " y el Catch usa " + valorDeClave(messageCatch) + ".";
    }

    private List<String> comportamientoIncoherente(MessageThrow messageThrow, MessageCatch messageCatch) {
        if (messageThrow.getComportamientoSinCaso() == null || messageCatch.getComportamientoSinCaso() == null
                || messageThrow.getComportamientoSinCaso() == messageCatch.getComportamientoSinCaso()) {
            return List.of();
        }
        return List.of("El Message Throw '" + messageThrow.getNombreMensaje() + "' documenta "
                + messageThrow.getComportamientoSinCaso() + " para mensajes sin caso en espera y su Message Catch"
                + " homólogo documenta " + messageCatch.getComportamientoSinCaso() + ".");
    }

    private List<String> documentacionDelCatchIntermedio(MessageCatch messageCatch) {
        List<String> advertencias = new ArrayList<>();
        if (normalizar(messageCatch.getClaveCorrelacion()) == null) {
            advertencias.add("El Message Catch intermedio '" + messageCatch.getNombreMensaje()
                    + "' no declara clave de correlación: no se puede identificar el caso en espera que recibe el"
                    + " mensaje.");
        }
        if (messageCatch.getComportamientoSinCaso() == null) {
            advertencias.add("El Message Catch intermedio '" + messageCatch.getNombreMensaje()
                    + "' no documenta qué ocurre si llega un mensaje sin caso en espera.");
        }
        return advertencias;
    }

    private List<String> ambiguedad(Evento mensaje, List<? extends Evento> delMismoTipo) {
        long repetidos = delMismoTipo.stream()
                .filter(otro -> !otro.getId().equals(mensaje.getId()))
                .filter(otro -> mismoNombre(otro, mensaje) && mismaClave(otro, mensaje))
                .count();
        if (repetidos == 0) {
            return List.of();
        }
        String tipo = mensaje.getTipo().getDescripcion();
        String sujeto = repetidos == 1 ? "otro " + tipo + " del proceso comparte"
                : "otros " + repetidos + " " + tipo + " del proceso comparten";
        return List.of("Ambigüedad de correlación: " + sujeto + " el nombre '" + mensaje.getNombreMensaje() + "' "
                + (normalizar(mensaje.getClaveCorrelacion()) == null ? "sin clave de correlación"
                        : "y la clave de correlación '" + mensaje.getClaveCorrelacion() + "'")
                + ".");
    }

    private String valorDeClave(Evento mensaje) {
        return mensaje.getClaveCorrelacion() == null ? "ninguna" : "'" + mensaje.getClaveCorrelacion() + "'";
    }

    private String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return ESPACIOS_EN_BLANCO.matcher(texto.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
