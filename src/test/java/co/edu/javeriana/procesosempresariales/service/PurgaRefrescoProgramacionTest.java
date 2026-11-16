package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import co.edu.javeriana.procesosempresariales.config.ProgramacionConfig;

@SpringJUnitConfig(classes = { ProgramacionConfig.class, PurgaRefrescoProgramada.class })
@TestPropertySource(properties = "seguridad.refresco.purga.cron=0 17 * * * *")
class PurgaRefrescoProgramacionTest {

    @MockitoBean
    private FamiliaRefrescoService familiaRefrescoService;

    @Autowired
    private ScheduledTaskHolder tareas;

    @Test
    void conLaConfiguracionPorDefectoLaPurgaQuedaProgramadaCadaHora() {
        assertThat(tareas.getScheduledTasks())
                .extracting(ScheduledTask::getTask)
                .singleElement()
                .isInstanceOfSatisfying(CronTask.class,
                        tarea -> assertThat(tarea.getExpression()).isEqualTo("0 17 * * * *"));
    }

    @Nested
    @TestPropertySource(properties = "seguridad.refresco.purga.cron=-")
    class Desactivada {

        @Autowired
        private ScheduledTaskHolder tareasDesactivadas;

        @Test
        void unCronGuionNoProgramaNingunaTarea() {
            assertThat(tareasDesactivadas.getScheduledTasks()).isEmpty();
        }
    }
}
