package co.edu.javeriana.procesosempresariales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "empresa", uniqueConstraints = @UniqueConstraint(name = "uk_empresa_nit", columnNames = "nit"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Empresa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String nit;

    @Column(name = "correo_contacto", nullable = false, length = 180)
    private String correoContacto;
}
