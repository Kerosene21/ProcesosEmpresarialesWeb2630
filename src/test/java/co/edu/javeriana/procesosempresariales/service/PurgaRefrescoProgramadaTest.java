package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class PurgaRefrescoProgramadaTest {

    @Mock
    private FamiliaRefrescoService familiaRefrescoService;

    @Test
    void cadaEjecucionDelegaEnLaPurgaYToleraQueOtraInstanciaTengaElBloqueo() {
        when(familiaRefrescoService.purgarCaducados())
                .thenReturn(Optional.of(new PurgaRefresco(4, 2)), Optional.empty());
        PurgaRefrescoProgramada tarea = new PurgaRefrescoProgramada(familiaRefrescoService);

        tarea.purgar();
        tarea.purgar();

        verify(familiaRefrescoService, times(2)).purgarCaducados();
    }

    @Test
    void laTareaUsaUnCronConfigurableEvaluadoEnUtc() throws NoSuchMethodException {
        Scheduled programacion = PurgaRefrescoProgramada.class.getMethod("purgar").getAnnotation(Scheduled.class);

        assertThat(programacion.cron()).isEqualTo("${seguridad.refresco.purga.cron:0 17 * * * *}");
        assertThat(programacion.zone()).isEqualTo("UTC");
    }
}
