package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AutenticacionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AutenticacionController()).build();
    }

    @Test
    void laRutaDeLoginRenderizaSuPropiaVista() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("autenticacion/login"));
    }

    @Test
    void laRutaDeAccesoDenegadoReutilizaLaPaginaDeProblemaConUnMensajeDePermisos() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/acceso-denegado"))
                .andExpect(status().isOk())
                .andExpect(view().name("error/problema"))
                .andExpect(model().attributeExists("titulo", "detalle"))
                .andReturn();

        assertThat(resultado.getModelAndView().getModel().get("detalle").toString())
                .contains("administrador");
    }
}
