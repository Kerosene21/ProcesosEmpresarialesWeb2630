import { Observable, catchError, map, of, switchMap } from 'rxjs';

import { DiagramaProceso, LaneRespuesta, PoolRespuesta } from '../core/modelos/diagrama';
import { FalloHttp } from '../core/modelos/fallo-http';
import { POSICION_MAXIMA, PosicionNodo } from '../core/modelos/movimiento';
import { Seleccion } from '../core/modelos/seleccion';
import { GeometriaDiagrama, NodoDiagrama, bandaDeLane, bandaDePool, calcularGeometria, centro } from './geometria';

interface Ancla {
  nodo: NodoDiagrama;
  laneId: number | null;
  poolId: number;
  altura: number;
}

const MAXIMO_ITERACIONES = 50;
const LANE_PROVISIONAL = -1;

export function recolocarContenido(antes: DiagramaProceso, despues: DiagramaProceso): PosicionNodo[] {
  const original = calcularGeometria(antes);
  const anclas = original.nodos.flatMap((nodo) => anclaDe(original, nodo));
  let actual = despues;
  for (let iteracion = 0; iteracion < MAXIMO_ITERACIONES; iteracion++) {
    const siguiente = colocarSegunAnclas(actual, calcularGeometria(actual), anclas);
    if (siguiente === actual) {
      break;
    }
    actual = siguiente;
  }
  const finales = new Map(calcularGeometria(actual).nodos.map((nodo) => [clave(nodo.elemento), nodo.caja]));
  return original.nodos.flatMap((nodo) => {
    const caja = finales.get(clave(nodo.elemento));
    return caja && caja.y !== nodo.caja.y && caja.y >= 0 && caja.y <= POSICION_MAXIMA
      ? [{ tipo: nodo.tipoFlujo, id: nodo.elemento.id, posicionX: caja.x, posicionY: caja.y }]
      : [];
  });
}

export function seguidoDeRecolocacion<T>(
  operacion: Observable<T>,
  nodos: PosicionNodo[],
  recolocar: (nodos: PosicionNodo[]) => Observable<void>,
  informar: (fallo: FalloHttp | null) => void,
): Observable<T> {
  if (nodos.length === 0) {
    return operacion;
  }
  return operacion.pipe(
    switchMap((resultado) =>
      recolocar(nodos).pipe(
        map((): FalloHttp | null => null),
        catchError((fallo: FalloHttp) => of(fallo)),
        map((fallo) => {
          informar(fallo);
          return resultado;
        }),
      ),
    ),
  );
}

export function resumenRecolocacion(cuantos: number, proposito: string): string {
  if (cuantos === 0) {
    return '';
  }
  return cuantos === 1
    ? ` Se recolocó un elemento del diagrama ${proposito}.`
    : ` Se recolocaron ${cuantos} elementos del diagrama ${proposito}.`;
}

export function conLanesOrdenadas(diagrama: DiagramaProceso, poolId: number, orden: readonly number[]): DiagramaProceso {
  return {
    ...diagrama,
    lanes: diagrama.lanes.map((lane) =>
      lane.poolId === poolId && orden.includes(lane.id) ? { ...lane, orden: orden.indexOf(lane.id) + 1 } : lane,
    ),
  };
}

export function conLaneNueva(diagrama: DiagramaProceso, poolId: number, orden: number | null): DiagramaProceso {
  const posicion = orden ?? diagrama.lanes.filter((lane) => lane.poolId === poolId).length + 1;
  const nueva: LaneRespuesta = { id: LANE_PROVISIONAL, nombre: '', rolProcesoId: null, poolId, orden: posicion };
  return {
    ...diagrama,
    lanes: [
      ...diagrama.lanes.map((lane) =>
        lane.poolId === poolId && lane.orden >= posicion ? { ...lane, orden: lane.orden + 1 } : lane,
      ),
      nueva,
    ],
  };
}

export function sinLane(diagrama: DiagramaProceso, laneId: number): DiagramaProceso {
  return { ...diagrama, lanes: diagrama.lanes.filter((lane) => lane.id !== laneId) };
}

export function conPool(diagrama: DiagramaProceso, poolId: number, cambios: Partial<PoolRespuesta>): DiagramaProceso {
  return { ...diagrama, pools: diagrama.pools.map((pool) => (pool.id === poolId ? { ...pool, ...cambios } : pool)) };
}

export function sinPool(diagrama: DiagramaProceso, poolId: number): DiagramaProceso {
  return { ...diagrama, pools: diagrama.pools.filter((pool) => pool.id !== poolId) };
}

function anclaDe(geometria: GeometriaDiagrama, nodo: NodoDiagrama): Ancla[] {
  if (nodo.poolId === null) {
    return [];
  }
  if (nodo.laneId !== null) {
    const lane = bandaDeLane(geometria, nodo.laneId);
    return lane ? [{ nodo, laneId: nodo.laneId, poolId: nodo.poolId, altura: nodo.caja.y - lane.y }] : [];
  }
  const pool = bandaDePool(geometria, nodo.poolId);
  if (!pool) {
    return [];
  }
  const medio = centro(nodo.caja).y;
  const lane = pool.lanes.find((banda) => medio >= banda.y && medio <= banda.y + banda.alto);
  return [
    lane
      ? { nodo, laneId: lane.lane.id, poolId: nodo.poolId, altura: nodo.caja.y - lane.y }
      : { nodo, laneId: null, poolId: nodo.poolId, altura: nodo.caja.y - pool.y },
  ];
}

function colocarSegunAnclas(diagrama: DiagramaProceso, geometria: GeometriaDiagrama, anclas: Ancla[]): DiagramaProceso {
  const actuales = new Map(geometria.nodos.map((nodo) => [clave(nodo.elemento), nodo.caja.y]));
  const nuevas = new Map<string, number>();
  for (const ancla of anclas) {
    const banda = (ancla.laneId === null ? null : bandaDeLane(geometria, ancla.laneId)) ?? bandaDePool(geometria, ancla.poolId);
    const deseada = banda ? banda.y + ancla.altura : undefined;
    if (deseada !== undefined && actuales.get(clave(ancla.nodo.elemento)) !== deseada) {
      nuevas.set(clave(ancla.nodo.elemento), deseada);
    }
  }
  if (nuevas.size === 0) {
    return diagrama;
  }
  const mover = <T extends { id: number; posicionY: number }>(tipo: Seleccion['tipo'], lista: T[]): T[] =>
    lista.map((elemento) => {
      const y = nuevas.get(clave({ tipo, id: elemento.id }));
      return y === undefined ? elemento : { ...elemento, posicionY: y };
    });
  return {
    ...diagrama,
    actividades: mover('ACTIVIDAD', diagrama.actividades),
    gateways: mover('GATEWAY', diagrama.gateways),
    messageThrows: mover('MESSAGE_THROW', diagrama.messageThrows),
    messageCatches: mover('MESSAGE_CATCH', diagrama.messageCatches),
    enviosExternos: mover('ENVIO_EXTERNO', diagrama.enviosExternos),
  };
}

function clave(elemento: Seleccion): string {
  return `${elemento.tipo}:${elemento.id}`;
}
