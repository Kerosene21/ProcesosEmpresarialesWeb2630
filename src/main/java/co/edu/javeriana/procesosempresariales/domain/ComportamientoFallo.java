package co.edu.javeriana.procesosempresariales.domain;


//define la decision documentada cuando falla el envio externo y permite saber si el proceso continua, maneja el error o finaliza

public enum ComportamientoFallo {
    CONTINUAR_FLUJO,
    MANEJO_ERROR,
    FINALIZAR_PROCESO
}
