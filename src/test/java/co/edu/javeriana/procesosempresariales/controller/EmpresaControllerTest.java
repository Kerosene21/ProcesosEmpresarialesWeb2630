package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;

@ExtendWith(MockitoExtension.class)
class EmpresaControllerTest {

    @Mock
    private EmpresaService empresaService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EmpresaController(empresaService)).build();
    }

    private EmpresaRespuestaDto empresaRegistrada() {
        EmpresaRespuestaDto empresa = new EmpresaRespuestaDto();
        empresa.setId(10L);
        empresa.setNombre("Alpes Logistica");
        empresa.setNit("900123456-7");
        empresa.setCorreoContacto("contacto@alpes.com");
        empresa.setAdministradorUsername("contacto@alpes.com");
        return empresa;
    }

    @Test
    void elListadoMuestraLasEmpresasRegistradas() throws Exception {
        when(empresaService.listar()).thenReturn(List.of(empresaRegistrada()));

        MvcResult resultado = mockMvc.perform(get("/empresas"))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/lista"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<EmpresaRespuestaDto> empresas =
                (List<EmpresaRespuestaDto>) resultado.getModelAndView().getModel().get("empresas");
        assertThat(empresas).hasSize(1);
        assertThat(empresas.get(0).getNombre()).isEqualTo("Alpes Logistica");
        assertThat(empresas.get(0).getNit()).isEqualTo("900123456-7");
    }

    @Test
    void elFormularioDeRegistroSeAbreConUnDtoVacio() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/empresas/nueva"))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/formulario"))
                .andReturn();

        RegistroEmpresaDto empresa = (RegistroEmpresaDto) resultado.getModelAndView().getModel().get("empresa");
        assertThat(empresa).isNotNull();
        assertThat(empresa.getNit()).isNull();
    }

    @Test
    void registrarUnaEmpresaValidaRedirigeASuDetalle() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class))).thenReturn(empresaRegistrada());

        mockMvc.perform(post("/empresas")
                .param("nombre", "Alpes Logistica")
                .param("nit", "900123456-7")
                .param("correoContacto", "contacto@alpes.com"))
                .andExpect(redirectedUrl("/empresas/10"))
                .andExpect(flash().attribute("mensaje",
                        "Empresa registrada correctamente junto a su usuario administrador inicial"));

        ArgumentCaptor<RegistroEmpresaDto> enviado = ArgumentCaptor.forClass(RegistroEmpresaDto.class);
        verify(empresaService).registrar(enviado.capture());
        assertThat(enviado.getValue().getNombre()).isEqualTo("Alpes Logistica");
        assertThat(enviado.getValue().getNit()).isEqualTo("900123456-7");
        assertThat(enviado.getValue().getCorreoContacto()).isEqualTo("contacto@alpes.com");
    }

    @Test
    void registrarVuelveAlFormularioCuandoLaValidacionFalla() throws Exception {
        mockMvc.perform(post("/empresas")
                .param("nombre", "")
                .param("nit", "")
                .param("correoContacto", "no-es-un-correo"))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/formulario"))
                .andExpect(model().attributeHasFieldErrors("empresa", "nombre", "nit", "correoContacto"));

        verify(empresaService, never()).registrar(any(RegistroEmpresaDto.class));
    }

    @Test
    void registrarSenalaElCampoNitCuandoYaEstaRegistrado() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class)))
                .thenThrow(new NitEmpresaDuplicadoException("Ya existe una empresa registrada con el NIT 900123456-7"));

        mockMvc.perform(post("/empresas")
                .param("nombre", "Alpes Logistica")
                .param("nit", "900123456-7")
                .param("correoContacto", "contacto@alpes.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/formulario"))
                .andExpect(model().attributeHasFieldErrors("empresa", "nit"));
    }

    @Test
    void registrarSenalaElCampoCorreoCuandoYaEstaEnUso() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class)))
                .thenThrow(new CorreoAdministradorEnUsoException(
                        "El correo contacto@alpes.com ya esta asociado a otro usuario"));

        mockMvc.perform(post("/empresas")
                .param("nombre", "Alpes Logistica")
                .param("nit", "900123456-7")
                .param("correoContacto", "contacto@alpes.com"))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/formulario"))
                .andExpect(model().attributeHasFieldErrors("empresa", "correoContacto"));
    }

    @Test
    void elDetalleMuestraLaEmpresaSolicitada() throws Exception {
        when(empresaService.obtener(10L)).thenReturn(empresaRegistrada());

        MvcResult resultado = mockMvc.perform(get("/empresas/10"))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/detalle"))
                .andReturn();

        EmpresaRespuestaDto empresa = (EmpresaRespuestaDto) resultado.getModelAndView().getModel().get("empresa");
        assertThat(empresa.getId()).isEqualTo(10L);
        assertThat(empresa.getNombre()).isEqualTo("Alpes Logistica");
        assertThat(empresa.getAdministradorUsername()).isEqualTo("contacto@alpes.com");
    }
}
