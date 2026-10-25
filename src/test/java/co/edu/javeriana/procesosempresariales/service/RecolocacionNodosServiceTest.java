package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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

import co.edu.javeriana.procesosempresariales.domain.Actividad;
import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Evento;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.HistorialProceso;
import co.edu.javeriana.procesosempresariales.domain.Lane;
import co.edu.javeriana.procesosempresariales.domain.MessageCatch;
import co.edu.javeriana.procesosempresariales.domain.Pool;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.PosicionNodoDto;
import co.edu.javeriana.procesosempresariales.dto.RecolocarNodosDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.repository.ActividadRepository;
import co.edu.javeriana.procesosempresariales.repository.EventoRepository;
import co.edu.javeriana.procesosempresariales.repository.GatewayRepository;
import co.edu.javeriana.procesosempresariales.repository.HistorialProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class RecolocacionNodosServiceTest {

    private static final String USERNAME = "editor@alpes.com";
    private static final String HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5jzHiZQ2mQ0ym2hQ0y1hQ0ym2hQ0y";
    private static final Long EMPRESA_PROPIA = 7L;
    private static final Long EMPRESA_AJENA = 99L;
    private static final Long PROCESO_ID = 5L;

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private GatewayRepository gatewayRepository;

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProcesoRepository procesoRepository;

    @Mock
    private HistorialProcesoRepository historialProcesoRepository;

    private RecolocacionNodosService recolocacionNodosService;

    private final Pool pool = new Pool(80L, null, "Alpes", TipoPool.PROPIETARIO, 1, false, true, null,
            new ArrayList<>());

    @BeforeEach
    void inicializar() {
        UsuarioService usuarios = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        AccesoProcesoService acceso = new AccesoProcesoService(usuarios, procesoRepository);
        recolocacionNodosService = new RecolocacionNodosService(actividadRepository, gatewayRepository,
                eventoRepository, acceso, new HistorialProcesoService(historialProcesoRepository));
    }

    private Empresa empresa(Long id) {
        return new Empresa(id, "Alpes Logistica", "900123456-7", "contacto@alpes.com");
    }

    private void autenticar(RolUsuario rol) {
        when(usuarioRepository.findByUsername(USERNAME))
                .thenReturn(Optional.of(new Usuario(1L, USERNAME, HASH, rol, true, empresa(EMPRESA_PROPIA))));
    }

    private Proceso existeElProceso(Long empresaId, boolean eliminado) {
        Proceso proceso = new Proceso(PROCESO_ID, "Ventas", "Proceso comercial", "Comercial", EstadoProceso.BORRADOR,
                empresa(empresaId), List.of(pool), eliminado);
        when(procesoRepository.findById(PROCESO_ID)).thenReturn(Optional.of(proceso));
        return proceso;
    }

    private Actividad existeLaActividad(Proceso proceso, Long id, int x, int y) {
        Actividad actividad = new Actividad(id, "Actividad " + id, TipoActividad.TAREA_USUARIO, proceso,
                new Lane(11L, "General", pool, null, 1, true), x, y, true);
        when(actividadRepository.findByIdAndProcesoId(id, PROCESO_ID)).thenReturn(Optional.of(actividad));
        return actividad;
    }

    private RecolocarNodosDto solicitud(PosicionNodoDto... nodos) {
        return new RecolocarNodosDto(List.of(nodos));
    }

    @Test
    void recolocaActividadesGatewaysYEventosEnUnaSolaOperacionYLaRegistraUnaVez() {
        autenticar(RolUsuario.EDITOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        Actividad registrar = existeLaActividad(proceso, 30L, 100, 60);
        Actividad evaluar = existeLaActividad(proceso, 31L, 300, 60);
        Gateway gateway = new Gateway(12L, TipoGateway.EXCLUSIVO, proceso, pool, 500, 70, true);
        when(gatewayRepository.findByIdAndProcesoId(12L, PROCESO_ID)).thenReturn(Optional.of(gateway));
        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setId(41L);
        messageCatch.setVariante(VarianteMessageCatch.INTERMEDIO);
        messageCatch.setPosicionX(40);
        messageCatch.setPosicionY(60);
        messageCatch.setActivo(true);
        when(eventoRepository.findByIdAndProcesoId(41L, PROCESO_ID)).thenReturn(Optional.of(messageCatch));

        recolocacionNodosService.recolocar(PROCESO_ID, solicitud(
                new PosicionNodoDto(TipoNodoFlujo.ACTIVIDAD, 30L, 100, 200),
                new PosicionNodoDto(TipoNodoFlujo.ACTIVIDAD, 31L, 300, 60),
                new PosicionNodoDto(TipoNodoFlujo.GATEWAY, 12L, 500, 210),
                new PosicionNodoDto(TipoNodoFlujo.EVENTO, 41L, 40, 200)), USERNAME);

        assertThat(registrar.getPosicionY()).isEqualTo(200);
        assertThat(registrar.getLane().getId()).isEqualTo(11L);
        assertThat(evaluar.getPosicionY()).isEqualTo(60);
        assertThat(gateway.getPosicionY()).isEqualTo(210);
        assertThat(messageCatch.getPosicionY()).isEqualTo(200);
        verify(actividadRepository).save(registrar);
        verify(actividadRepository, never()).save(evaluar);
        ArgumentCaptor<HistorialProceso> historial = ArgumentCaptor.forClass(HistorialProceso.class);
        verify(historialProcesoRepository).save(historial.capture());
        assertThat(historial.getValue().getCambiosRealizados()).isEqualTo("elementos del diagrama recolocados: 3");
    }

    @Test
    void sinCambiosRealesNoRegistraHistorial() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeLaActividad(proceso, 30L, 100, 60);

        Gateway gateway = new Gateway(12L, TipoGateway.PARALELO, proceso, pool, 500, 70, true);
        when(gatewayRepository.findByIdAndProcesoId(12L, PROCESO_ID)).thenReturn(Optional.of(gateway));
        MessageCatch messageCatch = new MessageCatch();
        messageCatch.setPosicionX(40);
        messageCatch.setPosicionY(60);
        messageCatch.setActivo(true);
        when(eventoRepository.findByIdAndProcesoId(41L, PROCESO_ID)).thenReturn(Optional.of(messageCatch));

        recolocacionNodosService.recolocar(PROCESO_ID, solicitud(
                new PosicionNodoDto(TipoNodoFlujo.ACTIVIDAD, 30L, 100, 60),
                new PosicionNodoDto(TipoNodoFlujo.GATEWAY, 12L, 500, 70),
                new PosicionNodoDto(TipoNodoFlujo.EVENTO, 41L, 40, 60)), USERNAME);

        verify(actividadRepository, never()).save(any(Actividad.class));
        verify(gatewayRepository, never()).save(any(Gateway.class));
        verify(eventoRepository, never()).save(any(Evento.class));
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void unNodoAjenoOEliminadoInterrumpeLaOperacion() {
        autenticar(RolUsuario.ADMINISTRADOR);
        Proceso proceso = existeElProceso(EMPRESA_PROPIA, false);
        existeLaActividad(proceso, 30L, 100, 60);
        when(gatewayRepository.findByIdAndProcesoId(99L, PROCESO_ID)).thenReturn(Optional.empty());
        when(gatewayRepository.findByIdAndProcesoId(13L, PROCESO_ID))
                .thenReturn(Optional.of(new Gateway(13L, TipoGateway.PARALELO, proceso, pool, 500, 70, false)));
        when(actividadRepository.findByIdAndProcesoId(31L, PROCESO_ID)).thenReturn(Optional.empty());
        Evento eliminado = new MessageCatch();
        eliminado.setActivo(false);
        when(eventoRepository.findByIdAndProcesoId(41L, PROCESO_ID)).thenReturn(Optional.of(eliminado));

        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID, solicitud(
                new PosicionNodoDto(TipoNodoFlujo.ACTIVIDAD, 30L, 100, 200),
                new PosicionNodoDto(TipoNodoFlujo.GATEWAY, 99L, 500, 210)), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("El gateway #99 no existe en este proceso o ya fue eliminado");
        verify(actividadRepository, never()).save(any(Actividad.class));
        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID,
                solicitud(new PosicionNodoDto(TipoNodoFlujo.EVENTO, 41L, 40, 200)), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID,
                solicitud(new PosicionNodoDto(TipoNodoFlujo.GATEWAY, 13L, 500, 210)), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID,
                solicitud(new PosicionNodoDto(TipoNodoFlujo.ACTIVIDAD, 31L, 100, 200)), USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("La actividad #31 no existe en este proceso o ya fue eliminada");
        verify(historialProcesoRepository, never()).save(any(HistorialProceso.class));
    }

    @Test
    void elLectorUnaEmpresaAjenaYUnProcesoEliminadoNoAdmitenRecolocar() {
        RecolocarNodosDto dto = solicitud(new PosicionNodoDto(TipoNodoFlujo.ACTIVIDAD, 30L, 100, 200));
        autenticar(RolUsuario.SOLO_LECTURA);
        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class)
                .hasMessage("Solo un administrador o editor puede modificar el diagrama");

        autenticar(RolUsuario.ADMINISTRADOR);
        existeElProceso(EMPRESA_AJENA, false);
        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        existeElProceso(EMPRESA_PROPIA, true);
        assertThatThrownBy(() -> recolocacionNodosService.recolocar(PROCESO_ID, dto, USERNAME))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verifyNoInteractions(actividadRepository);
    }
}
