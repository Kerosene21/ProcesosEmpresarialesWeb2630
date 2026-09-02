package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Información básica del proceso que se guarda en la base de datos y se muestra al cliente
@Entity // Cada objeto representa un registro de la tabla proceso
// La base de datos también controla que el nombre no se repita en una empresa
@Table(name = "proceso", uniqueConstraints = @UniqueConstraint(name = "uk_proceso_empresa_nombre", columnNames = {"empresa_id", "nombre"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Proceso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Se asigna al guardar el proceso

    @Column(nullable = false, length = 150)
    private String nombre; // No se puede repetir dentro de la misma empresa

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion; // Explica para qué sirve el proceso

    @Column(nullable = false, length = 100)
    private String categoria; // Ayuda a clasificar los procesos

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoProceso estado; // Empieza como borrador y luego a publicado

    // evitar consultas que no son necesarias entonces se trae solo cuando hace falta para trabajar con el proceso
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa; // Define quién es dueño del proceso

    // El pool se crea con el proceso y tambien se elimina con el
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, optional = false)
    @JoinColumn(name = "pool_id", nullable = false)
    private Pool pool; // Punto inicial para empezar a dibujar el proceso 
}