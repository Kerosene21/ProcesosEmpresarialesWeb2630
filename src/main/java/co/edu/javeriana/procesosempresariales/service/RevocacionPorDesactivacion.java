package co.edu.javeriana.procesosempresariales.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.domain.UsuarioDesactivado;

@Component
public class RevocacionPorDesactivacion {

    private FamiliaRefrescoService familiaRefrescoService;

    @Autowired
    public RevocacionPorDesactivacion(FamiliaRefrescoService familiaRefrescoService) {
        this.familiaRefrescoService = familiaRefrescoService;
    }

    @EventListener
    public void alDesactivar(UsuarioDesactivado evento) {
        familiaRefrescoService.revocarDelUsuario(evento.usuarioId(), MotivoRevocacion.USUARIO_DESACTIVADO);
    }
}
