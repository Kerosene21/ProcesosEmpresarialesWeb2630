package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;

@WebMvcTest(controllers = EmpresaRestController.class)
@Import(SecurityConfig.class)
class EmpresaRestControllerTest {

    private static final String RUTA = "/api/empresas";
    private static final String RUTA_ACTUAL = "/api/empresas/actual";
    private static final String CORREO = "admin@alpes.com";
    private static final String CODIGO = "$.codigo";
    private static final String JSON_VALIDO = """
            {"nombre":"Alpes Logistica","nit":"900123456-7","correoContacto":"admin@alpes.com",
             "passwordAdministrador":"Clave-Inicial-2026"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmpresaService empresaService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private EmpresaRespuestaDto empresa() {
        EmpresaRespuestaDto empresa = new EmpresaRespuestaDto();
        empresa.setId(10L);
        empresa.setNombre("Alpes Logistica");
        empresa.setNit("900123456-7");
        empresa.setCorreoContacto(CORREO);
        empresa.setAdministradorUsername(CORREO);
        return empresa;
    }

    @Test
    void elRegistroEsPublicoYRespondeCreadoSinDevolverLaContrasena() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class))).thenReturn(empresa());

        String cuerpo = mockMvc.perform(post(RUTA).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.nit").value("900123456-7"))
                .andExpect(jsonPath("$.administradorUsername").value(CORREO))
                .andExpect(jsonPath("$.passwordAdministrador").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<RegistroEmpresaDto> registro = ArgumentCaptor.forClass(RegistroEmpresaDto.class);
        verify(empresaService).registrar(registro.capture());
        assertThat(registro.getValue().getNombre()).isEqualTo("Alpes Logistica");
        assertThat(registro.getValue().getPasswordAdministrador()).isEqualTo("Clave-Inicial-2026");
        assertThat(cuerpo).doesNotContain("Clave-Inicial-2026");
    }

    @Test
    void elRegistroConservaLaProteccionCsrf() throws Exception {
        mockMvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));

        verify(empresaService, never()).registrar(any(RegistroEmpresaDto.class));
    }

    @Test
    void losCamposObligatoriosSeValidanYCadaErrorSeAsociaASuCampo() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"nit\":\" \",\"correoContacto\":\"\",\"passwordAdministrador\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("DATOS_NO_VALIDOS"))
                .andExpect(jsonPath("$.mensaje").value("Hay campos con valores no válidos"))
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.nit").value("El NIT es obligatorio"))
                .andExpect(jsonPath("$.campos.correoContacto").exists())
                .andExpect(jsonPath("$.campos.passwordAdministrador").exists());

        verify(empresaService, never()).registrar(any(RegistroEmpresaDto.class));
    }

    @Test
    void laContrasenaDelAdministradorExigeAlMenosOchoCaracteresYElCorreoUnFormatoValido() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Alpes","nit":"900123456-7","correoContacto":"no-es-correo",
                         "passwordAdministrador":"corta12"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.passwordAdministrador")
                        .value("La contrasena debe tener entre 8 y 100 caracteres"))
                .andExpect(jsonPath("$.campos.correoContacto")
                        .value("El correo de contacto no tiene un formato valido"))
                .andExpect(jsonPath("$.campos.nombre").doesNotExist());
    }

    @Test
    void losLimitesDeLongitudDelDtoSeRespetan() throws Exception {
        String json = "{\"nombre\":\"" + "N".repeat(151) + "\",\"nit\":\"" + "1".repeat(21)
                + "\",\"correoContacto\":\"admin@alpes.com\",\"passwordAdministrador\":\"" + "c".repeat(101) + "\"}";

        mockMvc.perform(post(RUTA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").value("El nombre no puede superar 150 caracteres"))
                .andExpect(jsonPath("$.campos.nit").value("El NIT no puede superar 20 caracteres"))
                .andExpect(jsonPath("$.campos.passwordAdministrador")
                        .value("La contrasena debe tener entre 8 y 100 caracteres"));
    }

    @Test
    void unNitRepetidoSeReportaComoConflicto() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class)))
                .thenThrow(new NitEmpresaDuplicadoException("Ya existe una empresa registrada con el NIT 900123456-7"));

        mockMvc.perform(post(RUTA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("EMPRESA_NIT_DUPLICADO"));
    }

    @Test
    void unCorreoDeAdministradorEnUsoSeReportaComoConflicto() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class)))
                .thenThrow(new CorreoAdministradorEnUsoException("El correo ya esta asociado a otro usuario"));

        mockMvc.perform(post(RUTA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath(CODIGO).value("USUARIO_CORREO_EN_USO"));
    }

    @Test
    void unJsonMalFormadoSeRechazaSinLlegarAlServicio() throws Exception {
        mockMvc.perform(post(RUTA).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());

        verify(empresaService, never()).registrar(any(RegistroEmpresaDto.class));
    }

    @Test
    void laEmpresaActualExigeSesion() throws Exception {
        mockMvc.perform(get(RUTA_ACTUAL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value("USUARIO_NO_AUTORIZADO"));

        verify(empresaService, never()).listarVisiblesPara(anyString());
    }

    @Test
    @WithMockUser(username = CORREO, roles = "SOLO_LECTURA")
    void laEmpresaActualEsLaDelUsuarioAutenticadoSinImportarSuRol() throws Exception {
        when(empresaService.listarVisiblesPara(CORREO)).thenReturn(List.of(empresa()));
        when(empresaService.obtenerParaUsuario(10L, CORREO)).thenReturn(empresa());

        mockMvc.perform(get(RUTA_ACTUAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.nombre").value("Alpes Logistica"));

        verify(empresaService).obtenerParaUsuario(10L, CORREO);
    }

    @Test
    @WithMockUser(username = CORREO, roles = "ADMINISTRADOR")
    void elServicioConservaElAislamientoEntreEmpresas() throws Exception {
        when(empresaService.listarVisiblesPara(CORREO)).thenReturn(List.of(empresa()));
        when(empresaService.obtenerParaUsuario(anyLong(), anyString()))
                .thenThrow(new UsuarioSinPermisoException("La empresa consultada no pertenece al usuario autenticado"));

        mockMvc.perform(get(RUTA_ACTUAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value("USUARIO_SIN_PERMISO"));
    }
}
