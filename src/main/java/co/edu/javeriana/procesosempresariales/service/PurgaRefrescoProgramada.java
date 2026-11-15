package co.edu.javeriana.procesosempresariales.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PurgaRefrescoProgramada {

    static final String CRON = "${seguridad.refresco.purga.cron:0 17 * * * *}";

    private static final Logger LOGGER = LoggerFactory.getLogger(PurgaRefrescoProgramada.class);

    private FamiliaRefrescoService familiaRefrescoService;

    @Autowired
    public PurgaRefrescoProgramada(FamiliaRefrescoService familiaRefrescoService) {
        this.familiaRefrescoService = familiaRefrescoService;
    }

    @Scheduled(cron = CRON, zone = "UTC")
    public void purgar() {
        familiaRefrescoService.purgarCaducados().ifPresentOrElse(
                purga -> LOGGER.info("Purga de refresh tokens: {} tokens y {} familias eliminados",
                        purga.tokensEliminados(), purga.familiasEliminadas()),
                () -> LOGGER.info("Purga de refresh tokens omitida: otra instancia la está ejecutando"));
    }
}
