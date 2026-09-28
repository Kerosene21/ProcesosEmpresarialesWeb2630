package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;

@WebMvcTest(controllers = { RolProcesoRestController.class, AutenticacionController.class })
@Import(SecurityConfig.class)
class SeguridadRolesProcesoTest {

    private static final String USERNAME = "usuario@alpes.com";
    private static final String RUTA_ROLES = "/api/roles-proceso";
    private static final String RUTA_ROL = "/api/roles-proceso/40";

    private static final String JSON_ROL = """
            {"nombre":"Analista de credito","descripcion":"Evalua el riesgo"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RolProcesoService rolProcesoService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private RolProcesoRespuestaDto rol() {
        RolProcesoRespuestaDto rol = new RolProcesoRespuestaDto();
        rol.setId(40L);
        rol.setNombre("Analista de credito");
        rol.setDescripcion("Evalua el riesgo");
        rol.setActivo(true);
        return rol;
    }

    @Test
    void laConsultaDeRolesResponde401SinSesion() throws Exception {
        mockMvc.perform(get(RUTA_ROLES))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(rolProcesoService, never()).consultar(any(FiltroRolesProcesoDto.class), anyString());
    }

    @Test
    void laCreacionDeRolesResponde401SinSesion() throws Exception {
        mockMvc.perform(post(RUTA_ROLES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_ROL))
                .andExpect(status().isUnauthorized());

        verify(rolProcesoService, never()).crear(any(CrearRolProcesoDto.class), anyString());
    }

    @Test
    void laEdicionDeRolesResponde401SinSesion() throws Exception {
        mockMvc.perform(put(RUTA_ROL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_ROL))
                .andExpect(status().isUnauthorized());

        verify(rolProcesoService, never()).editar(anyLong(), any(EditarRolProcesoDto.class), anyString());
    }

    @Test
    void laEliminacionDeRolesResponde401SinSesion() throws Exception {
        mockMvc.perform(delete(RUTA_ROL).with(csrf())).andExpect(status().isUnauthorized());

        verify(rolProcesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "SOLO_LECTURA")
    void unUsuarioDeSoloLecturaConsultaLosRolesDeSuEmpresa() throws Exception {
        when(rolProcesoService.consultar(any(FiltroRolesProcesoDto.class), eq(USERNAME)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get(RUTA_ROLES)).andExpect(status().isOk());

        verify(rolProcesoService).consultar(any(FiltroRolesProcesoDto.class), eq(USERNAME));
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void elAdministradorCreaRolesConTokenCsrf() throws Exception {
        when(rolProcesoService.crear(any(CrearRolProcesoDto.class), eq(USERNAME))).thenReturn(rol());

        mockMvc.perform(post(RUTA_ROLES).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON_ROL))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laCreacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(post(RUTA_ROLES).contentType(MediaType.APPLICATION_JSON).content(JSON_ROL))
                .andExpect(status().isForbidden());

        verify(rolProcesoService, never()).crear(any(CrearRolProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "ADMINISTRADOR")
    void laEliminacionSinTokenCsrfSeRechaza() throws Exception {
        mockMvc.perform(delete(RUTA_ROL)).andExpect(status().isForbidden());

        verify(rolProcesoService, never()).eliminar(anyLong(), anyString());
    }
}
