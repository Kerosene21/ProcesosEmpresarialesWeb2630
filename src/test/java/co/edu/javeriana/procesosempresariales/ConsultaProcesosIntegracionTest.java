package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsultaProcesosIntegracionTest {

    private static final String ADMIN_ALPES = "admin@alpes-consulta.com";
    private static final String LECTOR_ALPES = "lector@alpes-consulta.com";
    private static final String ADMIN_ANDES = "admin@andes-consulta.com";
    private static final String PASSWORD = "Clave-Consulta-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private ProcesoService procesoService;

    private void registrarAlpes() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes Consulta", "905000001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(LECTOR_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
    }

    private void registrarAndes() {
        empresaService.registrar(new RegistroEmpresaDto("Andes Consulta", "905000002-2", ADMIN_ANDES, PASSWORD));
    }

    private ProcesoRespuestaDto crear(String nombre, String categoria, String autor) {
        return procesoService.crear(new CrearProcesoDto(nombre, "Descripcion de " + nombre, categoria), autor);
    }

    private void publicar(ProcesoRespuestaDto proceso, String autor) {
        procesoService.editar(proceso.getId(), new EditarProcesoDto(proceso.getNombre(), proceso.getDescripcion(),
                proceso.getCategoria(), EstadoProceso.PUBLICADO), autor);
    }

    private FiltroProcesosDto filtro() {
        return new FiltroProcesosDto();
    }

    private List<String> nombresDe(Page<ProcesoResumenDto> pagina) {
        return pagina.getContent().stream().map(ProcesoResumenDto::getNombre).toList();
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    @Test
    void elListadoDevuelveSoloLosProcesosDeLaEmpresaDelUsuario() {
        registrarAlpes();
        registrarAndes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ANDES);

        Page<ProcesoResumenDto> deAlpes = procesoService.consultarProcesos(filtro(), ADMIN_ALPES);
        Page<ProcesoResumenDto> deAndes = procesoService.consultarProcesos(filtro(), ADMIN_ANDES);

        assertThat(nombresDe(deAlpes)).containsExactly("Ventas");
        assertThat(nombresDe(deAndes)).containsExactly("Compras");
    }

    @Test
    void laBusquedaNuncaEncuentraProcesosDeOtraEmpresa() {
        registrarAlpes();
        registrarAndes();
        crear("Ventas Corporativas", "Comercial", ADMIN_ANDES);

        FiltroProcesosDto filtro = filtro();
        filtro.setQ("ventas");

        assertThat(procesoService.consultarProcesos(filtro, ADMIN_ALPES).getTotalElements()).isZero();
    }

    @Test
    void elListadoPorDefectoExcluyeLosProcesosEliminados() {
        registrarAlpes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        ProcesoRespuestaDto compras = crear("Compras", "Operaciones", ADMIN_ALPES);
        procesoService.eliminar(compras.getId(), ADMIN_ALPES);

        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(filtro(), ADMIN_ALPES);

        assertThat(nombresDe(pagina)).containsExactly("Ventas");
    }

    @Test
    void elFiltroDeInactivosMuestraSoloLosProcesosEliminados() {
        registrarAlpes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        ProcesoRespuestaDto compras = crear("Compras", "Operaciones", ADMIN_ALPES);
        procesoService.eliminar(compras.getId(), ADMIN_ALPES);

        FiltroProcesosDto filtro = filtro();
        filtro.setVisibilidad(VisibilidadProceso.INACTIVOS);
        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(filtro, ADMIN_ALPES);

        assertThat(nombresDe(pagina)).containsExactly("Compras");
        assertThat(pagina.getContent().get(0).isEliminado()).isTrue();
    }

    @Test
    void elFiltroDeTodosMuestraActivosEInactivos() {
        registrarAlpes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        ProcesoRespuestaDto compras = crear("Compras", "Operaciones", ADMIN_ALPES);
        procesoService.eliminar(compras.getId(), ADMIN_ALPES);

        FiltroProcesosDto filtro = filtro();
        filtro.setVisibilidad(VisibilidadProceso.TODOS);

        assertThat(nombresDe(procesoService.consultarProcesos(filtro, ADMIN_ALPES)))
                .containsExactly("Compras", "Ventas");
    }

    @Test
    void laBusquedaPorNombreEsParcialEInsensibleAMayusculas() {
        registrarAlpes();
        crear("Proceso de Ventas", "Comercial", ADMIN_ALPES);
        crear("Compras nacionales", "Operaciones", ADMIN_ALPES);

        FiltroProcesosDto minusculas = filtro();
        minusculas.setQ("venta");
        FiltroProcesosDto mayusculas = filtro();
        mayusculas.setQ("VENTA");

        assertThat(nombresDe(procesoService.consultarProcesos(minusculas, ADMIN_ALPES)))
                .containsExactly("Proceso de Ventas");
        assertThat(nombresDe(procesoService.consultarProcesos(mayusculas, ADMIN_ALPES)))
                .containsExactly("Proceso de Ventas");
    }

    @Test
    void elFiltroPorEstadoSeparaBorradoresDePublicados() {
        registrarAlpes();
        ProcesoRespuestaDto ventas = crear("Ventas", "Comercial", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ALPES);
        publicar(ventas, ADMIN_ALPES);

        FiltroProcesosDto publicados = filtro();
        publicados.setEstado(EstadoProceso.PUBLICADO);
        FiltroProcesosDto borradores = filtro();
        borradores.setEstado(EstadoProceso.BORRADOR);

        assertThat(nombresDe(procesoService.consultarProcesos(publicados, ADMIN_ALPES))).containsExactly("Ventas");
        assertThat(nombresDe(procesoService.consultarProcesos(borradores, ADMIN_ALPES))).containsExactly("Compras");
    }

    @Test
    void elFiltroPorCategoriaDevuelveSoloEsaCategoria() {
        registrarAlpes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ALPES);

        FiltroProcesosDto filtro = filtro();
        filtro.setCategoria("Comercial");

        assertThat(nombresDe(procesoService.consultarProcesos(filtro, ADMIN_ALPES))).containsExactly("Ventas");
    }

    @Test
    void unaCategoriaEscritaConEspaciosSeGuardaRecortadaYElFiltroLaEncuentra() {
        registrarAlpes();
        crear("Ventas", "  Comercial  ", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ALPES);

        assertThat(procesoService.categoriasDisponibles(ADMIN_ALPES)).contains("Comercial");

        FiltroProcesosDto filtro = filtro();
        filtro.setCategoria("Comercial");

        assertThat(nombresDe(procesoService.consultarProcesos(filtro, ADMIN_ALPES))).containsExactly("Ventas");
    }

    @Test
    void losFiltrosSeCombinanEntreSi() {
        registrarAlpes();
        ProcesoRespuestaDto ventasComercial = crear("Ventas Corporativas", "Comercial", ADMIN_ALPES);
        ProcesoRespuestaDto ventasOperaciones = crear("Ventas Internas", "Operaciones", ADMIN_ALPES);
        crear("Compras", "Comercial", ADMIN_ALPES);
        publicar(ventasComercial, ADMIN_ALPES);
        publicar(ventasOperaciones, ADMIN_ALPES);

        FiltroProcesosDto filtro = filtro();
        filtro.setQ("ventas");
        filtro.setEstado(EstadoProceso.PUBLICADO);
        filtro.setCategoria("Comercial");
        filtro.setVisibilidad(VisibilidadProceso.ACTIVOS);

        assertThat(nombresDe(procesoService.consultarProcesos(filtro, ADMIN_ALPES)))
                .containsExactly("Ventas Corporativas");
    }

    @Test
    void elListadoSePaginaEnBloquesDeDiez() {
        registrarAlpes();
        for (int numero = 1; numero <= 12; numero++) {
            crear("Proceso " + String.format("%02d", numero), "Comercial", ADMIN_ALPES);
        }

        Page<ProcesoResumenDto> primera = procesoService.consultarProcesos(filtro(), ADMIN_ALPES);
        FiltroProcesosDto segundaPagina = filtro();
        segundaPagina.setPage(1);
        Page<ProcesoResumenDto> segunda = procesoService.consultarProcesos(segundaPagina, ADMIN_ALPES);

        assertThat(primera.getTotalElements()).isEqualTo(12);
        assertThat(primera.getTotalPages()).isEqualTo(2);
        assertThat(primera.getContent()).hasSize(10);
        assertThat(segunda.getContent()).hasSize(2);
        assertThat(nombresDe(segunda)).containsExactly("Proceso 11", "Proceso 12");
    }

    @Test
    void unaPaginaFueraDeRangoDevuelveContenidoVacioSinFallar() {
        registrarAlpes();
        crear("Ventas", "Comercial", ADMIN_ALPES);

        FiltroProcesosDto filtro = filtro();
        filtro.setPage(99);
        Page<ProcesoResumenDto> pagina = procesoService.consultarProcesos(filtro, ADMIN_ALPES);

        assertThat(pagina.getContent()).isEmpty();
        assertThat(pagina.getTotalElements()).isEqualTo(1);
    }

    @Test
    void lasCategoriasDisponiblesSonSoloLasDeLaEmpresa() {
        registrarAlpes();
        registrarAndes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ALPES);
        crear("Logistica", "Transporte", ADMIN_ANDES);

        assertThat(procesoService.categoriasDisponibles(ADMIN_ALPES))
                .containsExactly("Comercial", "Operaciones");
        assertThat(procesoService.categoriasDisponibles(ADMIN_ANDES)).containsExactly("Transporte");
    }

    @Test
    void unUsuarioDeSoloLecturaConsultaElListadoDesdeLaWeb() throws Exception {
        registrarAlpes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(LECTOR_ALPES);

        mockMvc.perform(get("/procesos").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/lista"));
    }

    @Test
    void elListadoWebDeUnaEmpresaNoIncluyeProcesosDeLaOtra() throws Exception {
        registrarAlpes();
        registrarAndes();
        crear("Ventas", "Comercial", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ANDES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        MvcResult resultado = mockMvc.perform(get("/procesos").session(sesion))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Page<ProcesoResumenDto> procesos =
                (Page<ProcesoResumenDto>) resultado.getModelAndView().getModel().get("procesos");
        assertThat(nombresDe(procesos)).containsExactly("Ventas");
    }

    @Test
    void elListadoWebAplicaLaBusquedaYLosFiltrosRecibidos() throws Exception {
        registrarAlpes();
        ProcesoRespuestaDto ventas = crear("Ventas Corporativas", "Comercial", ADMIN_ALPES);
        crear("Compras", "Operaciones", ADMIN_ALPES);
        publicar(ventas, ADMIN_ALPES);
        MockHttpSession sesion = iniciarSesion(ADMIN_ALPES);

        MvcResult resultado = mockMvc.perform(get("/procesos").session(sesion)
                .param("q", "corporativas")
                .param("estado", "PUBLICADO")
                .param("categoria", "Comercial"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Page<ProcesoResumenDto> procesos =
                (Page<ProcesoResumenDto>) resultado.getModelAndView().getModel().get("procesos");
        assertThat(nombresDe(procesos)).containsExactly("Ventas Corporativas");
    }

    @Test
    void elDetalleDeUnProcesoPropioMuestraSuPool() {
        registrarAlpes();
        ProcesoRespuestaDto ventas = crear("Ventas", "Comercial", ADMIN_ALPES);

        ProcesoRespuestaDto detalle = procesoService.obtener(ventas.getId(), LECTOR_ALPES);

        assertThat(detalle.getNombre()).isEqualTo("Ventas");
        assertThat(detalle.getPoolId()).isNotNull();
        assertThat(detalle.getPoolNombre()).isEqualTo("Alpes Consulta");
    }
}
