import { ComportamientoFallo, ComportamientoSinCaso, TipoDestinoExterno, VarianteMessageCatch } from './diagrama';

export const VARIANTES_CATCH: readonly VarianteMessageCatch[] = ['INICIO', 'INTERMEDIO'];

export const COMPORTAMIENTOS_SIN_CASO: readonly ComportamientoSinCaso[] = ['DESCARTAR', 'INICIAR_NUEVO_CASO'];

export const TIPOS_DESTINO_EXTERNO: readonly TipoDestinoExterno[] = ['CORREO', 'SERVICIO_WEB', 'COLA'];

export const COMPORTAMIENTOS_FALLO: readonly ComportamientoFallo[] = ['CONTINUAR', 'RUTA_ERROR', 'FINALIZAR'];

export const LONGITUD_MAXIMA_EVENTO = {
  nombreMensaje: 150,
  contenido: 2000,
  datosEsperados: 2000,
  actividadesUso: 1000,
  datosEnviados: 2000,
  momentoProceso: 300,
  claveCorrelacion: 100,
} as const;

export interface EditarMessageThrow {
  nombreMensaje: string;
  contenido: string;
  poolDestinoId: number;
  claveCorrelacion: string | null;
  comportamientoSinCaso: ComportamientoSinCaso | null;
}

export interface CrearMessageThrow extends EditarMessageThrow {
  poolOrigenId: number;
  posicionX: number;
  posicionY: number;
}

export interface EditarMessageCatch {
  nombreMensaje: string;
  variante: VarianteMessageCatch;
  datosEsperados: string;
  actividadesUso: string;
  origenExterno: boolean;
  claveCorrelacion: string | null;
  comportamientoSinCaso: ComportamientoSinCaso | null;
}

export interface CrearMessageCatch extends EditarMessageCatch {
  poolId: number;
  posicionX: number;
  posicionY: number;
}

export interface EditarEnvioExterno {
  nombreMensaje: string;
  poolDestinoId: number;
  tipoDestino: TipoDestinoExterno;
  datosEnviados: string;
  momentoProceso: string;
  comportamientoFallo: ComportamientoFallo;
  claveCorrelacion: string | null;
}

export interface CrearEnvioExterno extends EditarEnvioExterno {
  poolOrigenId: number;
  posicionX: number;
  posicionY: number;
}
