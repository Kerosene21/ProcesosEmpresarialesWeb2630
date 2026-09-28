package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    private static final String CORREO = "contacto@alpes.com";
    private static final String PASSWORD = "Clave-Inicial-2026";

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private PasswordEncoder passwordEncoder;

    private EmpresaService empresaService;

    @BeforeEach
    void inicializar() {
        passwordEncoder = new BCryptPasswordEncoder();
        UsuarioService usuarioService = new UsuarioService(usuarioRepository, new ModelMapper(), passwordEncoder);
        empresaService = new EmpresaService(empresaRepository, usuarioService, new ModelMapper());
    }

    private RegistroEmpresaDto formularioValido() {
        return new RegistroEmpresaDto("Alpes Logistica", "900123456-7", CORREO, PASSWORD);
    }

    private void asignarIdAlGuardarEmpresa(Long id) {
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(invocacion -> {
            Empresa empresa = invocacion.getArgument(0);
            empresa.setId(id);
            return empresa;
        });
    }

    private Usuario administradorCapturado() {
        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private Usuario usuarioDeLaEmpresa(Empresa empresa) {
        return new Usuario(3L, CORREO, passwordEncoder.encode(PASSWORD), RolUsuario.ADMINISTRADOR, true, empresa);
    }

    @Test
    void registrarPersisteLaEmpresaConLosDatosDelFormulario() {
        asignarIdAlGuardarEmpresa(10L);

        empresaService.registrar(formularioValido());

        ArgumentCaptor<Empresa> capturada = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getNombre()).isEqualTo("Alpes Logistica");
        assertThat(capturada.getValue().getNit()).isEqualTo("900123456-7");
        assertThat(capturada.getValue().getCorreoContacto()).isEqualTo(CORREO);
    }

    @Test
    void registrarNormalizaEspaciosYMayusculasDelCorreoAntesDePersistir() {
        asignarIdAlGuardarEmpresa(11L);

        empresaService.registrar(
                new RegistroEmpresaDto("  Alpes Logistica  ", "  900123456-7  ", "  Contacto@Alpes.com  ", PASSWORD));

        ArgumentCaptor<Empresa> capturada = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getNombre()).isEqualTo("Alpes Logistica");
        assertThat(capturada.getValue().getNit()).isEqualTo("900123456-7");
        assertThat(capturada.getValue().getCorreoContacto()).isEqualTo(CORREO);
    }

    @Test
    void registrarDevuelveLaEmpresaConElIdentificadorAsignado() {
        asignarIdAlGuardarEmpresa(42L);

        EmpresaRespuestaDto respuesta = empresaService.registrar(formularioValido());

        assertThat(respuesta.getId()).isEqualTo(42L);
        assertThat(respuesta.getNombre()).isEqualTo("Alpes Logistica");
        assertThat(respuesta.getNit()).isEqualTo("900123456-7");
        assertThat(respuesta.getCorreoContacto()).isEqualTo(CORREO);
    }

    @Test
    void registrarRechazaUnNitYaRegistrado() {
        when(empresaRepository.existsByNit("900123456-7")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(formularioValido()))
                .isInstanceOf(NitEmpresaDuplicadoException.class);

        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrarVerificaLaUnicidadDelNitSobreElValorNormalizado() {
        when(empresaRepository.existsByNit("900123456-7")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(
                new RegistroEmpresaDto("Alpes Logistica", "  900123456-7  ", CORREO, PASSWORD)))
                .isInstanceOf(NitEmpresaDuplicadoException.class);
    }

    @Test
    void registrarCreaElAdministradorInicialAsociadoALaEmpresaCreada() {
        asignarIdAlGuardarEmpresa(7L);

        empresaService.registrar(formularioValido());

        Usuario administrador = administradorCapturado();
        assertThat(administrador.getEmpresa()).isNotNull();
        assertThat(administrador.getEmpresa().getId()).isEqualTo(7L);
        assertThat(administrador.getEmpresa().getNit()).isEqualTo("900123456-7");
    }

    @Test
    void registrarCreaElAdministradorInicialConRolAdministrador() {
        asignarIdAlGuardarEmpresa(8L);

        empresaService.registrar(formularioValido());

        assertThat(administradorCapturado().getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    @Test
    void registrarIdentificaAlAdministradorInicialConElCorreoDeContacto() {
        asignarIdAlGuardarEmpresa(9L);

        EmpresaRespuestaDto respuesta = empresaService.registrar(formularioValido());

        assertThat(administradorCapturado().getUsername()).isEqualTo(CORREO);
        assertThat(respuesta.getAdministradorUsername()).isEqualTo(CORREO);
    }

    @Test
    void registrarRechazaUnCorreoYaUsadoPorOtroUsuario() {
        when(usuarioRepository.existsByUsername(CORREO)).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(formularioValido()))
                .isInstanceOf(CorreoAdministradorEnUsoException.class);

        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrarGuardaLaContrasenaDelAdministradorComoHashBcrypt() {
        asignarIdAlGuardarEmpresa(12L);

        empresaService.registrar(formularioValido());

        String almacenada = administradorCapturado().getPassword();
        assertThat(almacenada).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, almacenada)).isTrue();
    }

    @Test
    void registrarNuncaGuardaLaContrasenaDelAdministradorEnClaro() {
        asignarIdAlGuardarEmpresa(13L);

        empresaService.registrar(formularioValido());

        String almacenada = administradorCapturado().getPassword();
        assertThat(almacenada).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
    }

    @Test
    void registrarDejaAlAdministradorInicialActivoParaQuePuedaIniciarSesion() {
        asignarIdAlGuardarEmpresa(14L);

        empresaService.registrar(formularioValido());

        assertThat(administradorCapturado().isActivo()).isTrue();
    }

    @Test
    void registrarRechazaUnFormularioSinContrasenaParaElAdministrador() {
        assertThatThrownBy(() -> empresaService.registrar(
                new RegistroEmpresaDto("Alpes Logistica", "900123456-7", CORREO, "   ")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void laRespuestaDelRegistroNoExponeNingunCampoDeContrasena() {
        asignarIdAlGuardarEmpresa(15L);

        EmpresaRespuestaDto respuesta = empresaService.registrar(formularioValido());

        assertThat(respuesta).isNotNull();
        for (Field campo : EmpresaRespuestaDto.class.getDeclaredFields()) {
            assertThat(campo.getName().toLowerCase()).doesNotContain("password").doesNotContain("contrasena");
        }
    }

    @Test
    void obtenerParaUsuarioDevuelveSuPropiaEmpresaJuntoAlAdministradorInicial() {
        Empresa empresa = new Empresa(5L, "Alpes Logistica", "900123456-7", CORREO);
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.of(usuarioDeLaEmpresa(empresa)));
        when(empresaRepository.findById(5L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findFirstByEmpresaIdAndRolOrderByIdAsc(5L, RolUsuario.ADMINISTRADOR))
                .thenReturn(Optional.of(usuarioDeLaEmpresa(empresa)));

        EmpresaRespuestaDto respuesta = empresaService.obtenerParaUsuario(5L, CORREO);

        assertThat(respuesta.getId()).isEqualTo(5L);
        assertThat(respuesta.getNombre()).isEqualTo("Alpes Logistica");
        assertThat(respuesta.getCorreoContacto()).isEqualTo(CORREO);
        assertThat(respuesta.getAdministradorUsername()).isEqualTo(CORREO);
    }

    @Test
    void obtenerParaUsuarioRechazaLaConsultaDeUnaEmpresaAjena() {
        Empresa propia = new Empresa(5L, "Alpes Logistica", "900123456-7", CORREO);
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.of(usuarioDeLaEmpresa(propia)));

        assertThatThrownBy(() -> empresaService.obtenerParaUsuario(99L, CORREO))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(empresaRepository, never()).findById(99L);
    }

    @Test
    void obtenerParaUsuarioFallaCuandoElUsuarioAutenticadoNoExiste() {
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.obtenerParaUsuario(5L, CORREO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerParaUsuarioFallaCuandoLaEmpresaPropiaYaNoExiste() {
        Empresa propia = new Empresa(5L, "Alpes Logistica", "900123456-7", CORREO);
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.of(usuarioDeLaEmpresa(propia)));
        when(empresaRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.obtenerParaUsuario(5L, CORREO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarVisiblesParaDevuelveUnicamenteLaEmpresaDelUsuarioAutenticado() {
        Empresa propia = new Empresa(5L, "Alpes Logistica", "900123456-7", CORREO);
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.of(usuarioDeLaEmpresa(propia)));

        List<EmpresaRespuestaDto> empresas = empresaService.listarVisiblesPara(CORREO);

        assertThat(empresas).hasSize(1);
        assertThat(empresas.get(0).getId()).isEqualTo(5L);
        assertThat(empresas.get(0).getNit()).isEqualTo("900123456-7");
        verify(empresaRepository, never()).findAll();
    }

    @Test
    void listarVisiblesParaFallaCuandoElUsuarioAutenticadoNoExiste() {
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.listarVisiblesPara(CORREO))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
    @Test
    void buscarPorIdDevuelveLaEmpresaExistente() {
        Empresa empresa = new Empresa(7L, "Alpes Logistica", "900123456-7", CORREO);
        when(empresaRepository.findById(7L)).thenReturn(Optional.of(empresa));

        assertThat(empresaService.buscarPorId(7L)).isSameAs(empresa);
    }

    @Test
    void buscarPorIdDeUnaEmpresaInexistenteFallaConMensajeClaro() {
        when(empresaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.buscarPorId(404L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage(EmpresaService.EMPRESA_NO_EXISTE);
    }
}
