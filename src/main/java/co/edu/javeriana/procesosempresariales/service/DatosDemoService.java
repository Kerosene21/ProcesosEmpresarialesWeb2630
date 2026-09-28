package co.edu.javeriana.procesosempresariales.service;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearEnvioExternoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.CrearLaneDto;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageCatchDto;
import co.edu.javeriana.procesosempresariales.dto.CrearMessageThrowDto;
import co.edu.javeriana.procesosempresariales.dto.CrearPoolDto;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;

@Service
public class DatosDemoService {

    private static final String CORREO_ADMINISTRADOR = "admin.demo@example.com";
    private static final String CORREO_EDITOR = "editor.demo@example.com";
    private static final String CORREO_LECTURA = "lectura.demo@example.com";
    private static final String NIT = "900100200-1";
    private static final String EMPRESA = "Empresa Demo";
    private static final String MENSAJE_SOLICITUD = "Solicitud de servicio";
    private static final String CLAVE_CORRELACION = "numeroSolicitud";
    private static final String DATASET_INCONSISTENTE = "El NIT demo " + NIT + " ya está registrado pero el "
            + "administrador demo no existe: el dataset demo es inconsistente y no se repara automáticamente";
    private static final String SIN_LANE_INICIAL = "El proceso demo no tiene su lane inicial";

    private UsuarioService usuarioService;
    private EmpresaService empresaService;
    private RolProcesoService rolProcesoService;
    private ProcesoService procesoService;
    private PermisoEstructuraService permisoEstructuraService;
    private LaneService laneService;
    private PoolService poolService;
    private ActividadService actividadService;
    private GatewayService gatewayService;
    private MessageCatchService messageCatchService;
    private MessageThrowService messageThrowService;
    private EnvioExternoService envioExternoService;
    private ArcoService arcoService;
    private Validator validator;

    @Autowired
    public DatosDemoService(UsuarioService usuarioService, EmpresaService empresaService,
            RolProcesoService rolProcesoService, ProcesoService procesoService,
            PermisoEstructuraService permisoEstructuraService, LaneService laneService, PoolService poolService,
            ActividadService actividadService, GatewayService gatewayService,
            MessageCatchService messageCatchService, MessageThrowService messageThrowService,
            EnvioExternoService envioExternoService, ArcoService arcoService, Validator validator) {
        this.usuarioService = usuarioService;
        this.empresaService = empresaService;
        this.rolProcesoService = rolProcesoService;
        this.procesoService = procesoService;
        this.permisoEstructuraService = permisoEstructuraService;
        this.laneService = laneService;
        this.poolService = poolService;
        this.actividadService = actividadService;
        this.gatewayService = gatewayService;
        this.messageCatchService = messageCatchService;
        this.messageThrowService = messageThrowService;
        this.envioExternoService = envioExternoService;
        this.arcoService = arcoService;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public boolean estaCargado() {
        return usuarioService.existeUsuarioConCorreo(CORREO_ADMINISTRADOR);
    }

    @Transactional
    public boolean cargar(String passwordAdministrador, String passwordUsuarios) {
        if (estaCargado()) {
            return false;
        }
        RegistroEmpresaDto empresa = validado(
                new RegistroEmpresaDto(EMPRESA, NIT, CORREO_ADMINISTRADOR, passwordAdministrador));
        CrearUsuarioDto editor = validado(new CrearUsuarioDto(CORREO_EDITOR, passwordUsuarios, RolUsuario.EDITOR));
        CrearUsuarioDto lectura = validado(
                new CrearUsuarioDto(CORREO_LECTURA, passwordUsuarios, RolUsuario.SOLO_LECTURA));

        registrarEmpresa(empresa);
        usuarioService.crear(editor, CORREO_ADMINISTRADOR);
        usuarioService.crear(lectura, CORREO_ADMINISTRADOR);
        crearRol("Analista", "Registra y evalúa las solicitudes de servicio de los clientes");
        Long supervisor = crearRol("Supervisor", "Aprueba las solicitudes que el analista evaluó");
        ProcesoRespuestaDto proceso = procesoService.crear(validado(new CrearProcesoDto("Atención de solicitudes",
                "Recepción, evaluación y respuesta de las solicitudes de servicio de los clientes",
                "Servicio al cliente")), CORREO_ADMINISTRADOR);
        permisoEstructuraService.configurar(proceso.getId(), RolUsuario.EDITOR,
                validado(new ConfigurarPermisoEstructuraDto(false, true, false, true, true, false)),
                CORREO_ADMINISTRADOR);
        crearModelo(proceso.getId(), proceso.getPoolId(), supervisor);
        return true;
    }

    private void crearModelo(Long procesoId, Long propietario, Long rolSupervisor) {
        Long general = laneInicial(procesoId, propietario);
        Long supervisor = laneService.crear(procesoId, propietario, validado(new CrearLaneDto(rolSupervisor, null)),
                CORREO_ADMINISTRADOR).getId();
        Long cliente = crearPool(procesoId, "Cliente", false);
        Long servicioCorreo = crearPool(procesoId, "Servicio de correo", true);

        Long registrar = crearActividad(procesoId, "Registrar solicitud", general, 160, 60);
        Long evaluar = crearActividad(procesoId, "Evaluar solicitud", general, 340, 60);
        Long aprobar = crearActividad(procesoId, "Aprobar solicitud", supervisor, 620, 200);
        Long gateway = crearGatewayDeAprobacion(procesoId, propietario);
        Long solicitud = crearCatchDeSolicitud(procesoId, propietario);
        crearThrowDeSolicitud(procesoId, cliente, propietario);
        Long notificacion = crearNotificacion(procesoId, propietario, servicioCorreo);

        conectar(procesoId, TipoNodoFlujo.EVENTO, solicitud, TipoNodoFlujo.ACTIVIDAD, registrar, null);
        conectar(procesoId, TipoNodoFlujo.ACTIVIDAD, registrar, TipoNodoFlujo.ACTIVIDAD, evaluar, null);
        conectar(procesoId, TipoNodoFlujo.ACTIVIDAD, evaluar, TipoNodoFlujo.GATEWAY, gateway, null);
        conectar(procesoId, TipoNodoFlujo.GATEWAY, gateway, TipoNodoFlujo.ACTIVIDAD, aprobar, "Aprobada");
        conectar(procesoId, TipoNodoFlujo.GATEWAY, gateway, TipoNodoFlujo.EVENTO, notificacion, "Rechazada");
        conectar(procesoId, TipoNodoFlujo.ACTIVIDAD, aprobar, TipoNodoFlujo.EVENTO, notificacion, null);
    }

    private void registrarEmpresa(RegistroEmpresaDto empresa) {
        try {
            empresaService.registrar(empresa);
        } catch (NitEmpresaDuplicadoException exception) {
            throw new IllegalStateException(DATASET_INCONSISTENTE, exception);
        }
    }

    private Long crearRol(String nombre, String descripcion) {
        return rolProcesoService.crear(validado(new CrearRolProcesoDto(nombre, descripcion)), CORREO_ADMINISTRADOR)
                .getId();
    }

    private Long laneInicial(Long procesoId, Long propietario) {
        return laneService.listar(procesoId, propietario, CORREO_ADMINISTRADOR).stream()
                .filter(lane -> lane.getRolProcesoId() == null)
                .map(LaneRespuestaDto::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(SIN_LANE_INICIAL));
    }

    private Long crearPool(Long procesoId, String nombre, boolean cajaNegra) {
        return poolService.crear(procesoId, validado(new CrearPoolDto(nombre, TipoPool.EXTERNO, cajaNegra, null)),
                CORREO_ADMINISTRADOR).getId();
    }

    private Long crearActividad(Long procesoId, String nombre, Long laneId, int posicionX, int posicionY) {
        return actividadService.crear(procesoId, validado(new CrearActividadDto(nombre, TipoActividad.TAREA_USUARIO,
                laneId, posicionX, posicionY)), CORREO_ADMINISTRADOR).getId();
    }

    private Long crearGatewayDeAprobacion(Long procesoId, Long propietario) {
        return gatewayService.crear(procesoId,
                validado(new CrearGatewayDto(TipoGateway.EXCLUSIVO, 520, 70, propietario)), CORREO_ADMINISTRADOR)
                .getId();
    }

    private Long crearCatchDeSolicitud(Long procesoId, Long propietario) {
        return messageCatchService.crear(procesoId, validado(new CrearMessageCatchDto(MENSAJE_SOLICITUD,
                VarianteMessageCatch.INICIO, "Número de solicitud, datos del cliente y servicio solicitado",
                "Registrar solicitud", false, CLAVE_CORRELACION, ComportamientoSinCaso.INICIAR_NUEVO_CASO,
                propietario, 60, 70)), CORREO_ADMINISTRADOR).getId();
    }

    private void crearThrowDeSolicitud(Long procesoId, Long cliente, Long propietario) {
        messageThrowService.crear(procesoId, validado(new CrearMessageThrowDto(MENSAJE_SOLICITUD,
                "Datos de la solicitud de servicio del cliente", cliente, propietario, CLAVE_CORRELACION,
                ComportamientoSinCaso.INICIAR_NUEVO_CASO, 60, 380)), CORREO_ADMINISTRADOR);
    }

    private Long crearNotificacion(Long procesoId, Long propietario, Long servicioCorreo) {
        return envioExternoService.crear(procesoId, validado(new CrearEnvioExternoDto("Notificación de resultado",
                propietario, servicioCorreo, TipoDestinoExterno.CORREO,
                "Número de solicitud y resultado de la evaluación", "Al cerrar la solicitud, aprobada o rechazada",
                ComportamientoFallo.CONTINUAR, CLAVE_CORRELACION, 820, 70)), CORREO_ADMINISTRADOR).getId();
    }

    private void conectar(Long procesoId, TipoNodoFlujo origenTipo, Long origenId, TipoNodoFlujo destinoTipo,
            Long destinoId, String condicion) {
        arcoService.crear(procesoId,
                validado(new CrearArcoDto(origenTipo, origenId, destinoTipo, destinoId, null, condicion)),
                CORREO_ADMINISTRADOR);
    }

    private <T> T validado(T dto) {
        Set<ConstraintViolation<T>> violaciones = validator.validate(dto);
        if (!violaciones.isEmpty()) {
            throw new ConstraintViolationException(violaciones);
        }
        return dto;
    }
}
