package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Usuario que trabaja dentro de una empresa
@Entity // se guarda en la tabla de usuarios
@Table(name = "usuario") 
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // id para guardar el usuario

    private String username; // nombre del usuario

    @Enumerated(EnumType.STRING)
    private RolUsuario rol; // rol que verifica si puede editar o solo consultar

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false) // Guarda la relación en usuario
    private Empresa empresa; // Define a qué empresa pertenece el usuario
}