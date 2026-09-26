package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;

@ExtendWith(MockitoExtension.class)
class HistorialProcesoServiceTest {

    private static final Long PROCESO_ID = 5L;
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final String USERNAME = "admin@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    private HistorialProcesoService historialProcesoService;

    @BeforeEach
    void inicializar() {
        historialProcesoService = new HistorialProcesoService(historialProcesoRepository);
    }

    private Empresa empresa() {
        return new Empresa(EMPRESA_PROPIA, "Alpes Logistica", "900123456-7", "contacto@alpes.com");
    }

    private Proceso proceso(EstadoProceso estado) {
        return new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", estado, empresa(),
                new Pool(80L, "Alpes Logistica", List.of()), false);
    }

    private Usuario usuario() {
        return new Usuario(1L, USERNAME, HASH, RolUsuario.ADMINISTRADOR, true, empresa());
    }

    private HistorialProceso historialGuardado() {
        ArgumentCaptor<HistorialProceso> capturado = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(capturado.capture());
        return capturado.getValue();
    }

    @Test
    void registrarConservaProcesoUsuarioFechaCambiosYEstadoAnteriorIndicado() {
        Proceso proceso = proceso(EstadoProceso.PUBLICADO);
        Usuario usuario = usuario();
        LocalDateTime antes = LocalDateTime.now();

        historialProcesoService.registrar(proceso, usuario, "estado: 'BORRADOR' -> 'PUBLICADO'", "BORRADOR");

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getId()).isNull();
        assertThat(historial.getProceso()).isSameAs(proceso);
        assertThat(historial.getUsuario()).isSameAs(usuario);
        assertThat(historial.getCambiosRealizados()).isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
        assertThat(historial.getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(historial.getFecha()).isBetween(antes, LocalDateTime.now());
    }

    @Test
    void registrarSinEstadoAnteriorUsaElEstadoActualDelProceso() {
        historialProcesoService.registrar(proceso(EstadoProceso.BORRADOR), usuario(), "actividad creada: 'Revisar'");

        HistorialProceso historial = historialGuardado();
        assertThat(historial.getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(historial.getCambiosRealizados()).isEqualTo("actividad creada: 'Revisar'");
    }

    @Test
    void registrarSiempreCreaUnRegistroNuevoSinBorrarLosAnteriores() {
        historialProcesoService.registrar(proceso(EstadoProceso.BORRADOR), usuario(), "gateway creado");

        assertThat(historialGuardado().getId()).isNull();
        verify(historialProcesoRepository, never()).delete(any(HistorialProceso.class));
        verify(historialProcesoRepository, never()).deleteById(anyLong());
        verify(historialProcesoRepository, never()).deleteAll();
    }

    @Test
    void consultarDelProcesoFiltraPorProcesoYEmpresaYExponeSoloElCorreoDelUsuario() {
        LocalDateTime fecha = LocalDateTime.of(2026, 9, 1, 10, 30);
        HistorialProceso registro = new HistorialProceso(40L, proceso(EstadoProceso.PUBLICADO), usuario(), fecha,
                "nombre: 'A' -> 'B'", "BORRADOR");
        when(historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(PROCESO_ID,
                EMPRESA_PROPIA)).thenReturn(List.of(registro));

        List<HistorialProcesoRespuestaDto> historial = historialProcesoService.consultarDelProceso(PROCESO_ID,
                EMPRESA_PROPIA);

        assertThat(historial).hasSize(1);
        HistorialProcesoRespuestaDto respuesta = historial.get(0);
        assertThat(respuesta.getFecha()).isEqualTo(fecha);
        assertThat(respuesta.getUsuarioCorreo()).isEqualTo(USERNAME);
        assertThat(respuesta.getEstadoAnterior()).isEqualTo("BORRADOR");
        assertThat(respuesta.getCambiosRealizados()).isEqualTo("nombre: 'A' -> 'B'");
        verify(historialProcesoRepository, never()).findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(PROCESO_ID,
                EMPRESA_AJENA);
    }

    @Test
    void consultarDelProcesoSinRegistrosDevuelveUnaListaVacia() {
        when(historialProcesoRepository.findByProcesoIdAndProcesoEmpresaIdOrderByFechaDesc(PROCESO_ID,
                EMPRESA_PROPIA)).thenReturn(List.of());

        assertThat(historialProcesoService.consultarDelProceso(PROCESO_ID, EMPRESA_PROPIA)).isEmpty();
    }
}
