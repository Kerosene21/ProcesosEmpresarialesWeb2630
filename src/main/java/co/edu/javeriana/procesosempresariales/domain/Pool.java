package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// pool inicial del diagrama identifica a la empresa participante
@Entity // pool se guarda en la base de datos
@Table(name = "pool") // Queda separado para editarlo si algo
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Pool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // identificador de pool

    private String nombre; // crear el proceso se pone el nombre de la empresa
}