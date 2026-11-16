package co.edu.javeriana.procesosempresariales.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:salud.properties", encoding = "UTF-8")
public class SaludConfig {
}
