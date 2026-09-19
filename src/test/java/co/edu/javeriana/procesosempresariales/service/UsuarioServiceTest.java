package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final String ADMINISTRADOR = "admin@alpes.com";
    private static final String NUEVO_CORREO = "editor@alpes.com";
    private static final String PASSWORD = "Clave-Usuario-2026";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long ID_ADMINISTRADOR = 1L;
    private static final Long ID_OBJETIVO = 2L;

    @Mock
    private UsuarioRepository usuarioRepository;

    private PasswordEncoder passwordEncoder;

    private UsuarioService usuarioService;

    @BeforeEach
    void inicializar() {
        passwordEncoder = new BCryptPasswordEncoder();
        usuarioService = new UsuarioService(usuarioRepository, new ModelMapper(), passwordEncoder);
    }

    private Empresa empresa(Long id, String nombre) {
        return new Empresa(id, nombre, "900123456-7", "contacto@alpes.com");
    }

    private Usuario usuario(Long id, String username, RolUsuario rol, boolean activo, Long empresaId) {
        return new Usuario(id, username, HASH, rol, activo, empresa(empresaId, "Alpes Logistica"));
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(ADMINISTRADOR))
                .thenReturn(Optional.of(usuario(ID_ADMINISTRADOR, ADMINISTRADOR, rol, true, EMPRESA_PROPIA)));
    }

    private void existeEnLaEmpresa(Usuario objetivo) {
        when(usuarioRepository.findByIdAndEmpresaId(objetivo.getId(), EMPRESA_PROPIA))
                .thenReturn(Optional.of(objetivo));
    }

    private void devolverLoGuardado() {
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    private Usuario usuarioGuardado() {
        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        return capturado.getValue();
    }

    private CrearUsuarioDto formularioValido() {
        return new CrearUsuarioDto(NUEVO_CORREO, PASSWORD, RolUsuario.EDITOR);
    }

    @Test
    void unAdministradorCreaUnUsuarioEnSuEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        UsuarioRespuestaDto respuesta = usuarioService.crear(formularioValido(), ADMINISTRADOR);

        assertThat(respuesta.getCorreo()).isEqualTo(NUEVO_CORREO);
        assertThat(usuarioGuardado().getUsername()).isEqualTo(NUEVO_CORREO);
    }

    @Test
    void laContrasenaDelUsuarioCreadoSeGuardaHasheadaConBcrypt() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        usuarioService.crear(formularioValido(), ADMINISTRADOR);

        String almacenada = usuarioGuardado().getPassword();
        assertThat(almacenada).startsWith("$2").isNotEqualTo(PASSWORD).doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, almacenada)).isTrue();
    }

    @Test
    void elUsuarioCreadoQuedaActivo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        usuarioService.crear(formularioValido(), ADMINISTRADOR);

        assertThat(usuarioGuardado().isActivo()).isTrue();
    }

    @Test
    void elUsuarioCreadoRecibeElRolIndicadoEnElFormulario() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        usuarioService.crear(new CrearUsuarioDto(NUEVO_CORREO, PASSWORD, RolUsuario.SOLO_LECTURA), ADMINISTRADOR);

        assertThat(usuarioGuardado().getRol()).isEqualTo(RolUsuario.SOLO_LECTURA);
    }

    @Test
    void laEmpresaDelUsuarioCreadoEsLaDelAdministradorAutenticado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        usuarioService.crear(formularioValido(), ADMINISTRADOR);

        assertThat(usuarioGuardado().getEmpresa().getId()).isEqualTo(EMPRESA_PROPIA);
    }

    @Test
    void elCorreoDelUsuarioCreadoSeNormalizaEnMinusculasYSinEspacios() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        usuarioService.crear(new CrearUsuarioDto("  Editor@Alpes.COM  ", PASSWORD, RolUsuario.EDITOR), ADMINISTRADOR);

        assertThat(usuarioGuardado().getUsername()).isEqualTo(NUEVO_CORREO);
    }

    @Test
    void elCorreoDuplicadoSeRechazaSobreElValorNormalizado() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(usuarioRepository.existsByUsername(NUEVO_CORREO)).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.crear(
                new CrearUsuarioDto("  Editor@Alpes.COM  ", PASSWORD, RolUsuario.EDITOR), ADMINISTRADOR))
                .isInstanceOf(CorreoAdministradorEnUsoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unEditorNoPuedeCrearUsuarios() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> usuarioService.crear(formularioValido(), ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unUsuarioDeSoloLecturaNoPuedeCrearUsuarios() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> usuarioService.crear(formularioValido(), ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crearRechazaUnFormularioSinContrasena() {
        autenticar(RolUsuario.ADMINISTRADOR);

        assertThatThrownBy(() -> usuarioService.crear(
                new CrearUsuarioDto(NUEVO_CORREO, "   ", RolUsuario.EDITOR), ADMINISTRADOR))
                .isInstanceOf(IllegalArgumentException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void laRespuestaDeUsuarioNoExponeNingunCampoDeContrasena() {
        autenticar(RolUsuario.ADMINISTRADOR);
        devolverLoGuardado();

        UsuarioRespuestaDto respuesta = usuarioService.crear(formularioValido(), ADMINISTRADOR);

        assertThat(respuesta).isNotNull();
        for (Field campo : UsuarioRespuestaDto.class.getDeclaredFields()) {
            assertThat(campo.getName().toLowerCase()).doesNotContain("password").doesNotContain("contrasena");
        }
    }

    @Test
    void elListadoDevuelveUnicamenteLosUsuariosDeLaEmpresaDelAdministrador() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(usuarioRepository.findByEmpresaIdOrderByUsernameAsc(EMPRESA_PROPIA)).thenReturn(List.of(
                usuario(ID_ADMINISTRADOR, ADMINISTRADOR, RolUsuario.ADMINISTRADOR, true, EMPRESA_PROPIA),
                usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.EDITOR, false, EMPRESA_PROPIA)));

        List<UsuarioRespuestaDto> usuarios = usuarioService.listarDeMiEmpresa(ADMINISTRADOR);

        assertThat(usuarios).hasSize(2);
        assertThat(usuarios).extracting(UsuarioRespuestaDto::getCorreo)
                .containsExactly(ADMINISTRADOR, NUEVO_CORREO);
        assertThat(usuarios).extracting(UsuarioRespuestaDto::isActivo).containsExactly(true, false);
        verify(usuarioRepository, never()).findAll();
    }

    @Test
    void elListadoExigeRolAdministrador() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> usuarioService.listarDeMiEmpresa(ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).findByEmpresaIdOrderByUsernameAsc(anyLong());
    }

    @Test
    void obtenerDevuelveUnUsuarioDeLaPropiaEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.EDITOR, true, EMPRESA_PROPIA));

        UsuarioRespuestaDto respuesta = usuarioService.obtener(ID_OBJETIVO, ADMINISTRADOR);

        assertThat(respuesta.getId()).isEqualTo(ID_OBJETIVO);
        assertThat(respuesta.getCorreo()).isEqualTo(NUEVO_CORREO);
        assertThat(respuesta.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(respuesta.isActivo()).isTrue();
    }

    @Test
    void obtenerExigeRolAdministrador() {
        autenticar(RolUsuario.SOLO_LECTURA);

        assertThatThrownBy(() -> usuarioService.obtener(ID_OBJETIVO, ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);
    }

    @Test
    void unAdministradorCambiaElRolDeUnUsuarioDeSuEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.SOLO_LECTURA, true, EMPRESA_PROPIA));
        devolverLoGuardado();

        UsuarioRespuestaDto respuesta = usuarioService.cambiarRol(ID_OBJETIVO,
                new CambiarRolUsuarioDto(RolUsuario.EDITOR), ADMINISTRADOR);

        assertThat(respuesta.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(usuarioGuardado().getRol()).isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void unEditorNoPuedeCambiarElRolDeNadie() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> usuarioService.cambiarRol(ID_OBJETIVO,
                new CambiarRolUsuarioDto(RolUsuario.ADMINISTRADOR), ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdministradorNoPuedeCambiarSuPropioRolParaNoDejarLaEmpresaSinAdministrador() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_ADMINISTRADOR, ADMINISTRADOR, RolUsuario.ADMINISTRADOR, true, EMPRESA_PROPIA));

        assertThatThrownBy(() -> usuarioService.cambiarRol(ID_ADMINISTRADOR,
                new CambiarRolUsuarioDto(RolUsuario.EDITOR), ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdministradorDesactivaUnUsuarioDeSuEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.EDITOR, true, EMPRESA_PROPIA));
        devolverLoGuardado();

        UsuarioRespuestaDto respuesta = usuarioService.desactivar(ID_OBJETIVO, ADMINISTRADOR);

        assertThat(respuesta.isActivo()).isFalse();
    }

    @Test
    void elUsuarioDesactivadoSeGuardaConActivoEnFalsoConservandoSuIdentidad() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.EDITOR, true, EMPRESA_PROPIA));
        devolverLoGuardado();

        usuarioService.desactivar(ID_OBJETIVO, ADMINISTRADOR);

        Usuario guardado = usuarioGuardado();
        assertThat(guardado.isActivo()).isFalse();
        assertThat(guardado.getId()).isEqualTo(ID_OBJETIVO);
        assertThat(guardado.getUsername()).isEqualTo(NUEVO_CORREO);
        assertThat(guardado.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(guardado.getEmpresa().getId()).isEqualTo(EMPRESA_PROPIA);
    }

    @Test
    void desactivarNuncaBorraFisicamenteAlUsuario() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.EDITOR, true, EMPRESA_PROPIA));
        devolverLoGuardado();

        usuarioService.desactivar(ID_OBJETIVO, ADMINISTRADOR);

        verify(usuarioRepository, never()).delete(any(Usuario.class));
        verify(usuarioRepository, never()).deleteById(anyLong());
    }

    @Test
    void unEditorNoPuedeDesactivarUsuarios() {
        autenticar(RolUsuario.EDITOR);

        assertThatThrownBy(() -> usuarioService.desactivar(ID_OBJETIVO, ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdministradorNoPuedeDesactivarseASiMismo() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_ADMINISTRADOR, ADMINISTRADOR, RolUsuario.ADMINISTRADOR, true, EMPRESA_PROPIA));

        assertThatThrownBy(() -> usuarioService.desactivar(ID_ADMINISTRADOR, ADMINISTRADOR))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdministradorNoPuedeConsultarUnUsuarioDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(ID_OBJETIVO, EMPRESA_PROPIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtener(ID_OBJETIVO, ADMINISTRADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).findById(anyLong());
    }

    @Test
    void unAdministradorNoPuedeCambiarElRolDeUnUsuarioDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(ID_OBJETIVO, EMPRESA_PROPIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.cambiarRol(ID_OBJETIVO,
                new CambiarRolUsuarioDto(RolUsuario.ADMINISTRADOR), ADMINISTRADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdministradorNoPuedeDesactivarUnUsuarioDeOtraEmpresa() {
        autenticar(RolUsuario.ADMINISTRADOR);
        when(usuarioRepository.findByIdAndEmpresaId(ID_OBJETIVO, EMPRESA_PROPIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.desactivar(ID_OBJETIVO, ADMINISTRADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void elAislamientoSeResuelveEnLaConsultaConLaEmpresaDelAdministrador() {
        autenticar(RolUsuario.ADMINISTRADOR);
        existeEnLaEmpresa(usuario(ID_OBJETIVO, NUEVO_CORREO, RolUsuario.EDITOR, true, EMPRESA_PROPIA));

        usuarioService.obtener(ID_OBJETIVO, ADMINISTRADOR);

        verify(usuarioRepository).findByIdAndEmpresaId(ID_OBJETIVO, EMPRESA_PROPIA);
        verify(usuarioRepository, never()).findByIdAndEmpresaId(ID_OBJETIVO, EMPRESA_AJENA);
    }

    @Test
    void lasOperacionesFallanCuandoElUsuarioAutenticadoNoExiste() {
        when(usuarioRepository.findByUsername(ADMINISTRADOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.listarDeMiEmpresa(ADMINISTRADOR))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
