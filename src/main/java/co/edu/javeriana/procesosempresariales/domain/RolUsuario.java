package co.edu.javeriana.procesosempresariales.domain;

// roles que determinan si un usuario puede modificar procesos o no
public enum RolUsuario {
    ADMINISTRADOR,
    EDITOR,
    SOLO_LECTURA,
}