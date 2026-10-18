import { HttpErrorResponse } from '@angular/common/http';

import { ErrorApi, FalloHttp, TipoFallo } from '../modelos/fallo-http';

const TITULOS: Record<TipoFallo, string> = {
  conexion: 'No hay conexión con el servidor',
  'no-autenticado': 'Se requiere iniciar sesión',
  'sin-permiso': 'No tienes permiso para esta consulta',
  'no-encontrado': 'El recurso solicitado no existe',
  'datos-no-validos': 'Los datos enviados no son válidos',
  conflicto: 'La operación entra en conflicto con los datos actuales',
  servidor: 'El servidor no pudo atender la solicitud',
  desconocido: 'Ocurrió un error inesperado',
};

const TIPOS_POR_ESTADO: Record<number, TipoFallo> = {
  0: 'conexion',
  400: 'datos-no-validos',
  401: 'no-autenticado',
  403: 'sin-permiso',
  404: 'no-encontrado',
  409: 'conflicto',
};

export function esFalloHttp(valor: unknown): valor is FalloHttp {
  return typeof valor === 'object' && valor !== null && 'tipo' in valor && 'titulo' in valor && 'estado' in valor;
}

export function traducirFallo(error: unknown): FalloHttp {
  if (esFalloHttp(error)) {
    return error;
  }
  if (!(error instanceof HttpErrorResponse)) {
    return crearFallo('desconocido', 0, null);
  }
  return crearFallo(tipoSegunEstado(error.status), error.status, esErrorApi(error.error) ? error.error : null);
}

function tipoSegunEstado(estado: number): TipoFallo {
  return TIPOS_POR_ESTADO[estado] ?? (estado >= 500 ? 'servidor' : 'desconocido');
}

function esErrorApi(cuerpo: unknown): cuerpo is ErrorApi {
  return (
    typeof cuerpo === 'object' &&
    cuerpo !== null &&
    typeof (cuerpo as ErrorApi).codigo === 'string' &&
    typeof (cuerpo as ErrorApi).mensaje === 'string'
  );
}

function crearFallo(tipo: TipoFallo, estado: number, cuerpo: ErrorApi | null): FalloHttp {
  return {
    tipo,
    estado,
    codigo: cuerpo?.codigo ?? null,
    titulo: TITULOS[tipo],
    detalle: cuerpo?.mensaje ?? null,
  };
}
