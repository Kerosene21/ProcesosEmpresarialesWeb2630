import { ArcoRespuesta } from '../core/modelos/diagrama';
import { arcosConectados } from './conexiones';
import { DestinoMovimiento, GeometriaDiagrama, NodoDiagrama, bandaDePool } from './geometria';

export function motivoMovimientoNoValido(
  geometria: GeometriaDiagrama,
  nodo: NodoDiagrama,
  destino: DestinoMovimiento | null,
  arcos: readonly ArcoRespuesta[],
): string | null {
  if (!destino) {
    return `Suelta «${nodo.nombre}» dentro de un pool del diagrama.`;
  }
  if (destino.pool.cajaNegra) {
    return `El pool «${destino.pool.nombre}» es una caja negra y no admite elementos.`;
  }
  if (nodo.elemento.tipo !== 'ACTIVIDAD') {
    const propio = nodo.poolId === null ? null : bandaDePool(geometria, nodo.poolId);
    return destino.pool.id === nodo.poolId
      ? null
      : `«${nodo.nombre}» conserva su pool al moverlo: suéltalo dentro de «${propio?.pool.nombre ?? 'su pool'}».`;
  }
  if (!destino.lane) {
    return `La actividad «${nodo.nombre}» debe quedar dentro de una lane.`;
  }
  const conectados = arcosConectados(arcos, nodo).length;
  return destino.pool.id !== nodo.poolId && conectados > 0
    ? `«${nodo.nombre}» tiene ${conectados === 1 ? 'un arco conectado' : `${conectados} arcos conectados`}: no puede pasar a otro pool porque un flujo de secuencia no cruza pools.`
    : null;
}

export function movimientoSinCambios(nodo: NodoDiagrama, destino: DestinoMovimiento): boolean {
  const mismaLane = nodo.elemento.tipo !== 'ACTIVIDAD' || destino.lane?.id === nodo.laneId;
  return mismaLane && nodo.caja.x === destino.posicion.x && nodo.caja.y === destino.posicion.y;
}
