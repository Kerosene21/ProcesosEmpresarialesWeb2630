package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.ApiExceptionHandler;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class UsuarioRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_USUARIOS = "/api/usuarios";
    private static final String RUTA_USUARIO = "/api/usuarios/15";
    private static final String CORREO_EDITOR = "editor@alpes.com";
    private static final String CODIGO = "$.codigo";

    private static final String JSON_USUARIO = """
            {"correo":"editor@alpes.com","password":"Clave-Inicial-2026","rol":"EDITOR"}
            """;

    private static final String JSON_NO_VALIDO = """
            {"correo":"sin-arroba","password":"corta"}
            """;

    private static final String JSON_ROL = """
            {"rol":"SOLO_LECTURA"}
            """;

    @Mock
    private UsuarioService usuarioService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UsuarioRestController(usuarioService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private UsuarioRespuestaDto usuario(RolUsuario rol, boolean activo) {
        UsuarioRespuestaDto usuario = new UsuarioRespuestaDto();
        usuario.setId(15L);
        usuario.setCorreo(CORREO_EDITOR);
        usuario.setRol(rol);
        usuario.setActivo(activo);
        return usuario;
    }

    @Test
    void listarDevuelveLosUsuariosDeLaEmpresaSinContrasenas() throws Exception {
        when(usuarioService.listarDeMiEmpresa(USERNAME)).thenReturn(List.of(usuario(RolUsuario.EDITOR, true)));

        mockMvc.perform(get(RUTA_USUARIOS).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(15))
                .andExpect(jsonPath("$[0].correo").value(CORREO_EDITOR))
                .andExpect(jsonPath("$[0].rol").value("EDITOR"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void listarDevuelve403SiElUsuarioNoEsAdministrador() throws Exception {
        when(usuarioService.listarDeMiEmpresa(USERNAME))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador puede gestionar los usuarios"));

        mockMvc.perform(get(RUTA_USUARIOS).principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void obtenerDevuelveElUsuarioYUn404SiEsDeOtraEmpresa() throws Exception {
        when(usuarioService.obtener(15L, USERNAME)).thenReturn(usuario(RolUsuario.EDITOR, true));
        when(usuarioService.obtener(16L, USERNAME))
                .thenThrow(new RecursoNoEncontradoException("El usuario no existe en tu empresa"));

        mockMvc.perform(get(RUTA_USUARIO).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(CORREO_EDITOR));
        mockMvc.perform(get("/api/usuarios/16").principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(CODIGO).value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void crearDevuelve201ConLaUbicacionYDelegaEnElServicio() throws Exception {
        when(usuarioService.crear(any(CrearUsuarioDto.class), eq(USERNAME))).thenReturn(usuario(RolUsuario.EDITOR, true));

        mockMvc.perform(post(RUTA_USUARIOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_USUARIO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/usuarios/15"))
                .andExpect(jsonPath("$.rol").value("EDITOR"))
                .andExpect(jsonPath("$.password").doesNotExist());

        ArgumentCaptor<CrearUsuarioDto> capturado = ArgumentCaptor.forClass(CrearUsuarioDto.class);
        verify(usuarioService).crear(capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getCorreo()).isEqualTo(CORREO_EDITOR);
        assertThat(capturado.getValue().getPassword()).isEqualTo("Clave-Inicial-2026");
        assertThat(capturado.getValue().getRol()).isEqualTo(RolUsuario.EDITOR);
    }

    @Test
    void crearDevuelve400ConLosCamposNoValidosYNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post(RUTA_USUARIOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_NO_VALIDO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("DATOS_NO_VALIDOS"))
                .andExpect(jsonPath("$.campos.correo").value("El correo no tiene un formato valido"))
                .andExpect(jsonPath("$.campos.password").value("La contrasena debe tener entre 8 y 100 caracteres"))
                .andExpect(jsonPath("$.campos.rol").value("El rol de acceso es obligatorio"));

        verify(usuarioService, never()).crear(any(CrearUsuarioDto.class), anyString());
    }

    @Test
    void crearDevuelve409SiElCorreoYaEstaEnUso() throws Exception {
        when(usuarioService.crear(any(CrearUsuarioDto.class), eq(USERNAME)))
                .thenThrow(new CorreoAdministradorEnUsoException("El correo editor@alpes.com ya esta asociado a otro usuario"));

        mockMvc.perform(post(RUTA_USUARIOS).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_USUARIO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("USUARIO_CORREO_EN_USO"));
    }

    @Test
    void cambiarRolDevuelveElUsuarioActualizado() throws Exception {
        when(usuarioService.cambiarRol(eq(15L), any(CambiarRolUsuarioDto.class), eq(USERNAME)))
                .thenReturn(usuario(RolUsuario.SOLO_LECTURA, true));

        mockMvc.perform(put(RUTA_USUARIO + "/rol").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SOLO_LECTURA"));

        ArgumentCaptor<CambiarRolUsuarioDto> capturado = ArgumentCaptor.forClass(CambiarRolUsuarioDto.class);
        verify(usuarioService).cambiarRol(eq(15L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getRol()).isEqualTo(RolUsuario.SOLO_LECTURA);
    }

    @Test
    void cambiarRolSinRolDevuelve400YSobreLaPropiaCuentaDevuelve403() throws Exception {
        when(usuarioService.cambiarRol(eq(15L), any(CambiarRolUsuarioDto.class), eq(USERNAME)))
                .thenThrow(new UsuarioSinPermisoException("Un administrador no puede cambiar su propio rol"));

        mockMvc.perform(put(RUTA_USUARIO + "/rol").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.rol").value("El rol de acceso es obligatorio"));
        mockMvc.perform(put(RUTA_USUARIO + "/rol").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void desactivarDevuelveElUsuarioInactivo() throws Exception {
        when(usuarioService.desactivar(15L, USERNAME)).thenReturn(usuario(RolUsuario.EDITOR, false));

        mockMvc.perform(post(RUTA_USUARIO + "/desactivacion").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void desactivarLaPropiaCuentaDevuelve403() throws Exception {
        when(usuarioService.desactivar(15L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("Un administrador no puede desactivar su propia cuenta"));

        mockMvc.perform(post(RUTA_USUARIO + "/desactivacion").principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
    }
}
