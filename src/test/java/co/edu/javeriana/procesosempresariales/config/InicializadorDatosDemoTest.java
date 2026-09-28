package co.edu.javeriana.procesosempresariales.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import co.edu.javeriana.procesosempresariales.service.DatosDemoService;

@ExtendWith(MockitoExtension.class)
class InicializadorDatosDemoTest {

    private static final String VARIABLE_ADMINISTRADOR = "DEMO_ADMIN_PASSWORD";
    private static final String VARIABLE_USUARIOS = "DEMO_USER_PASSWORD";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(DatosDemoService.class, () -> mock(DatosDemoService.class))
            .withUserConfiguration(InicializadorDatosDemo.class);

    @Mock
    private DatosDemoService datosDemoService;

    private MockEnvironment entorno;

    private InicializadorDatosDemo inicializador;

    @BeforeEach
    void preparar() {
        entorno = new MockEnvironment();
        inicializador = new InicializadorDatosDemo(datosDemoService, entorno);
    }

    private String valorAleatorio() {
        return UUID.randomUUID().toString();
    }

    @Test
    void sinPerfilActivoNoExisteElInicializador() {
        contexto.run(aplicacion -> assertThat(aplicacion).doesNotHaveBean(InicializadorDatosDemo.class));
    }

    @Test
    void conOtroPerfilNoExisteElInicializador() {
        contexto.withPropertyValues("spring.profiles.active=test")
                .run(aplicacion -> assertThat(aplicacion).doesNotHaveBean(InicializadorDatosDemo.class));
    }

    @Test
    void conElPerfilDemoExisteElInicializador() {
        contexto.withPropertyValues("spring.profiles.active=demo")
                .run(aplicacion -> assertThat(aplicacion).hasSingleBean(InicializadorDatosDemo.class));
    }

    @Test
    void siElDatasetYaExisteNoExigeContrasenasNiCargaNada() {
        when(datosDemoService.estaCargado()).thenReturn(true);

        inicializador.run();

        verify(datosDemoService, never()).cargar(anyString(), anyString());
    }

    @Test
    void siFaltaLaContrasenaDelAdministradorFallaExplicitamente() {
        when(datosDemoService.estaCargado()).thenReturn(false);
        entorno.setProperty(VARIABLE_USUARIOS, valorAleatorio());

        assertThatThrownBy(() -> inicializador.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VARIABLE_ADMINISTRADOR);
        verify(datosDemoService, never()).cargar(anyString(), anyString());
    }

    @Test
    void siFaltaLaContrasenaDeLosUsuariosFallaExplicitamente() {
        when(datosDemoService.estaCargado()).thenReturn(false);
        entorno.setProperty(VARIABLE_ADMINISTRADOR, valorAleatorio());

        assertThatThrownBy(() -> inicializador.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VARIABLE_USUARIOS);
        verify(datosDemoService, never()).cargar(anyString(), anyString());
    }

    @Test
    void unaContrasenaEnBlancoSeRechaza() {
        when(datosDemoService.estaCargado()).thenReturn(false);
        entorno.setProperty(VARIABLE_ADMINISTRADOR, "          ");
        entorno.setProperty(VARIABLE_USUARIOS, valorAleatorio());

        assertThatThrownBy(() -> inicializador.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VARIABLE_ADMINISTRADOR);
        verify(datosDemoService, never()).cargar(anyString(), anyString());
    }

    @Test
    void unaContrasenaDemasiadoCortaSeRechazaSinMostrarSuValor() {
        String corta = "abc1234";
        when(datosDemoService.estaCargado()).thenReturn(false);
        entorno.setProperty(VARIABLE_ADMINISTRADOR, valorAleatorio());
        entorno.setProperty(VARIABLE_USUARIOS, corta);

        assertThatThrownBy(() -> inicializador.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VARIABLE_USUARIOS)
                .hasMessageNotContaining(corta);
        verify(datosDemoService, never()).cargar(anyString(), anyString());
    }

    @Test
    void unaContrasenaDemasiadoLargaSeRechaza() {
        when(datosDemoService.estaCargado()).thenReturn(false);
        entorno.setProperty(VARIABLE_ADMINISTRADOR, "x".repeat(101));
        entorno.setProperty(VARIABLE_USUARIOS, valorAleatorio());

        assertThatThrownBy(() -> inicializador.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(VARIABLE_ADMINISTRADOR);
        verify(datosDemoService, never()).cargar(anyString(), anyString());
    }

    @Test
    void conContrasenasValidasDelegaLaCargaEnElServicio() {
        String administrador = valorAleatorio();
        String usuarios = valorAleatorio();
        when(datosDemoService.estaCargado()).thenReturn(false);
        when(datosDemoService.cargar(administrador, usuarios)).thenReturn(true);
        entorno.setProperty(VARIABLE_ADMINISTRADOR, administrador);
        entorno.setProperty(VARIABLE_USUARIOS, usuarios);

        inicializador.run();

        verify(datosDemoService).cargar(administrador, usuarios);
    }
}
