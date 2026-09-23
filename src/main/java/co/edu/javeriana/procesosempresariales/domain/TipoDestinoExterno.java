package co.edu.javeriana.procesosempresariales.domain;

/**
 representa los destinos permitidos para una notificacion externa
 permite documentar si el tercero recibe correo, una llamada web o un mensaje en cola
 */
public enum TipoDestinoExterno {
    CORREO,
    SERVICIO_WEB,
    COLA_MENSAJES
}
