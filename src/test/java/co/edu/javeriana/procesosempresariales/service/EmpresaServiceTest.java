package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.EmpresaRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceTest {

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private EmpresaService empresaService;

    @BeforeEach
    void inicializar() {
        empresaService = new EmpresaService(empresaRepository, usuarioRepository, new ModelMapper());
    }

    private RegistroEmpresaDto formularioValido() {
        return new RegistroEmpresaDto("Alpes Logistica", "900123456-7", "contacto@alpes.com");
    }

    private void asignarIdAlGuardarEmpresa(Long id) {
        when(empresaRepository.save(any(Empresa.class))).thenAnswer(invocacion -> {
            Empresa empresa = invocacion.getArgument(0);
            empresa.setId(id);
            return empresa;
        });
    }

    @Test
    void registrarPersisteLaEmpresaConLosDatosDelFormulario() {
        asignarIdAlGuardarEmpresa(10L);

        empresaService.registrar(formularioValido());

        ArgumentCaptor<Empresa> capturada = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getNombre()).isEqualTo("Alpes Logistica");
        assertThat(capturada.getValue().getNit()).isEqualTo("900123456-7");
        assertThat(capturada.getValue().getCorreoContacto()).isEqualTo("contacto@alpes.com");
    }

    @Test
    void registrarNormalizaEspaciosYMayusculasDelCorreoAntesDePersistir() {
        asignarIdAlGuardarEmpresa(11L);

        empresaService.registrar(
                new RegistroEmpresaDto("  Alpes Logistica  ", "  900123456-7  ", "  Contacto@Alpes.com  "));

        ArgumentCaptor<Empresa> capturada = ArgumentCaptor.forClass(Empresa.class);
        verify(empresaRepository).save(capturada.capture());
        assertThat(capturada.getValue().getNombre()).isEqualTo("Alpes Logistica");
        assertThat(capturada.getValue().getNit()).isEqualTo("900123456-7");
        assertThat(capturada.getValue().getCorreoContacto()).isEqualTo("contacto@alpes.com");
    }

    @Test
    void registrarDevuelveLaEmpresaConElIdentificadorAsignado() {
        asignarIdAlGuardarEmpresa(42L);

        EmpresaRespuestaDto respuesta = empresaService.registrar(formularioValido());

        assertThat(respuesta.getId()).isEqualTo(42L);
        assertThat(respuesta.getNombre()).isEqualTo("Alpes Logistica");
        assertThat(respuesta.getNit()).isEqualTo("900123456-7");
        assertThat(respuesta.getCorreoContacto()).isEqualTo("contacto@alpes.com");
    }

    @Test
    void registrarRechazaUnNitYaRegistrado() {
        when(empresaRepository.existsByNit("900123456-7")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(formularioValido()))
                .isInstanceOf(NitEmpresaDuplicadoException.class);

        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrarVerificaLaUnicidadDelNitSobreElValorNormalizado() {
        when(empresaRepository.existsByNit("900123456-7")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(
                new RegistroEmpresaDto("Alpes Logistica", "  900123456-7  ", "contacto@alpes.com")))
                .isInstanceOf(NitEmpresaDuplicadoException.class);
    }

    @Test
    void registrarCreaElAdministradorInicialAsociadoALaEmpresaCreada() {
        asignarIdAlGuardarEmpresa(7L);

        empresaService.registrar(formularioValido());

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        assertThat(capturado.getValue().getEmpresa()).isNotNull();
        assertThat(capturado.getValue().getEmpresa().getId()).isEqualTo(7L);
        assertThat(capturado.getValue().getEmpresa().getNit()).isEqualTo("900123456-7");
    }

    @Test
    void registrarCreaElAdministradorInicialConRolAdministrador() {
        asignarIdAlGuardarEmpresa(8L);

        empresaService.registrar(formularioValido());

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        assertThat(capturado.getValue().getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    @Test
    void registrarIdentificaAlAdministradorInicialConElCorreoDeContacto() {
        asignarIdAlGuardarEmpresa(9L);

        EmpresaRespuestaDto respuesta = empresaService.registrar(formularioValido());

        ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(capturado.capture());
        assertThat(capturado.getValue().getUsername()).isEqualTo("contacto@alpes.com");
        assertThat(respuesta.getAdministradorUsername()).isEqualTo("contacto@alpes.com");
    }

    @Test
    void registrarRechazaUnCorreoYaUsadoPorOtroUsuario() {
        when(usuarioRepository.existsByUsername("contacto@alpes.com")).thenReturn(true);

        assertThatThrownBy(() -> empresaService.registrar(formularioValido()))
                .isInstanceOf(CorreoAdministradorEnUsoException.class);

        verify(empresaRepository, never()).save(any(Empresa.class));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void obtenerDevuelveLaEmpresaJuntoASuAdministradorInicial() {
        Empresa empresa = new Empresa(5L, "Alpes Logistica", "900123456-7", "contacto@alpes.com");
        Usuario administrador = new Usuario(3L, "contacto@alpes.com", RolUsuario.ADMINISTRADOR, empresa);
        when(empresaRepository.findById(5L)).thenReturn(Optional.of(empresa));
        when(usuarioRepository.findFirstByEmpresaIdAndRolOrderByIdAsc(5L, RolUsuario.ADMINISTRADOR))
                .thenReturn(Optional.of(administrador));

        EmpresaRespuestaDto respuesta = empresaService.obtener(5L);

        assertThat(respuesta.getId()).isEqualTo(5L);
        assertThat(respuesta.getNombre()).isEqualTo("Alpes Logistica");
        assertThat(respuesta.getCorreoContacto()).isEqualTo("contacto@alpes.com");
        assertThat(respuesta.getAdministradorUsername()).isEqualTo("contacto@alpes.com");
    }

    @Test
    void obtenerFallaCuandoLaEmpresaNoExiste() {
        when(empresaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> empresaService.obtener(404L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void listarDevuelveTodasLasEmpresasRegistradas() {
        when(empresaRepository.findAll()).thenReturn(List.of(
                new Empresa(1L, "Alpes Logistica", "900123456-7", "contacto@alpes.com"),
                new Empresa(2L, "Andes Software", "800987654-3", "hola@andes.com")));

        List<EmpresaRespuestaDto> empresas = empresaService.listar();

        assertThat(empresas).hasSize(2);
        assertThat(empresas).extracting(EmpresaRespuestaDto::getNit)
                .containsExactly("900123456-7", "800987654-3");
        assertThat(empresas).extracting(EmpresaRespuestaDto::getNombre)
                .containsExactly("Alpes Logistica", "Andes Software");
    }
}
