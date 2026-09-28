package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

import java.util.List;
import java.util.UUID;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.config.InicializadorDatosDemo;
import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.AlcanceProceso;
import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.PermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.PoolRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.AccesoProcesoService;
import co.edu.javeriana.procesosempresariales.service.DatosDemoService;
import co.edu.javeriana.procesosempresariales.service.DiagramaProcesoService;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.PermisoEstructuraService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;
import co.edu.javeriana.procesosempresariales.service.ValidacionModeloService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DatosDemoIntegracionTest {

    private static final String ADMIN = "admin.demo@example.com";
    private static final String EDITOR = "editor.demo@example.com";
    private static final String LECTURA = "lectura.demo@example.com";
    private static final String NIT_DEMO = "900100200-1";
    private static final String NIT_CONFLICTO = "905900002-2";
    private static final String CLAVE_CORRELACION = "numeroSolicitud";

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatosDemoService datosDemoService;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RolProcesoService rolProcesoService;

    @Autowired
    private ProcesoService procesoService;

    @Autowired
    private DiagramaProcesoService diagramaProcesoService;

    @Autowired
    private PermisoEstructuraService permisoEstructuraService;

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private AccesoProcesoService accesoProcesoService;

    @Autowired
    private ValidacionModeloService validacionModeloService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String passwordAdministrador = UUID.randomUUID().toString();
    private final String passwordUsuarios = UUID.randomUUID().toString();

    private boolean cargar() {
        return datosDemoService.cargar(passwordAdministrador, passwordUsuarios);
    }

    private Long procesoDemo() {
        return procesoService.consultarProcesos(new FiltroProcesosDto(), ADMIN).getContent().get(0).getId();
    }

    private Long empresaDe(String correo) {
        return usuarioService.usuarioAutenticado(correo).getEmpresa().getId();
    }

    private DiagramaProcesoDto diagramaDemo() {
        return diagramaProcesoService.obtener(procesoDemo(), ADMIN);
    }

    private Long idDelPool(DiagramaProcesoDto diagrama, String nombre) {
        return diagrama.getPools().stream()
                .filter(pool -> pool.getNombre().equals(nombre))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private Long idDeLaActividad(DiagramaProcesoDto diagrama, String nombre) {
        return diagrama.getActividades().stream()
                .filter(actividad -> actividad.getNombre().equals(nombre))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private String hashDe(String correo) {
        return usuarioRepository.findByUsername(correo).map(Usuario::getPassword).orElseThrow();
    }

    private void verificarSinDatosDemo() {
        assertThat(datosDemoService.estaCargado()).isFalse();
        assertThat(usuarioService.existeUsuarioConCorreo(EDITOR)).isFalse();
        assertThat(usuarioService.existeUsuarioConCorreo(LECTURA)).isFalse();
    }

    @Test
    void fueraDelPerfilDemoElInicializadorNoExiste() {
        assertThat(contexto.getBeanNamesForType(InicializadorDatosDemo.class)).isEmpty();
        assertThat(contexto.getBeanNamesForType(DatosDemoService.class)).hasSize(1);
    }

    @Test
    void cargaElDatasetCompletoCuandoNoExiste() {
        assertThat(datosDemoService.estaCargado()).isFalse();

        assertThat(cargar()).isTrue();

        assertThat(datosDemoService.estaCargado()).isTrue();
        EmpresaRespuestaDto empresa = empresaService.listarVisiblesPara(ADMIN).get(0);
        assertThat(empresa.getNombre()).isEqualTo("Empresa Demo");
        assertThat(empresa.getNit()).isEqualTo(NIT_DEMO);
        assertThat(procesoService.consultarProcesos(new FiltroProcesosDto(), ADMIN).getTotalElements())
                .isEqualTo(1);
        ProcesoRespuestaDto proceso = procesoService.obtener(procesoDemo(), ADMIN);
        assertThat(proceso.getNombre()).isEqualTo("Atención de solicitudes");
        assertThat(proceso.getCategoria()).isEqualTo("Servicio al cliente");
        assertThat(proceso.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
        assertThat(rolProcesoService.activosDeLaEmpresa(empresaDe(ADMIN)))
                .extracting(RolProcesoRespuestaDto::getNombre)
                .containsExactly("Analista", "Supervisor");
    }

    @Test
    void losUsuariosDemoTienenLosRolesCorrectos() {
        cargar();

        assertThat(usuarioService.listarDeMiEmpresa(ADMIN))
                .extracting(UsuarioRespuestaDto::getCorreo, UsuarioRespuestaDto::getRol, UsuarioRespuestaDto::isActivo)
                .containsExactly(
                        tuple(ADMIN, RolUsuario.ADMINISTRADOR, true),
                        tuple(EDITOR, RolUsuario.EDITOR, true),
                        tuple(LECTURA, RolUsuario.SOLO_LECTURA, true));
    }

    @Test
    void lasContrasenasSeGuardanComoHashBcryptYPermitenIniciarSesion() throws Exception {
        cargar();

        String hashAdministrador = hashDe(ADMIN);
        assertThat(hashAdministrador).isNotEqualTo(passwordAdministrador).startsWith("$2");
        assertThat(passwordEncoder.matches(passwordAdministrador, hashAdministrador)).isTrue();
        assertThat(passwordEncoder.matches(passwordUsuarios, hashAdministrador)).isFalse();
        for (String correo : List.of(EDITOR, LECTURA)) {
            String hash = hashDe(correo);
            assertThat(hash).isNotEqualTo(passwordUsuarios).startsWith("$2");
            assertThat(passwordEncoder.matches(passwordUsuarios, hash)).isTrue();
        }
        mockMvc.perform(formLogin().user(ADMIN).password(passwordAdministrador))
                .andExpect(authenticated().withUsername(ADMIN));
        mockMvc.perform(formLogin().user(EDITOR).password(passwordUsuarios))
                .andExpect(authenticated().withUsername(EDITOR));
        mockMvc.perform(formLogin().user(LECTURA).password(passwordUsuarios))
                .andExpect(authenticated().withUsername(LECTURA));
    }

    @Test
    void elDiagramaContieneLosElementosDemo() {
        cargar();

        DiagramaProcesoDto diagrama = diagramaDemo();
        Long propietario = idDelPool(diagrama, "Empresa Demo");
        Long cliente = idDelPool(diagrama, "Cliente");
        Long servicioCorreo = idDelPool(diagrama, "Servicio de correo");

        assertThat(diagrama.getPools())
                .extracting(PoolRespuestaDto::getNombre, PoolRespuestaDto::getTipo, PoolRespuestaDto::isCajaNegra)
                .containsExactly(
                        tuple("Empresa Demo", TipoPool.PROPIETARIO, false),
                        tuple("Cliente", TipoPool.EXTERNO, false),
                        tuple("Servicio de correo", TipoPool.EXTERNO, true));
        assertThat(diagrama.getLanes()).extracting("nombre").containsExactly("General", "Supervisor");
        assertThat(diagrama.getActividades())
                .extracting(ActividadRespuestaDto::getNombre, ActividadRespuestaDto::getLaneNombre)
                .containsExactlyInAnyOrder(
                        tuple("Registrar solicitud", "General"),
                        tuple("Evaluar solicitud", "General"),
                        tuple("Aprobar solicitud", "Supervisor"));
        assertThat(diagrama.getGateways())
                .extracting(GatewayRespuestaDto::getTipo, GatewayRespuestaDto::getPoolId)
                .containsExactly(tuple(TipoGateway.EXCLUSIVO, propietario));
        assertThat(diagrama.getMessageCatches())
                .extracting(MessageCatchRespuestaDto::getNombreMensaje, MessageCatchRespuestaDto::getVariante,
                        MessageCatchRespuestaDto::getPoolId, MessageCatchRespuestaDto::isOrigenExterno)
                .containsExactly(tuple("Solicitud de servicio", VarianteMessageCatch.INICIO, propietario, false));
        assertThat(diagrama.getMessageThrows())
                .extracting(MessageThrowRespuestaDto::getNombreMensaje, MessageThrowRespuestaDto::getPoolOrigenId,
                        MessageThrowRespuestaDto::getPoolDestinoId)
                .containsExactly(tuple("Solicitud de servicio", cliente, propietario));
        assertThat(diagrama.getEnviosExternos())
                .extracting(EnvioExternoRespuestaDto::getNombreMensaje, EnvioExternoRespuestaDto::getPoolOrigenId,
                        EnvioExternoRespuestaDto::getPoolDestinoId, EnvioExternoRespuestaDto::getTipoDestino,
                        EnvioExternoRespuestaDto::getComportamientoFallo)
                .containsExactly(tuple("Notificación de resultado", propietario, servicioCorreo,
                        TipoDestinoExterno.CORREO, ComportamientoFallo.CONTINUAR));
        assertThat(diagrama.getFlujosMensaje()).hasSize(2);
    }

    @Test
    void losArcosFormanUnFlujoCoherenteDentroDelPoolPropietario() {
        cargar();

        DiagramaProcesoDto diagrama = diagramaDemo();
        Long solicitud = diagrama.getMessageCatches().get(0).getId();
        Long notificacion = diagrama.getEnviosExternos().get(0).getId();
        Long gateway = diagrama.getGateways().get(0).getId();
        Long registrar = idDeLaActividad(diagrama, "Registrar solicitud");
        Long evaluar = idDeLaActividad(diagrama, "Evaluar solicitud");
        Long aprobar = idDeLaActividad(diagrama, "Aprobar solicitud");

        assertThat(diagrama.getArcos())
                .extracting("origenTipo", "origenId", "destinoTipo", "destinoId", "condicion")
                .containsExactlyInAnyOrder(
                        tuple(TipoNodoFlujo.EVENTO, solicitud, TipoNodoFlujo.ACTIVIDAD, registrar, null),
                        tuple(TipoNodoFlujo.ACTIVIDAD, registrar, TipoNodoFlujo.ACTIVIDAD, evaluar, null),
                        tuple(TipoNodoFlujo.ACTIVIDAD, evaluar, TipoNodoFlujo.GATEWAY, gateway, null),
                        tuple(TipoNodoFlujo.GATEWAY, gateway, TipoNodoFlujo.ACTIVIDAD, aprobar, "Aprobada"),
                        tuple(TipoNodoFlujo.GATEWAY, gateway, TipoNodoFlujo.EVENTO, notificacion, "Rechazada"),
                        tuple(TipoNodoFlujo.ACTIVIDAD, aprobar, TipoNodoFlujo.EVENTO, notificacion, null));
        assertThat(diagrama.getArcos()).noneMatch(arco -> arco.getDestinoTipo() == TipoNodoFlujo.EVENTO
                && arco.getDestinoId().equals(solicitud));
    }

    @Test
    void laCorrelacionEntreMessageThrowYMessageCatchEsCoherente() {
        cargar();

        DiagramaProcesoDto diagrama = diagramaDemo();
        MessageThrowRespuestaDto messageThrow = diagrama.getMessageThrows().get(0);
        MessageCatchRespuestaDto messageCatch = diagrama.getMessageCatches().get(0);

        assertThat(messageThrow.getCatchHomologoId()).isEqualTo(messageCatch.getId());
        assertThat(messageCatch.getThrowHomologoId()).isEqualTo(messageThrow.getId());
        assertThat(messageThrow.getClaveCorrelacion()).isEqualTo(CLAVE_CORRELACION);
        assertThat(messageCatch.getClaveCorrelacion()).isEqualTo(CLAVE_CORRELACION);
        assertThat(diagrama.getEnviosExternos().get(0).getClaveCorrelacion()).isEqualTo(CLAVE_CORRELACION);
        assertThat(messageThrow.getAdvertencias()).isEmpty();
        assertThat(messageCatch.getAdvertencias()).isEmpty();
    }

    @Test
    void elModeloFinalSoloConservaElRecordatorioDelGatewayExclusivoYSePodriaPublicar() {
        cargar();

        Long procesoId = procesoDemo();
        assertThat(gatewayService.advertenciasDelProceso(procesoId, ADMIN))
                .singleElement()
                .asString()
                .contains("revisa que las condiciones de sus salidas sean mutuamente excluyentes");
        assertThat(validacionModeloService.problemasDelModelo(
                accesoProcesoService.procesoDeLaEmpresa(procesoId, usuarioService.usuarioAutenticado(ADMIN))))
                .isEmpty();
        assertThat(procesoService.obtener(procesoId, ADMIN).getEstado()).isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void elEditorNoPuedeCrearPoolsPeroConservaElRestoDePermisosPorDefecto() {
        cargar();

        PermisoEstructuraDto editor = permisoEstructuraService.consultar(procesoDemo(), ADMIN).stream()
                .filter(permiso -> permiso.getRol() == RolUsuario.EDITOR)
                .findFirst()
                .orElseThrow();
        assertThat(editor.isCrearPool()).isFalse();
        assertThat(editor.isEditarPool()).isTrue();
        assertThat(editor.isEliminarPool()).isFalse();
        assertThat(editor.isCrearLane()).isTrue();
        assertThat(editor.isEditarLane()).isTrue();
        assertThat(editor.isEliminarLane()).isFalse();
    }

    @Test
    void unaSegundaCargaNoDuplicaDatosNiCambiaContrasenas() {
        cargar();
        String hashAntes = hashDe(ADMIN);
        String otraPassword = UUID.randomUUID().toString();

        assertThat(datosDemoService.cargar(otraPassword, otraPassword)).isFalse();

        assertThat(usuarioService.listarDeMiEmpresa(ADMIN)).hasSize(3);
        assertThat(rolProcesoService.activosDeLaEmpresa(empresaDe(ADMIN))).hasSize(2);
        assertThat(procesoService.consultarProcesos(new FiltroProcesosDto(), ADMIN).getTotalElements())
                .isEqualTo(1);
        DiagramaProcesoDto diagrama = diagramaDemo();
        assertThat(diagrama.getPools()).hasSize(3);
        assertThat(diagrama.getLanes()).hasSize(2);
        assertThat(diagrama.getActividades()).hasSize(3);
        assertThat(diagrama.getGateways()).hasSize(1);
        assertThat(diagrama.getArcos()).hasSize(6);
        assertThat(hashDe(ADMIN)).isEqualTo(hashAntes);
        assertThat(passwordEncoder.matches(otraPassword, hashDe(ADMIN))).isFalse();
    }

    @Test
    void elDatasetDemoQuedaAisladoDeOtrasEmpresas() {
        cargar();
        String otroAdmin = "admin@aislamiento-demo.com";
        empresaService.registrar(new RegistroEmpresaDto("Aislamiento Demo", "905900001-1", otroAdmin,
                UUID.randomUUID().toString()));
        Long procesoId = procesoDemo();
        FiltroProcesosDto todos = new FiltroProcesosDto();
        todos.setAlcance(AlcanceProceso.TODOS);

        assertThat(procesoService.consultarProcesos(todos, otroAdmin).getTotalElements()).isZero();
        assertThatThrownBy(() -> procesoService.obtener(procesoId, otroAdmin))
                .isInstanceOf(UsuarioSinPermisoException.class);
        assertThat(usuarioService.listarDeMiEmpresa(otroAdmin))
                .extracting(UsuarioRespuestaDto::getCorreo)
                .containsExactly(otroAdmin);
        assertThat(rolProcesoService.activosDeLaEmpresa(empresaDe(otroAdmin))).isEmpty();
    }

    @Test
    void unaContrasenaInvalidaSeRechazaSinDejarDatos() {
        assertThatThrownBy(() -> datosDemoService.cargar("corta", passwordUsuarios))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageNotContaining("corta");
        assertThatThrownBy(() -> datosDemoService.cargar(passwordAdministrador, " "))
                .isInstanceOf(ConstraintViolationException.class);

        verificarSinDatosDemo();
        assertThat(empresaRepository.existsByNit(NIT_DEMO)).isFalse();
    }

    @Test
    void unNitDemoRegistradoSinSuAdministradorFallaSinRepararElDataset() {
        empresaService.registrar(new RegistroEmpresaDto("Empresa Ajena", NIT_DEMO, "admin@ajena-demo.com",
                UUID.randomUUID().toString()));

        assertThatThrownBy(this::cargar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NIT_DEMO);

        verificarSinDatosDemo();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unFalloAMitadDeLaCargaRevierteTodoElDataset() {
        try {
            empresaService.registrar(new RegistroEmpresaDto("Conflicto Demo", NIT_CONFLICTO, EDITOR,
                    UUID.randomUUID().toString()));

            assertThatThrownBy(this::cargar).isInstanceOf(CorreoAdministradorEnUsoException.class);

            assertThat(datosDemoService.estaCargado()).isFalse();
            assertThat(empresaRepository.existsByNit(NIT_DEMO)).isFalse();
            assertThat(usuarioService.existeUsuarioConCorreo(LECTURA)).isFalse();
        } finally {
            jdbcTemplate.update("delete from usuario where username = ?", EDITOR);
            jdbcTemplate.update("delete from empresa where nit = ?", NIT_CONFLICTO);
        }
    }
}
