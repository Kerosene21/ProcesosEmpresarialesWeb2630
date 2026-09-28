package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.TipoEvento;
import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FlujoMensajeDto;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.DiagramaProcesoService;

@ExtendWith(MockitoExtension.class)
class DiagramaProcesoRestControllerTest {

    private static final String USERNAME = "lector@andes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/diagrama";

    @Mock
    private DiagramaProcesoService diagramaProcesoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DiagramaProcesoRestController(diagramaProcesoService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void elDiagramaIncluyeLosFlujosDeMensaje() throws Exception {
        DiagramaProcesoDto diagrama = new DiagramaProcesoDto();
        diagrama.setProcesoId(5L);
        diagrama.setFlujosMensaje(List.of(new FlujoMensajeDto(TipoEvento.MESSAGE_THROW, 40L, "Solicitud de pago", 80L,
                90L, 41L)));
        when(diagramaProcesoService.obtener(5L, USERNAME)).thenReturn(diagrama);

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesoId").value(5))
                .andExpect(jsonPath("$.pools").isArray())
                .andExpect(jsonPath("$.messageThrows").isArray())
                .andExpect(jsonPath("$.flujosMensaje[0].tipoEvento").value("MESSAGE_THROW"))
                .andExpect(jsonPath("$.flujosMensaje[0].poolDestinoId").value(90))
                .andExpect(jsonPath("$.flujosMensaje[0].catchDestinoId").value(41));
    }

    @Test
    void unProcesoAjenoResponde403() throws Exception {
        when(diagramaProcesoService.obtener(5L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }
}
