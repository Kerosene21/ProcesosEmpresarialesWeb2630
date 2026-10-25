import { AbstractControl } from '@angular/forms';

import { TEXTO_TIPO_POOL } from '../compartido/textos';
import { errorDelServidor } from '../core/formularios/errores-servidor';
import { DiagramaProceso, PoolRespuesta } from '../core/modelos/diagrama';
import { Punto } from './geometria';

export interface PropuestaEvento {
  poolId: number | null;
  posicion: Punto;
}

function porOrden(uno: PoolRespuesta, otro: PoolRespuesta): number {
  return uno.orden - otro.orden || uno.id - otro.id;
}

export function poolsConElementos(diagrama: DiagramaProceso): PoolRespuesta[] {
  return diagrama.pools.filter((pool) => !pool.cajaNegra).sort(porOrden);
}

export function poolsDestino(diagrama: DiagramaProceso, origenId: number | null): PoolRespuesta[] {
  return diagrama.pools.filter((pool) => pool.id !== origenId).sort(porOrden);
}

export function sistemasExternos(diagrama: DiagramaProceso): PoolRespuesta[] {
  return diagrama.pools.filter((pool) => pool.tipo === 'EXTERNO' && pool.cajaNegra).sort(porOrden);
}

export function nombreDePool(pool: PoolRespuesta): string {
  const tipo = TEXTO_TIPO_POOL[pool.tipo].toLowerCase();
  return `${pool.nombre} (${pool.cajaNegra ? `${tipo}, caja negra` : tipo})`;
}

export function textoONulo(texto: string): string | null {
  const limpio = texto.trim();
  return limpio.length > 0 ? limpio : null;
}

export function errorDeCampo(control: AbstractControl, obligatorio: string, maximo?: number): string | null {
  if (control.disabled || control.valid || !control.touched) {
    return null;
  }
  const servidor = errorDelServidor(control);
  if (servidor) {
    return servidor;
  }
  if (control.hasError('maxlength') && maximo !== undefined) {
    return `No puede superar ${maximo} caracteres`;
  }
  if (control.hasError('entero')) {
    return 'Usa un número entero';
  }
  return obligatorio;
}
