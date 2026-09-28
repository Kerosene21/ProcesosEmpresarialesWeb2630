package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Arco;
import co.edu.javeriana.procesosempresariales.domain.Gateway;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.exception.ModeloDeProcesoNoValidoException;

@Service
public class ValidacionModeloService {

    static final int SALIDAS_MINIMAS_DE_DIVERGENCIA = 2;
    static final String DIVERGENCIA_INCOMPLETA = " se usa como divergencia con una sola salida: necesita al menos dos";
    static final String SALIDA_SIN_CONDICION = " tiene arcos de salida sin condición";
    static final String SALIDA_CON_CONDICION = " es paralelo y conserva condiciones en sus arcos de salida";
    static final String MODELO_INCOMPLETO = "El proceso no puede salir de borrador: ";

    private GatewayService gatewayService;
    private ConexionesService conexionesService;

    @Autowired
    public ValidacionModeloService(GatewayService gatewayService, ConexionesService conexionesService) {
        this.gatewayService = gatewayService;
        this.conexionesService = conexionesService;
    }

    @Transactional(readOnly = true)
    public void validarParaSalirDeBorrador(Proceso proceso) {
        List<String> problemas = problemasDelModelo(proceso);
        if (!problemas.isEmpty()) {
            throw new ModeloDeProcesoNoValidoException(MODELO_INCOMPLETO + String.join("; ", problemas));
        }
    }

    @Transactional(readOnly = true)
    public List<String> problemasDelModelo(Proceso proceso) {
        List<String> problemas = new ArrayList<>();
        for (Gateway gateway : gatewayService.activosDelProceso(proceso)) {
            revisarGateway(proceso, gateway, problemas);
        }
        return problemas;
    }

    private void revisarGateway(Proceso proceso, Gateway gateway, List<String> problemas) {
        List<Arco> salientes = conexionesService.salientesActivos(proceso.getId(), TipoNodoFlujo.GATEWAY,
                gateway.getId());
        if (salientes.isEmpty()) {
            return;
        }
        if (salientes.size() < SALIDAS_MINIMAS_DE_DIVERGENCIA) {
            problemas.add(gateway.etiqueta() + DIVERGENCIA_INCOMPLETA);
        }
        if (gateway.getTipo().exigeCondicion()) {
            if (salientes.stream().anyMatch(arco -> esVacio(arco.getCondicion()))) {
                problemas.add(gateway.etiqueta() + SALIDA_SIN_CONDICION);
            }
        } else if (salientes.stream().anyMatch(arco -> !esVacio(arco.getCondicion()))) {
            problemas.add(gateway.etiqueta() + SALIDA_CON_CONDICION);
        }
    }

    private boolean esVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
