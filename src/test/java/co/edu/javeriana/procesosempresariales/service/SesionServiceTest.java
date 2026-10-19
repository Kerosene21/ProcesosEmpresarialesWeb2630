package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.SesionDto;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class SesionServiceTest {

    private static final String CORREO = "editor@alpes.com";

    @Mock
    private UsuarioRepository usuarioRepository;

    private SesionService sesionService;

    @BeforeEach
    void inicializar() {
        UsuarioService usuarioService = new UsuarioService(usuarioRepository, new ModelMapper(),
                new BCryptPasswordEncoder());
        sesionService = new SesionService(usuarioService);
    }

    @Test
    void laSesionExponeCorreoRolYEmpresaDelUsuarioSinSuContrasena() {
        Empresa empresa = new Empresa(10L, "Alpes Logistica", "900123456-7", "admin@alpes.com");
        when(usuarioRepository.findByUsername(CORREO))
                .thenReturn(Optional.of(new Usuario(3L, CORREO, "$2a$10$hash", RolUsuario.EDITOR, true, empresa)));

        SesionDto sesion = sesionService.consultar(CORREO);

        assertThat(sesion.isAutenticado()).isTrue();
        assertThat(sesion.getCorreo()).isEqualTo(CORREO);
        assertThat(sesion.getRol()).isEqualTo(RolUsuario.EDITOR);
        assertThat(sesion.getEmpresaId()).isEqualTo(10L);
        assertThat(sesion.getEmpresaNombre()).isEqualTo("Alpes Logistica");
        assertThat(SesionDto.class.getDeclaredFields()).noneMatch(campo -> campo.getName().contains("password"));
    }

    @Test
    void unUsuarioQueYaNoExisteNoProduceUnaSesion() {
        when(usuarioRepository.findByUsername(CORREO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sesionService.consultar(CORREO)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void unaSesionAnonimaNoTieneDatosDeUsuario() {
        SesionDto anonima = new SesionDto();

        assertThat(anonima.isAutenticado()).isFalse();
        assertThat(anonima.getCorreo()).isNull();
        assertThat(anonima.getRol()).isNull();
        assertThat(anonima.getEmpresaId()).isNull();
    }
}
