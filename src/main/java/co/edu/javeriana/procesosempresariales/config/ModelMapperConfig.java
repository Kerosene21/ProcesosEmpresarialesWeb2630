package co.edu.javeriana.procesosempresariales.config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Clase de configuración para poner registros de componentes que son reutilizables.
@Configuration 
public class ModelMapperConfig {
    // Convertir DTOs en entidades y viceversa.
    @Bean
    ModelMapper modelMapper() {
        return new ModelMapper();
    }
}