package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.NodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;

@Service
public class ConexionesService {

    private static final int SALIDAS_MINIMAS_DE_DIVERGENCIA = 2;
    private static final String SIN_SALIDAS = "' quedó sin arcos de salida";
    private static final String SIN_ENTRADAS = "' quedó sin arcos de entrada";
    private static final String MARCA_SALIDA = "SALIDA:";
    private static final String MARCA_ENTRADA = "ENTRADA:";
    private static final Pattern ESPACIOS_EN_BLANCO = Pattern.compile("\\s+");

    private ArcoRepository arcoRepository;
    private NodoFlujoResolver nodoFlujoResolver;

    @Autowired
    public ConexionesService(ArcoRepository arcoRepository, NodoFlujoResolver nodoFlujoResolver) {
        this.arcoRepository = arcoRepository;
        this.nodoFlujoResolver = nodoFlujoResolver;
    }

    @Transactional(readOnly = true)
    public List<Arco> salientesActivos(Long procesoId, TipoNodoFlujo tipo, Long nodoId) {
        return arcoRepository.findByProcesoIdAndOrigenTipoAndOrigenIdAndActivoTrueOrderByIdAsc(procesoId, tipo, nodoId);
    }

    @Transactional(readOnly = true)
    public List<Arco> entrantesActivos(Long procesoId, TipoNodoFlujo tipo, Long nodoId) {
        return arcoRepository.findByProcesoIdAndDestinoTipoAndDestinoIdAndActivoTrueOrderByIdAsc(procesoId, tipo,
                nodoId);
    }

    @Transactional(readOnly = true)
    public List<Arco> conectadosActivos(Proceso proceso, TipoNodoFlujo tipo, Long nodoId) {
        return arcoRepository.conectadosAlNodo(proceso.getId(), tipo, nodoId);
    }

    @Transactional
    public List<Arco> desactivarConectadosA(Proceso proceso, TipoNodoFlujo tipo, Long nodoId) {
        List<Arco> conectados = arcoRepository.conectadosAlNodo(proceso.getId(), tipo, nodoId);
        if (conectados.isEmpty()) {
            return conectados;
        }
        conectados.forEach(arco -> arco.setActivo(false));
        arcoRepository.saveAll(conectados);
        return conectados;
    }

    @Transactional
    public void guardarCambios(List<Arco> arcos) {
        if (!arcos.isEmpty()) {
            arcoRepository.saveAll(arcos);
        }
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasTrasDesactivar(Proceso proceso, List<Arco> desactivados,
            TipoNodoFlujo tipoExcluido, Long idExcluido) {
        return advertencias(proceso, desactivados, tipoExcluido, idExcluido, new HashSet<>());
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasSiSeElimina(Proceso proceso, Arco arco) {
        return advertencias(proceso, List.of(arco), null, null, idsDe(List.of(arco)));
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasSiSeEliminaNodo(Proceso proceso, TipoNodoFlujo tipo, Long nodoId,
            List<Arco> conectados) {
        return advertencias(proceso, conectados, tipo, nodoId, idsDe(conectados));
    }

    @Transactional(readOnly = true)
    public List<String> advertenciasDeGateway(Proceso proceso, NodoFlujo gateway) {
        List<Arco> salientes = salientesActivos(proceso.getId(), gateway.tipo(), gateway.id());
        List<String> advertencias = new ArrayList<>();
        if (salientes.size() < SALIDAS_MINIMAS_DE_DIVERGENCIA) {
            advertencias.add(gateway.nombre() + " tiene " + salientes.size()
                    + (salientes.size() == 1 ? " arco de salida" : " arcos de salida")
                    + ": como divergencia necesita al menos dos.");
        }
        if (gateway.exigeCondicion()) {
            agregarAdvertenciasDeCondiciones(gateway, salientes, advertencias);
        }
        return advertencias;
    }

    private void agregarAdvertenciasDeCondiciones(NodoFlujo gateway, List<Arco> salientes,
            List<String> advertencias) {
        if (salientes.stream().anyMatch(arco -> esVacio(arco.getCondicion()))) {
            advertencias.add(gateway.nombre()
                    + " tiene arcos de salida sin condición: un gateway exclusivo o inclusivo la exige en cada"
                    + " salida.");
        }
        if (gateway.tipoGateway() != TipoGateway.EXCLUSIVO) {
            return;
        }
        List<String> repetidas = condicionesRepetidas(salientes);
        for (String repetida : repetidas) {
            advertencias.add(gateway.nombre() + " repite la condición '" + repetida
                    + "' en más de una salida: esas condiciones no son mutuamente excluyentes.");
        }
        if (repetidas.isEmpty() && salientes.size() >= SALIDAS_MINIMAS_DE_DIVERGENCIA) {
            advertencias.add(gateway.nombre()
                    + ": revisa que las condiciones de sus salidas sean mutuamente excluyentes, porque solo una"
                    + " puede cumplirse.");
        }
    }

    private List<String> condicionesRepetidas(List<Arco> salientes) {
        Set<String> vistas = new LinkedHashSet<>();
        Set<String> repetidas = new LinkedHashSet<>();
        for (Arco arco : salientes) {
            String normalizada = normalizar(arco.getCondicion());
            if (normalizada != null && !vistas.add(normalizada)) {
                repetidas.add(arco.getCondicion().trim());
            }
        }
        return new ArrayList<>(repetidas);
    }

    private List<String> advertencias(Proceso proceso, List<Arco> arcos, TipoNodoFlujo tipoExcluido, Long idExcluido,
            Set<Long> arcosIgnorados) {
        List<String> advertencias = new ArrayList<>();
        Set<String> revisados = new LinkedHashSet<>();
        for (Arco arco : arcos) {
            revisar(proceso, arco.getOrigenTipo(), arco.getOrigenId(), true, tipoExcluido, idExcluido, arcosIgnorados,
                    revisados, advertencias);
            revisar(proceso, arco.getDestinoTipo(), arco.getDestinoId(), false, tipoExcluido, idExcluido,
                    arcosIgnorados, revisados, advertencias);
        }
        return advertencias;
    }

    private void revisar(Proceso proceso, TipoNodoFlujo tipo, Long nodoId, boolean salida,
            TipoNodoFlujo tipoExcluido, Long idExcluido, Set<Long> arcosIgnorados, Set<String> revisados,
            List<String> advertencias) {
        if (tipo == tipoExcluido && Objects.equals(nodoId, idExcluido)) {
            return;
        }
        String marca = (salida ? MARCA_SALIDA : MARCA_ENTRADA) + nodoFlujoResolver.clave(tipo, nodoId);
        if (!revisados.add(marca)) {
            return;
        }
        List<Arco> restantes = salida ? salientesActivos(proceso.getId(), tipo, nodoId)
                : entrantesActivos(proceso.getId(), tipo, nodoId);
        if (restantes.stream().anyMatch(arco -> !arcosIgnorados.contains(arco.getId()))) {
            return;
        }
        advertencias.add("'" + nodoFlujoResolver.describir(proceso, tipo, nodoId) + (salida ? SIN_SALIDAS
                : SIN_ENTRADAS));
    }

    private Set<Long> idsDe(List<Arco> arcos) {
        return arcos.stream().map(Arco::getId).collect(Collectors.toCollection(HashSet::new));
    }

    private boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private String normalizar(String condicion) {
        if (esVacio(condicion)) {
            return null;
        }
        return ESPACIOS_EN_BLANCO.matcher(condicion.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }
}
