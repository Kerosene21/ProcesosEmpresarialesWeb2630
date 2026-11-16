import {
  ActividadRespuesta,
  ArcoRespuesta,
  DiagramaProceso,
  GatewayRespuesta,
  LaneRespuesta,
  PoolRespuesta,
} from '../app/core/modelos/diagrama';
import { Proceso } from '../app/core/modelos/proceso';

export const PROCESO_PRUEBA: Proceso = {
  id: 5,
  nombre: 'Atención de solicitudes',
  descripcion: 'Recepción y evaluación de solicitudes',
  categoria: 'Servicio al cliente',
  estado: 'BORRADOR',
  poolId: 80,
  poolNombre: 'Empresa de prueba',
  eliminado: false,
  empresaPropietariaId: 7,
  empresaPropietariaNombre: 'Empresa de prueba',
  soloLectura: false,
};

export function pool(id: number, nombre: string, orden: number, cambios: Partial<PoolRespuesta> = {}): PoolRespuesta {
  return {
    id,
    procesoId: 5,
    nombre,
    tipo: orden === 1 ? 'PROPIETARIO' : 'EXTERNO',
    orden,
    cajaNegra: false,
    activo: true,
    empresaId: orden === 1 ? 7 : null,
    empresaNombre: orden === 1 ? nombre : null,
    ...cambios,
  };
}

export function lane(id: number, nombre: string, orden: number, rolProcesoId: number | null = null): LaneRespuesta {
  return { id, nombre, rolProcesoId, poolId: 80, orden };
}

export function actividad(
  id: number,
  nombre: string,
  laneId: number,
  posicionX: number,
  posicionY: number,
  cambios: Partial<ActividadRespuesta> = {},
): ActividadRespuesta {
  return {
    id,
    nombre,
    tipo: 'TAREA_USUARIO',
    procesoId: 5,
    poolId: 80,
    laneId,
    laneNombre: laneId === 12 ? 'Supervisor' : 'General',
    posicionX,
    posicionY,
    activo: true,
    arcosDesactivados: 0,
    advertencias: [],
    ...cambios,
  };
}

export function gateway(id: number, posicionX: number, posicionY: number, cambios: Partial<GatewayRespuesta> = {}): GatewayRespuesta {
  const tipo = cambios.tipo ?? 'EXCLUSIVO';
  return {
    id,
    procesoId: 5,
    poolId: 80,
    tipo,
    simbolo: { EXCLUSIVO: 'X', PARALELO: '+', INCLUSIVO: 'O' }[tipo],
    etiqueta: `Gateway ${tipo} #${id}`,
    posicionX,
    posicionY,
    activo: true,
    arcosDesactivados: 0,
    advertencias: [],
    ...cambios,
  };
}

export function arco(
  id: number,
  origen: [ArcoRespuesta['origenTipo'], number, string],
  destino: [ArcoRespuesta['destinoTipo'], number, string],
  puntos: [number, number, number, number] | null,
  cambios: Partial<ArcoRespuesta> = {},
): ArcoRespuesta {
  return {
    id,
    procesoId: 5,
    origenTipo: origen[0],
    origenId: origen[1],
    origenNombre: origen[2],
    destinoTipo: destino[0],
    destinoId: destino[1],
    destinoNombre: destino[2],
    etiqueta: null,
    condicion: null,
    activo: true,
    origenX: puntos?.[0] ?? null,
    origenY: puntos?.[1] ?? null,
    destinoX: puntos?.[2] ?? null,
    destinoY: puntos?.[3] ?? null,
    advertencias: [],
    ...cambios,
  };
}

export function diagramaInicial(): DiagramaProceso {
  return {
    procesoId: 5,
    pools: [pool(80, 'Empresa de prueba', 1)],
    lanes: [lane(11, 'General', 1)],
    actividades: [],
    gateways: [],
    arcos: [],
    messageThrows: [],
    messageCatches: [],
    enviosExternos: [],
    flujosMensaje: [],
  };
}

export function diagramaCompleto(): DiagramaProceso {
  return {
    procesoId: 5,
    pools: [
      pool(80, 'Empresa de prueba', 1),
      pool(81, 'Cliente', 2),
      pool(82, 'Servicio de correo', 3, { cajaNegra: true }),
    ],
    lanes: [lane(11, 'General', 1), lane(12, 'Supervisor', 2, 21)],
    actividades: [
      actividad(1, 'Registrar solicitud', 11, 160, 60),
      actividad(2, 'Evaluar solicitud', 11, 340, 60),
      actividad(3, 'Aprobar solicitud', 12, 620, 200),
    ],
    gateways: [gateway(4, 520, 70)],
    arcos: [
      arco(31, ['EVENTO', 5, 'Message Catch: Solicitud de servicio'], ['ACTIVIDAD', 1, 'Registrar solicitud'], [78, 88, 160, 85]),
      arco(32, ['ACTIVIDAD', 1, 'Registrar solicitud'], ['ACTIVIDAD', 2, 'Evaluar solicitud'], [225, 83, 340, 83]),
      arco(33, ['ACTIVIDAD', 2, 'Evaluar solicitud'], ['GATEWAY', 4, 'Gateway EXCLUSIVO #4'], [405, 83, 520, 91]),
      arco(34, ['GATEWAY', 4, 'Gateway EXCLUSIVO #4'], ['ACTIVIDAD', 3, 'Aprobar solicitud'], [543, 93, 660, 200], {
        condicion: 'Aprobada',
      }),
      arco(35, ['GATEWAY', 4, 'Gateway EXCLUSIVO #4'], ['EVENTO', 7, 'Envío externo: Notificación de resultado'], [543, 93, 820, 88], {
        condicion: 'Rechazada',
        etiqueta: 'No procede',
      }),
      arco(36, ['ACTIVIDAD', 3, 'Aprobar solicitud'], ['EVENTO', 7, 'Envío externo: Notificación de resultado'], [685, 223, 820, 104]),
    ],
    messageThrows: [
      {
        id: 6,
        procesoId: 5,
        nombreMensaje: 'Solicitud de servicio',
        etiqueta: 'Message Throw: Solicitud de servicio',
        contenido: 'Datos de la solicitud de servicio del cliente',
        poolOrigenId: 81,
        poolOrigenNombre: 'Cliente',
        poolDestinoId: 80,
        poolDestinoNombre: 'Empresa de prueba',
        claveCorrelacion: 'numeroSolicitud',
        comportamientoSinCaso: 'INICIAR_NUEVO_CASO',
        catchHomologoId: 5,
        posicionX: 60,
        posicionY: 380,
        activo: true,
        arcosDesactivados: 0,
        advertencias: [],
      },
    ],
    messageCatches: [
      {
        id: 5,
        procesoId: 5,
        nombreMensaje: 'Solicitud de servicio',
        etiqueta: 'Message Catch: Solicitud de servicio',
        variante: 'INICIO',
        datosEsperados: 'Número de solicitud y datos del cliente',
        actividadesUso: 'Registrar solicitud',
        poolId: 80,
        poolNombre: 'Empresa de prueba',
        origenExterno: false,
        claveCorrelacion: 'numeroSolicitud',
        comportamientoSinCaso: 'INICIAR_NUEVO_CASO',
        throwHomologoId: 6,
        posicionX: 60,
        posicionY: 70,
        activo: true,
        arcosDesactivados: 0,
        advertencias: [],
      },
    ],
    enviosExternos: [
      {
        id: 7,
        procesoId: 5,
        nombreMensaje: 'Notificación de resultado',
        etiqueta: 'Envío externo: Notificación de resultado',
        poolOrigenId: 80,
        poolOrigenNombre: 'Empresa de prueba',
        poolDestinoId: 82,
        poolDestinoNombre: 'Servicio de correo',
        tipoDestino: 'CORREO',
        datosEnviados: 'Número de solicitud y resultado',
        momentoProceso: 'Al cerrar la solicitud',
        comportamientoFallo: 'CONTINUAR',
        claveCorrelacion: 'numeroSolicitud',
        posicionX: 820,
        posicionY: 70,
        activo: true,
        arcosDesactivados: 0,
        advertencias: [],
      },
    ],
    flujosMensaje: [
      {
        tipoEvento: 'MESSAGE_THROW',
        eventoId: 6,
        nombreMensaje: 'Solicitud de servicio',
        poolOrigenId: 81,
        poolDestinoId: 80,
        catchDestinoId: 5,
      },
      {
        tipoEvento: 'ENVIO_EXTERNO',
        eventoId: 7,
        nombreMensaje: 'Notificación de resultado',
        poolOrigenId: 80,
        poolDestinoId: 82,
        catchDestinoId: null,
      },
    ],
  };
}
