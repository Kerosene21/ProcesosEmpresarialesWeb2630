package co.edu.javeriana.procesosempresariales.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.service.DatosDemoService;

@Component
@Profile("demo")
public class InicializadorDatosDemo implements CommandLineRunner {

    private static final String VARIABLE_ADMINISTRADOR = "DEMO_ADMIN_PASSWORD";
    private static final String VARIABLE_USUARIOS = "DEMO_USER_PASSWORD";
    private static final Logger LOGGER = LoggerFactory.getLogger(InicializadorDatosDemo.class);
    private static final int LONGITUD_MINIMA = 8;
    private static final int LONGITUD_MAXIMA = 100;

    private DatosDemoService datosDemoService;
    private Environment environment;

    @Autowired
    public InicializadorDatosDemo(DatosDemoService datosDemoService, Environment environment) {
        this.datosDemoService = datosDemoService;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        if (datosDemoService.estaCargado()) {
            LOGGER.info("El dataset demo ya existe: no se modifica");
            return;
        }
        String passwordAdministrador = exigir(VARIABLE_ADMINISTRADOR);
        String passwordUsuarios = exigir(VARIABLE_USUARIOS);
        if (datosDemoService.cargar(passwordAdministrador, passwordUsuarios)) {
            LOGGER.info("Dataset demo creado");
        }
    }

    private String exigir(String variable) {
        String valor = environment.getProperty(variable);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("El perfil demo necesita la variable " + variable
                    + " para crear el dataset demo");
        }
        if (valor.length() < LONGITUD_MINIMA || valor.length() > LONGITUD_MAXIMA) {
            throw new IllegalStateException("La variable " + variable + " debe tener entre " + LONGITUD_MINIMA
                    + " y " + LONGITUD_MAXIMA + " caracteres");
        }
        return valor;
    }
}
