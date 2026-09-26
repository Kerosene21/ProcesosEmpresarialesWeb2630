package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.NodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.dto.NodoFlujoDto;
import co.edu.javeriana.procesosempresariales.exception.ArcoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.CondicionArcoNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.FlujoEntrePoolsException;
import co.edu.javeriana.procesosempresariales.exception.NodoFlujoNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.ArcoRepository;

@Service
public class ArcoService {

    static final String ARCO_NO_EXISTE = "El arco no existe en este proceso";
    static final String ARCO_ELIMINADO = "El arco ya fue eliminado";
    static final String ARCO_DUPLICADO = "Ya existe un arco activo entre ese origen y ese destino";
    static final String EXTREMOS_IGUALES = "El origen y el destino no pueden ser el mismo nodo";
    static final String CONDICION_OBLIGATORIA =
            "Un arco que sale de un gateway exclusivo o inclusivo necesita condición";
    static final String CONDICION_EN_PARALELO = "Un arco que sale de un gateway paralelo no lleva condición";
    static final String CONDICION_SIN_GATEWAY = "Solo los arcos que salen de un gateway llevan condición";
    static final String SIN_PERMISO_ESCRITURA = "Solo un administrador o editor puede crear o modificar arcos";
    static final String SIN_PERMISO_ELIMINAR = "Solo un administrador puede eliminar arcos";
    static final String ENTRE_POOLS = "' están en pools distintos: un flujo de secuencia no cruza pools;"
            + " entre pools solo se admiten flujos de mensaje";

    private ArcoRepository arcoRepository;
    private AccesoProcesoService accesoProcesoService;
    private HistorialProcesoService historialProcesoService;
    private NodoFlujoResolver nodoFlujoResolver;
    private ConexionesService conexionesService;
    private GeometriaArco geometriaArco;

    @Autowired
    public ArcoService(ArcoRepository arcoRepository, AccesoProcesoService accesoProcesoService,
            HistorialProcesoService historialProcesoService, NodoFlujoResolver nodoFlujoResolver,
            ConexionesService conexionesService, GeometriaArco geometriaArco) {
        this.arcoRepository = arcoRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.historialProcesoService = historialProcesoService;
        this.nodoFlujoResolver = nodoFlujoResolver;
        this.conexionesService = conexionesService;
        this.geometriaArco = geometriaArco;
    }

    @Transactional
    public ArcoRespuestaDto crear(Long procesoId, CrearArcoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);

        NodoFlujo origen = nodoFlujoResolver.resolverActivo(proceso, dto.getOrigenTipo(), dto.getOrigenId());
        NodoFlujo destino = nodoFlujoResolver.resolverActivo(proceso, dto.getDestinoTipo(), dto.getDestinoId());
        validarExtremosDistintos(origen, destino);
        validarMismoPool(origen, destino);
        validarQueNoEsteRepetido(proceso, origen, destino, null);

        Arco arco = new Arco();
        arco.setProceso(proceso);
        arco.setOrigenTipo(origen.tipo());
        arco.setOrigenId(origen.id());
        arco.setDestinoTipo(destino.tipo());
        arco.setDestinoId(destino.id());
        arco.setEtiqueta(textoONulo(dto.getEtiqueta()));
        arco.setCondicion(condicionValidada(origen, dto.getCondicion()));
        arco.setActivo(true);

        Arco guardado = arcoRepository.save(arco);
        historialProcesoService.registrar(proceso, usuario,
                "arco creado: " + descripcion(origen.nombre(), destino.nombre()));

        ArcoRespuestaDto respuesta = toDto(guardado, proceso);
        respuesta.setAdvertencias(advertenciasDeGateways(proceso, origen, null));
        return respuesta;
    }

    @Transactional(readOnly = true)
    public ArcoRespuestaDto obtener(Long procesoId, Long arcoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return toDto(arcoActivoDelProceso(arcoId, proceso), proceso);
    }

    @Transactional(readOnly = true)
    public List<ArcoRespuestaDto> consultarActivos(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        Map<String, NodoFlujo> nodos = nodoFlujoResolver.indiceDeNodosActivos(proceso);
        return arcoRepository.findByProcesoIdAndActivoTrueOrderByIdAsc(proceso.getId()).stream()
                .map(arco -> toDto(arco, proceso, nodos))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ArcoRespuestaDto> salientesDe(Long procesoId, TipoNodoFlujo tipo, Long nodoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        Map<String, NodoFlujo> nodos = nodoFlujoResolver.indiceDeNodosActivos(proceso);
        return conexionesService.salientesActivos(proceso.getId(), tipo, nodoId).stream()
                .map(arco -> toDto(arco, proceso, nodos))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NodoFlujoDto> nodosDelProceso(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoVisiblePara(procesoId, usuario);
        return nodoFlujoResolver.nodosActivos(proceso).stream()
                .map(nodo -> new NodoFlujoDto(nodo.tipo(), nodo.id(), nodo.nombre()))
                .toList();
    }

    @Transactional
    public ArcoRespuestaDto editar(Long procesoId, Long arcoId, EditarArcoDto dto, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolDeEscritura(usuario, SIN_PERMISO_ESCRITURA);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Arco arco = arcoActivoDelProceso(arcoId, proceso);

        NodoFlujo origen = nodoFlujoResolver.resolverActivo(proceso, dto.getOrigenTipo(), dto.getOrigenId());
        NodoFlujo destino = nodoFlujoResolver.resolverActivo(proceso, dto.getDestinoTipo(), dto.getDestinoId());
        validarExtremosDistintos(origen, destino);
        validarMismoPool(origen, destino);
        validarQueNoEsteRepetido(proceso, origen, destino, arco.getId());

        String etiquetaNueva = textoONulo(dto.getEtiqueta());
        String condicionNueva = condicionValidada(origen, dto.getCondicion());
        NodoFlujo origenAnterior = nodoFlujoResolver.buscar(proceso, arco.getOrigenTipo(), arco.getOrigenId())
                .orElse(null);
        String cambios = construirCambios(proceso, arco, origen, destino, etiquetaNueva, condicionNueva);
        if (cambios.isEmpty()) {
            return toDto(arco, proceso);
        }

        String resumen = "arco " + descripcionActual(proceso, arco) + ": " + cambios;
        arco.setOrigenTipo(origen.tipo());
        arco.setOrigenId(origen.id());
        arco.setDestinoTipo(destino.tipo());
        arco.setDestinoId(destino.id());
        arco.setEtiqueta(etiquetaNueva);
        arco.setCondicion(condicionNueva);
        arcoRepository.save(arco);

        historialProcesoService.registrar(proceso, usuario, resumen);

        ArcoRespuestaDto respuesta = toDto(arco, proceso);
        respuesta.setAdvertencias(advertenciasDeGateways(proceso, origen, origenAnterior));
        return respuesta;
    }

    @Transactional(readOnly = true)
    public ArcoRespuestaDto obtenerParaEliminar(Long procesoId, Long arcoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Arco arco = arcoActivoDelProceso(arcoId, proceso);

        ArcoRespuestaDto respuesta = toDto(arco, proceso);
        respuesta.setAdvertencias(conexionesService.advertenciasSiSeElimina(proceso, arco));
        return respuesta;
    }

    @Transactional
    public ArcoRespuestaDto eliminar(Long procesoId, Long arcoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO_ELIMINAR);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        Arco arco = arcoActivoDelProceso(arcoId, proceso);

        String descripcion = descripcionActual(proceso, arco);
        arco.setActivo(false);
        arcoRepository.save(arco);

        historialProcesoService.registrar(proceso, usuario, "arco eliminado: " + descripcion);

        ArcoRespuestaDto respuesta = toDto(arco, proceso);
        respuesta.setAdvertencias(conexionesService.advertenciasTrasDesactivar(proceso, List.of(arco), null, null));
        return respuesta;
    }

    private Arco arcoActivoDelProceso(Long arcoId, Proceso proceso) {
        Arco arco = arcoRepository.findByIdAndProcesoId(arcoId, proceso.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException(ARCO_NO_EXISTE));
        if (!arco.isActivo()) {
            throw new RecursoNoEncontradoException(ARCO_ELIMINADO);
        }
        return arco;
    }

    private void validarExtremosDistintos(NodoFlujo origen, NodoFlujo destino) {
        if (origen.tipo() == destino.tipo() && Objects.equals(origen.id(), destino.id())) {
            throw new NodoFlujoNoValidoException(EXTREMOS_IGUALES);
        }
    }

    private void validarMismoPool(NodoFlujo origen, NodoFlujo destino) {
        if (!origen.mismoPool(destino)) {
            throw new FlujoEntrePoolsException("'" + origen.nombre() + "' y '" + destino.nombre() + ENTRE_POOLS);
        }
    }

    private void validarQueNoEsteRepetido(Proceso proceso, NodoFlujo origen, NodoFlujo destino, Long arcoId) {
        boolean repetido = arcoId == null
                ? arcoRepository.existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrue(
                        proceso.getId(), origen.tipo(), origen.id(), destino.tipo(), destino.id())
                : arcoRepository
                        .existsByProcesoIdAndOrigenTipoAndOrigenIdAndDestinoTipoAndDestinoIdAndActivoTrueAndIdNot(
                                proceso.getId(), origen.tipo(), origen.id(), destino.tipo(), destino.id(), arcoId);
        if (repetido) {
            throw new ArcoDuplicadoException(ARCO_DUPLICADO);
        }
    }

    private String condicionValidada(NodoFlujo origen, String condicion) {
        String texto = textoONulo(condicion);
        if (origen.exigeCondicion()) {
            if (texto == null) {
                throw new CondicionArcoNoValidaException(CONDICION_OBLIGATORIA);
            }
            return texto;
        }
        if (texto != null) {
            throw new CondicionArcoNoValidaException(
                    origen.esGateway() ? CONDICION_EN_PARALELO : CONDICION_SIN_GATEWAY);
        }
        return null;
    }

    private List<String> advertenciasDeGateways(Proceso proceso, NodoFlujo origen, NodoFlujo origenAnterior) {
        List<String> advertencias = new ArrayList<>();
        if (origen.esGateway()) {
            advertencias.addAll(conexionesService.advertenciasDeGateway(proceso, origen));
        }
        if (origenAnterior != null && origenAnterior.esGateway() && !mismoNodo(origenAnterior, origen)) {
            advertencias.addAll(conexionesService.advertenciasDeGateway(proceso, origenAnterior));
        }
        return advertencias;
    }

    private boolean mismoNodo(NodoFlujo uno, NodoFlujo otro) {
        return uno.tipo() == otro.tipo() && Objects.equals(uno.id(), otro.id());
    }

    private String construirCambios(Proceso proceso, Arco arco, NodoFlujo origen, NodoFlujo destino,
            String etiquetaNueva, String condicionNueva) {
        List<String> cambios = new ArrayList<>();
        if (arco.getOrigenTipo() != origen.tipo() || !Objects.equals(arco.getOrigenId(), origen.id())) {
            cambios.add("origen: '" + nodoFlujoResolver.describir(proceso, arco.getOrigenTipo(), arco.getOrigenId())
                    + "' -> '" + origen.nombre() + "'");
        }
        if (arco.getDestinoTipo() != destino.tipo() || !Objects.equals(arco.getDestinoId(), destino.id())) {
            cambios.add("destino: '" + nodoFlujoResolver.describir(proceso, arco.getDestinoTipo(), arco.getDestinoId())
                    + "' -> '" + destino.nombre() + "'");
        }
        agregarCambio(cambios, "etiqueta", arco.getEtiqueta(), etiquetaNueva);
        agregarCambio(cambios, "condicion", arco.getCondicion(), condicionNueva);
        return String.join("; ", cambios);
    }

    private void agregarCambio(List<String> cambios, String campo, String anterior, String nuevo) {
        if (!Objects.equals(anterior, nuevo)) {
            cambios.add(campo + ": '" + textoVisible(anterior) + "' -> '" + textoVisible(nuevo) + "'");
        }
    }

    private String textoVisible(String texto) {
        return texto == null ? "" : texto;
    }

    private String textoONulo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.trim();
    }

    private String descripcion(String origen, String destino) {
        return "'" + origen + "' -> '" + destino + "'";
    }

    private String descripcionActual(Proceso proceso, Arco arco) {
        return descripcion(nodoFlujoResolver.describir(proceso, arco.getOrigenTipo(), arco.getOrigenId()),
                nodoFlujoResolver.describir(proceso, arco.getDestinoTipo(), arco.getDestinoId()));
    }

    private ArcoRespuestaDto toDto(Arco arco, Proceso proceso) {
        return toDto(arco, proceso, nodoFlujoResolver.indiceDeNodosActivos(proceso));
    }

    private ArcoRespuestaDto toDto(Arco arco, Proceso proceso, Map<String, NodoFlujo> nodos) {
        ArcoRespuestaDto respuesta = new ArcoRespuestaDto();
        respuesta.setId(arco.getId());
        respuesta.setProcesoId(proceso.getId());
        respuesta.setOrigenTipo(arco.getOrigenTipo());
        respuesta.setOrigenId(arco.getOrigenId());
        respuesta.setDestinoTipo(arco.getDestinoTipo());
        respuesta.setDestinoId(arco.getDestinoId());
        respuesta.setEtiqueta(arco.getEtiqueta());
        respuesta.setCondicion(arco.getCondicion());
        respuesta.setActivo(arco.isActivo());

        Optional<NodoFlujo> origen = buscarEnIndice(proceso, nodos, arco.getOrigenTipo(), arco.getOrigenId());
        Optional<NodoFlujo> destino = buscarEnIndice(proceso, nodos, arco.getDestinoTipo(), arco.getDestinoId());
        respuesta.setOrigenNombre(origen.map(NodoFlujo::nombre)
                .orElseGet(() -> nodoFlujoResolver.referencia(arco.getOrigenTipo(), arco.getOrigenId())));
        respuesta.setDestinoNombre(destino.map(NodoFlujo::nombre)
                .orElseGet(() -> nodoFlujoResolver.referencia(arco.getDestinoTipo(), arco.getDestinoId())));
        if (origen.isPresent() && destino.isPresent()) {
            ubicarExtremos(respuesta, origen.get(), destino.get());
        }
        return respuesta;
    }

    private Optional<NodoFlujo> buscarEnIndice(Proceso proceso, Map<String, NodoFlujo> nodos, TipoNodoFlujo tipo,
            Long nodoId) {
        NodoFlujo nodo = nodos.get(nodoFlujoResolver.clave(tipo, nodoId));
        if (nodo != null) {
            return Optional.of(nodo);
        }
        return nodoFlujoResolver.buscar(proceso, tipo, nodoId);
    }

    private void ubicarExtremos(ArcoRespuestaDto respuesta, NodoFlujo origen, NodoFlujo destino) {
        PuntoDiagrama centroOrigen = geometriaArco.centro(origen.tipo(), origen.posicionX(),
                origen.posicionY());
        PuntoDiagrama centroDestino = geometriaArco.centro(destino.tipo(), destino.posicionX(),
                destino.posicionY());
        PuntoDiagrama llegada = geometriaArco.llegada(centroOrigen, centroDestino, destino.tipo());
        respuesta.setOrigenX(centroOrigen.x());
        respuesta.setOrigenY(centroOrigen.y());
        respuesta.setDestinoX(llegada.x());
        respuesta.setDestinoY(llegada.y());
    }
}
