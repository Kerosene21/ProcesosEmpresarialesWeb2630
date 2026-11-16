package co.edu.javeriana.procesosempresariales.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.domain.UsuarioDesactivado;

@ExtendWith(MockitoExtension.class)
class RevocacionPorDesactivacionTest {

    @Mock
    private FamiliaRefrescoService familiaRefrescoService;

    @Test
    void alDesactivarUnUsuarioSeRevocanTodasSusFamilias() {
        new RevocacionPorDesactivacion(familiaRefrescoService).alDesactivar(new UsuarioDesactivado(7L));

        verify(familiaRefrescoService).revocarDelUsuario(7L, MotivoRevocacion.USUARIO_DESACTIVADO);
    }
}
