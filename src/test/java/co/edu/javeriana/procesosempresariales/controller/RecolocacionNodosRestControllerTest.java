package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.RecolocarNodosDto;
import co.edu.javeriana.procesosempresariales.exception.ApiExceptionHandler;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.service.RecolocacionNodosService;

@ExtendWith(MockitoExtension.class)
class RecolocacionNodosRestControllerTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/diagrama/posiciones";
    private static final String JSON = """
            {"nodos":[{"tipo":"ACTIVIDAD","id":30,"posicionX":100,"posicionY":200},
                      {"tipo":"EVENTO","id":41,"posicionX":40,"posicionY":200}]}
            """;

    @Mock
    private RecolocacionNodosService recolocacionNodosService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RecolocacionNodosRestController(recolocacionNodosService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void recolocaLosNodosYRespondeSinContenido() throws Exception {
        mockMvc.perform(put(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isNoContent());

        ArgumentCaptor<RecolocarNodosDto> capturado = ArgumentCaptor.forClass(RecolocarNodosDto.class);
        verify(recolocacionNodosService).recolocar(eq(5L), capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getNodos()).hasSize(2);
        assertThat(capturado.getValue().getNodos().get(1).getTipo()).isEqualTo(TipoNodoFlujo.EVENTO);
    }

    @Test
    void rechazaUnaListaVaciaOPosicionesFueraDelLienzo() throws Exception {
        mockMvc.perform(put(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodos\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nodos").value("Indica al menos un nodo para recolocar"));
        mockMvc.perform(put(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodos\":[{\"tipo\":\"GATEWAY\",\"id\":12,\"posicionX\":-1,\"posicionY\":60}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_NO_VALIDOS"));

        verify(recolocacionNodosService, never()).recolocar(anyLong(), any(RecolocarNodosDto.class), anyString());
    }

    @Test
    void unNodoInexistenteDevuelveCuatrocientosCuatro() throws Exception {
        doThrow(new RecursoNoEncontradoException("El evento #41 no existe en este proceso o ya fue eliminado"))
                .when(recolocacionNodosService).recolocar(anyLong(), any(RecolocarNodosDto.class), anyString());

        mockMvc.perform(put(RUTA).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON).content(JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}
