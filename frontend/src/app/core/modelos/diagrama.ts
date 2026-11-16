export type TipoPool = 'PROPIETARIO' | 'PARTICIPANTE' | 'EXTERNO';

export type TipoActividad = 'TAREA_USUARIO' | 'TAREA_SISTEMA' | 'TAREA_MANUAL' | 'TAREA_SERVICIO';

export type TipoGateway = 'EXCLUSIVO' | 'PARALELO' | 'INCLUSIVO';

export type TipoNodoFlujo = 'ACTIVIDAD' | 'GATEWAY' | 'EVENTO';

export type TipoEvento = 'MESSAGE_THROW' | 'MESSAGE_CATCH' | 'ENVIO_EXTERNO';

export type VarianteMessageCatch = 'INICIO' | 'INTERMEDIO';

export type ComportamientoSinCaso = 'DESCARTAR' | 'INICIAR_NUEVO_CASO';

export type TipoDestinoExterno = 'CORREO' | 'SERVICIO_WEB' | 'COLA';

export type ComportamientoFallo = 'CONTINUAR' | 'RUTA_ERROR' | 'FINALIZAR';

export interface PoolRespuesta {
  id: number;
  procesoId: number;
  nombre: string;
  tipo: TipoPool;
  orden: number;
  cajaNegra: boolean;
  activo: boolean;
  empresaId: number | null;
  empresaNombre: string | null;
}

export interface LaneRespuesta {
  id: number;
  nombre: string;
  rolProcesoId: number | null;
  poolId: number;
  orden: number;
}

export interface ActividadRespuesta {
  id: number;
  nombre: string;
  tipo: TipoActividad;
  procesoId: number;
  poolId: number;
  laneId: number;
  laneNombre: string;
  posicionX: number;
  posicionY: number;
  activo: boolean;
  arcosDesactivados: number;
  advertencias: string[];
}

export interface GatewayRespuesta {
  id: number;
  procesoId: number;
  poolId: number;
  tipo: TipoGateway;
  simbolo: string;
  etiqueta: string;
  posicionX: number;
  posicionY: number;
  activo: boolean;
  arcosDesactivados: number;
  advertencias: string[];
}

export interface ArcoRespuesta {
  id: number;
  procesoId: number;
  origenTipo: TipoNodoFlujo;
  origenId: number;
  origenNombre: string;
  destinoTipo: TipoNodoFlujo;
  destinoId: number;
  destinoNombre: string;
  etiqueta: string | null;
  condicion: string | null;
  activo: boolean;
  origenX: number | null;
  origenY: number | null;
  destinoX: number | null;
  destinoY: number | null;
  advertencias: string[];
}

export interface MessageThrowRespuesta {
  id: number;
  procesoId: number;
  nombreMensaje: string;
  etiqueta: string;
  contenido: string;
  poolOrigenId: number;
  poolOrigenNombre: string;
  poolDestinoId: number;
  poolDestinoNombre: string;
  claveCorrelacion: string | null;
  comportamientoSinCaso: ComportamientoSinCaso | null;
  catchHomologoId: number | null;
  posicionX: number;
  posicionY: number;
  activo: boolean;
  arcosDesactivados: number;
  advertencias: string[];
}

export interface MessageCatchRespuesta {
  id: number;
  procesoId: number;
  nombreMensaje: string;
  etiqueta: string;
  variante: VarianteMessageCatch;
  datosEsperados: string;
  actividadesUso: string;
  poolId: number;
  poolNombre: string;
  origenExterno: boolean;
  claveCorrelacion: string | null;
  comportamientoSinCaso: ComportamientoSinCaso | null;
  throwHomologoId: number | null;
  posicionX: number;
  posicionY: number;
  activo: boolean;
  arcosDesactivados: number;
  advertencias: string[];
}

export interface EnvioExternoRespuesta {
  id: number;
  procesoId: number;
  nombreMensaje: string;
  etiqueta: string;
  poolOrigenId: number;
  poolOrigenNombre: string;
  poolDestinoId: number;
  poolDestinoNombre: string;
  tipoDestino: TipoDestinoExterno;
  datosEnviados: string;
  momentoProceso: string;
  comportamientoFallo: ComportamientoFallo;
  claveCorrelacion: string | null;
  posicionX: number;
  posicionY: number;
  activo: boolean;
  arcosDesactivados: number;
  advertencias: string[];
}

export interface FlujoMensaje {
  tipoEvento: TipoEvento;
  eventoId: number;
  nombreMensaje: string;
  poolOrigenId: number;
  poolDestinoId: number;
  catchDestinoId: number | null;
}

export interface DiagramaProceso {
  procesoId: number;
  pools: PoolRespuesta[];
  lanes: LaneRespuesta[];
  actividades: ActividadRespuesta[];
  gateways: GatewayRespuesta[];
  arcos: ArcoRespuesta[];
  messageThrows: MessageThrowRespuesta[];
  messageCatches: MessageCatchRespuesta[];
  enviosExternos: EnvioExternoRespuesta[];
  flujosMensaje: FlujoMensaje[];
}
