import {
  ArcoRespuesta,
  DiagramaProceso,
  EnvioExternoRespuesta,
  FlujoMensaje,
  LaneRespuesta,
  MessageCatchRespuesta,
  MessageThrowRespuesta,
  PoolRespuesta,
  TipoNodoFlujo,
} from '../core/modelos/diagrama';
import { exigeCondicion } from '../core/modelos/gateway';
import { Seleccion, TipoElemento } from '../core/modelos/seleccion';

export const ANCHO_ACTIVIDAD = 130;
export const ALTO_ACTIVIDAD = 46;
export const LADO_GATEWAY = 46;
export const DIAMETRO_EVENTO = 36;

export const ANCHO_CABECERA_POOL = 32;
export const ANCHO_CABECERA_LANE = 28;
export const ALTO_MINIMO_LANE = 140;
export const ALTO_MINIMO_POOL = 140;
export const ALTO_CAJA_NEGRA = 80;
export const SEPARACION_POOLS = 40;
export const SEPARACION_NODOS = 40;
export const RELLENO = 20;
export const MARGEN_LIENZO = 16;
export const ANCHO_MINIMO_CONTENIDO = 880;

export interface Punto {
  x: number;
  y: number;
}

export interface Rectangulo extends Punto {
  ancho: number;
  alto: number;
}

export interface Segmento {
  desde: Punto;
  hasta: Punto;
}

export type EventoDiagrama =
  | { tipo: 'MESSAGE_THROW'; datos: MessageThrowRespuesta }
  | { tipo: 'MESSAGE_CATCH'; datos: MessageCatchRespuesta }
  | { tipo: 'ENVIO_EXTERNO'; datos: EnvioExternoRespuesta };

export interface NodoDiagrama {
  elemento: Seleccion;
  tipoFlujo: TipoNodoFlujo;
  nombre: string;
  poolId: number | null;
  laneId: number | null;
  caja: Rectangulo;
  admiteEntradas: boolean;
  exigeCondicion: boolean;
}

export interface BandaLane extends Rectangulo {
  lane: LaneRespuesta;
}

export interface BandaPool extends Rectangulo {
  pool: PoolRespuesta;
  lanes: BandaLane[];
}

export interface ArcoUbicado {
  arco: ArcoRespuesta;
  segmento: Segmento | null;
}

export interface FlujoUbicado {
  flujo: FlujoMensaje;
  segmento: Segmento | null;
}

export interface GeometriaDiagrama {
  vista: Rectangulo;
  inicioContenido: number;
  pools: BandaPool[];
  nodos: NodoDiagrama[];
  arcos: ArcoUbicado[];
  flujos: FlujoUbicado[];
  advertencias: string[];
}

export interface Ubicacion {
  pool: BandaPool;
  lane: BandaLane | null;
}

export interface DestinoMovimiento {
  pool: PoolRespuesta;
  lane: LaneRespuesta | null;
  posicion: Punto;
}

const MEDIDAS: Record<TipoNodoFlujo, { ancho: number; alto: number }> = {
  ACTIVIDAD: { ancho: ANCHO_ACTIVIDAD, alto: ALTO_ACTIVIDAD },
  GATEWAY: { ancho: LADO_GATEWAY, alto: LADO_GATEWAY },
  EVENTO: { ancho: DIAMETRO_EVENTO, alto: DIAMETRO_EVENTO },
};

const TIPO_FLUJO: Record<Exclude<TipoElemento, 'ARCO'>, TipoNodoFlujo> = {
  ACTIVIDAD: 'ACTIVIDAD',
  GATEWAY: 'GATEWAY',
  MESSAGE_THROW: 'EVENTO',
  MESSAGE_CATCH: 'EVENTO',
  ENVIO_EXTERNO: 'EVENTO',
};

export function claveNodo(tipo: TipoNodoFlujo, id: number): string {
  return `${tipo}:${id}`;
}

export function tipoDeFlujo(tipo: Exclude<TipoElemento, 'ARCO'>): TipoNodoFlujo {
  return TIPO_FLUJO[tipo];
}

export function cajaDeNodo(tipo: TipoNodoFlujo, posicionX: number | null, posicionY: number | null): Rectangulo {
  return { x: posicionX ?? 0, y: posicionY ?? 0, ...MEDIDAS[tipo] };
}

export function centro(caja: Rectangulo): Punto {
  return { x: caja.x + Math.floor(caja.ancho / 2), y: caja.y + Math.floor(caja.alto / 2) };
}

export function llegada(origen: Punto, destino: Punto, caja: Pick<Rectangulo, 'ancho' | 'alto'>): Punto {
  const deltaX = destino.x - origen.x;
  const deltaY = destino.y - origen.y;
  if (deltaX === 0 && deltaY === 0) {
    return destino;
  }
  const escala = Math.min(
    1,
    escalaHasta(Math.floor(caja.ancho / 2), deltaX),
    escalaHasta(Math.floor(caja.alto / 2), deltaY),
  );
  return { x: Math.round(destino.x - deltaX * escala), y: Math.round(destino.y - deltaY * escala) };
}

export function puntoMedio(segmento: Segmento): Punto {
  return {
    x: Math.round((segmento.desde.x + segmento.hasta.x) / 2),
    y: Math.round((segmento.desde.y + segmento.hasta.y) / 2),
  };
}

export function puntosRombo(caja: Rectangulo): string {
  const medio = centro(caja);
  return [
    `${medio.x},${caja.y}`,
    `${caja.x + caja.ancho},${medio.y}`,
    `${medio.x},${caja.y + caja.alto}`,
    `${caja.x},${medio.y}`,
  ].join(' ');
}

export function recortar(texto: string, maximo: number): string {
  return texto.length <= maximo ? texto : `${texto.slice(0, Math.max(1, maximo - 1)).trimEnd()}…`;
}

export function nodosDe(diagrama: DiagramaProceso): NodoDiagrama[] {
  return [
    ...diagrama.actividades.map((actividad) =>
      nodo({ tipo: 'ACTIVIDAD', id: actividad.id }, actividad.nombre, actividad.poolId, actividad.laneId, actividad),
    ),
    ...diagrama.gateways.map((gateway) =>
      nodo({ tipo: 'GATEWAY', id: gateway.id }, gateway.etiqueta, gateway.poolId, null, gateway, {
        exigeCondicion: exigeCondicion(gateway.tipo),
      }),
    ),
    ...diagrama.messageThrows.map((evento) =>
      nodo({ tipo: 'MESSAGE_THROW', id: evento.id }, evento.etiqueta, evento.poolOrigenId, null, evento),
    ),
    ...diagrama.messageCatches.map((evento) =>
      nodo({ tipo: 'MESSAGE_CATCH', id: evento.id }, evento.etiqueta, evento.poolId, null, evento, {
        admiteEntradas: evento.variante !== 'INICIO',
      }),
    ),
    ...diagrama.enviosExternos.map((evento) =>
      nodo({ tipo: 'ENVIO_EXTERNO', id: evento.id }, evento.etiqueta, evento.poolOrigenId, null, evento),
    ),
  ];
}

export function calcularGeometria(diagrama: DiagramaProceso): GeometriaDiagrama {
  const advertencias: string[] = [];
  const nodos = nodosDe(diagrama);
  const izquierda = Math.min(0, ...nodos.map((actual) => actual.caja.x - RELLENO));
  const superior = Math.min(0, ...nodos.map((actual) => actual.caja.y - RELLENO));
  const derecha = Math.max(
    izquierda + ANCHO_MINIMO_CONTENIDO,
    ...nodos.map((actual) => actual.caja.x + actual.caja.ancho + RELLENO),
  );
  const xPools = izquierda - ANCHO_CABECERA_POOL - ANCHO_CABECERA_LANE;

  const pools = ubicarPools(diagrama, nodos, xPools, derecha, superior, advertencias);
  revisarPertenencia(diagrama, nodos, advertencias);

  const fondoPools = pools.length > 0 ? Math.max(...pools.map((banda) => banda.y + banda.alto)) : superior;
  const inferior = Math.max(
    fondoPools,
    superior + (pools.length > 0 ? 0 : ALTO_MINIMO_POOL),
    ...nodos.map((actual) => actual.caja.y + actual.caja.alto + RELLENO),
  );
  const indice = new Map(nodos.map((actual) => [claveNodo(actual.tipoFlujo, actual.elemento.id), actual]));

  return {
    vista: {
      x: xPools - MARGEN_LIENZO,
      y: superior - MARGEN_LIENZO,
      ancho: derecha - xPools + 2 * MARGEN_LIENZO,
      alto: inferior - superior + 2 * MARGEN_LIENZO,
    },
    inicioContenido: izquierda,
    pools,
    nodos,
    arcos: diagrama.arcos.map((arco) => ubicarArco(arco, indice, advertencias)),
    flujos: diagrama.flujosMensaje.map((flujo) => ubicarFlujo(flujo, indice, pools, advertencias)),
    advertencias,
  };
}

export function ubicacionEn(geometria: GeometriaDiagrama, punto: Punto): Ubicacion | null {
  if (punto.x < geometria.inicioContenido) {
    return null;
  }
  const pool = geometria.pools.find((banda) => contiene(banda, punto));
  if (!pool) {
    return null;
  }
  return { pool, lane: pool.lanes.find((banda) => contiene(banda, punto)) ?? null };
}

export function bandaDeLane(geometria: GeometriaDiagrama, laneId: number): BandaLane | null {
  return geometria.pools.flatMap((banda) => banda.lanes).find((franja) => franja.lane.id === laneId) ?? null;
}

export function bandaDePool(geometria: GeometriaDiagrama, poolId: number): BandaPool | null {
  return geometria.pools.find((banda) => banda.pool.id === poolId) ?? null;
}

export function colocarEnBanda(tipo: TipoNodoFlujo, centroDeseado: Punto, banda: Rectangulo, inicioContenido: number): Punto {
  const { ancho, alto } = MEDIDAS[tipo];
  const minimoY = banda.y + RELLENO;
  const maximoY = Math.max(minimoY, banda.y + banda.alto - alto - RELLENO);
  return {
    x: Math.max(inicioContenido + RELLENO, Math.round(centroDeseado.x - ancho / 2)),
    y: Math.min(maximoY, Math.max(minimoY, Math.round(centroDeseado.y - alto / 2))),
  };
}

export function posicionLibre(geometria: GeometriaDiagrama, tipo: TipoNodoFlujo, banda: Rectangulo): Punto {
  const ocupados = geometria.nodos.filter(({ caja }) => caja.y < banda.y + banda.alto && caja.y + caja.alto > banda.y);
  const x = Math.max(
    geometria.inicioContenido + RELLENO,
    ...ocupados.map(({ caja }) => caja.x + caja.ancho + SEPARACION_NODOS),
  );
  return colocarEnBanda(tipo, { x: x + MEDIDAS[tipo].ancho / 2, y: banda.y + banda.alto / 2 }, banda, geometria.inicioContenido);
}

export function sinElemento(diagrama: DiagramaProceso, elemento: Seleccion): DiagramaProceso {
  const otro = <T extends { id: number }>(tipo: TipoElemento, lista: T[]): T[] =>
    elemento.tipo === tipo ? lista.filter((actual) => actual.id !== elemento.id) : lista;
  return {
    ...diagrama,
    actividades: otro('ACTIVIDAD', diagrama.actividades),
    gateways: otro('GATEWAY', diagrama.gateways),
    messageThrows: otro('MESSAGE_THROW', diagrama.messageThrows),
    messageCatches: otro('MESSAGE_CATCH', diagrama.messageCatches),
    enviosExternos: otro('ENVIO_EXTERNO', diagrama.enviosExternos),
  };
}

export function ubicarMovimiento(
  diagrama: DiagramaProceso,
  geometria: GeometriaDiagrama,
  elemento: Seleccion,
  esquina: Punto,
): DestinoMovimiento | null {
  const nodo = buscarNodo(geometria, elemento);
  if (!nodo) {
    return null;
  }
  const medio = { x: esquina.x + nodo.caja.ancho / 2, y: esquina.y + nodo.caja.alto / 2 };
  const ubicacion = ubicacionEn(geometria, medio);
  if (!ubicacion) {
    return null;
  }
  const resto = calcularGeometria(sinElemento(diagrama, elemento));
  const banda = ubicacion.lane ?? ubicacion.pool;
  const final = ubicacion.lane ? bandaDeLane(resto, ubicacion.lane.lane.id) : bandaDePool(resto, ubicacion.pool.pool.id);
  if (!final) {
    return null;
  }
  return {
    pool: ubicacion.pool.pool,
    lane: ubicacion.lane?.lane ?? null,
    posicion: colocarEnBanda(nodo.tipoFlujo, { x: medio.x, y: final.y + medio.y - banda.y }, final, resto.inicioContenido),
  };
}

export function reubicarEnLane(
  diagrama: DiagramaProceso,
  geometria: GeometriaDiagrama,
  elemento: Seleccion,
  laneId: number,
): Punto | null {
  const nodo = buscarNodo(geometria, elemento);
  const resto = calcularGeometria(sinElemento(diagrama, elemento));
  const destino = bandaDeLane(resto, laneId);
  if (!nodo || !destino) {
    return null;
  }
  const origen = nodo.laneId === null ? null : bandaDeLane(geometria, nodo.laneId);
  const medio = centro(nodo.caja);
  const altura = origen ? medio.y - origen.y : destino.alto / 2;
  const posicion = colocarEnBanda(nodo.tipoFlujo, { x: medio.x, y: destino.y + altura }, destino, resto.inicioContenido);
  const caja = { ...nodo.caja, ...posicion };
  return resto.nodos.some((otro) => seSolapan(otro.caja, caja)) ? posicionLibre(resto, nodo.tipoFlujo, destino) : posicion;
}

export function desplazarNodo(geometria: GeometriaDiagrama, elemento: Seleccion, esquina: Punto): GeometriaDiagrama {
  const movido = buscarNodo(geometria, elemento);
  if (!movido) {
    return geometria;
  }
  const clave = claveNodo(movido.tipoFlujo, movido.elemento.id);
  const nodos = geometria.nodos.map((actual) =>
    actual === movido ? { ...actual, caja: { ...actual.caja, x: esquina.x, y: esquina.y } } : actual,
  );
  const indice = new Map(nodos.map((actual) => [claveNodo(actual.tipoFlujo, actual.elemento.id), actual]));
  return {
    ...geometria,
    nodos,
    arcos: geometria.arcos.map((ubicado) =>
      claveNodo(ubicado.arco.origenTipo, ubicado.arco.origenId) === clave ||
      claveNodo(ubicado.arco.destinoTipo, ubicado.arco.destinoId) === clave
        ? { arco: ubicado.arco, segmento: segmentoEntre(ubicado.arco, indice) ?? ubicado.segmento }
        : ubicado,
    ),
    flujos: geometria.flujos.map((ubicado) =>
      movido.tipoFlujo === 'EVENTO' && [ubicado.flujo.eventoId, ubicado.flujo.catchDestinoId].includes(movido.elemento.id)
        ? ubicarFlujo(ubicado.flujo, indice, geometria.pools, [])
        : ubicado,
    ),
  };
}

export function buscarNodo(geometria: GeometriaDiagrama, seleccion: Seleccion | null): NodoDiagrama | null {
  if (!seleccion || seleccion.tipo === 'ARCO') {
    return null;
  }
  return (
    geometria.nodos.find(
      (actual) => actual.elemento.tipo === seleccion.tipo && actual.elemento.id === seleccion.id,
    ) ?? null
  );
}

export function existeElemento(diagrama: DiagramaProceso, seleccion: Seleccion): boolean {
  const { id } = seleccion;
  switch (seleccion.tipo) {
    case 'ACTIVIDAD':
      return diagrama.actividades.some((actual) => actual.id === id);
    case 'GATEWAY':
      return diagrama.gateways.some((actual) => actual.id === id);
    case 'ARCO':
      return diagrama.arcos.some((actual) => actual.id === id);
    case 'MESSAGE_THROW':
      return diagrama.messageThrows.some((actual) => actual.id === id);
    case 'MESSAGE_CATCH':
      return diagrama.messageCatches.some((actual) => actual.id === id);
    case 'ENVIO_EXTERNO':
      return diagrama.enviosExternos.some((actual) => actual.id === id);
  }
}

function nodo(
  elemento: Seleccion & { tipo: Exclude<TipoElemento, 'ARCO'> },
  nombre: string,
  poolId: number | null,
  laneId: number | null,
  posicion: { posicionX: number | null; posicionY: number | null },
  reglas: Partial<Pick<NodoDiagrama, 'admiteEntradas' | 'exigeCondicion'>> = {},
): NodoDiagrama {
  const tipoFlujo = TIPO_FLUJO[elemento.tipo];
  return {
    elemento,
    tipoFlujo,
    nombre,
    poolId,
    laneId,
    caja: cajaDeNodo(tipoFlujo, posicion.posicionX, posicion.posicionY),
    admiteEntradas: reglas.admiteEntradas ?? true,
    exigeCondicion: reglas.exigeCondicion ?? false,
  };
}

function escalaHasta(margen: number, delta: number): number {
  return delta === 0 ? Number.MAX_VALUE : margen / Math.abs(delta);
}

function porOrden<T extends { orden: number; id: number }>(uno: T, otro: T): number {
  return uno.orden - otro.orden || uno.id - otro.id;
}

function fondoDe(caja: Rectangulo): number {
  return caja.y + caja.alto + RELLENO;
}

function contiene(caja: Rectangulo, punto: Punto): boolean {
  return punto.x >= caja.x && punto.x <= caja.x + caja.ancho && punto.y >= caja.y && punto.y <= caja.y + caja.alto;
}

function seSolapan(una: Rectangulo, otra: Rectangulo): boolean {
  return una.x < otra.x + otra.ancho && otra.x < una.x + una.ancho && una.y < otra.y + otra.alto && otra.y < una.y + una.alto;
}

function segmentoEntre(arco: ArcoRespuesta, indice: Map<string, NodoDiagrama>): Segmento | null {
  const origen = indice.get(claveNodo(arco.origenTipo, arco.origenId));
  const destino = indice.get(claveNodo(arco.destinoTipo, arco.destinoId));
  if (!origen || !destino) {
    return null;
  }
  const desde = centro(origen.caja);
  return { desde, hasta: llegada(desde, centro(destino.caja), destino.caja) };
}

function ubicarPools(
  diagrama: DiagramaProceso,
  nodos: NodoDiagrama[],
  x: number,
  derecha: number,
  superior: number,
  advertencias: string[],
): BandaPool[] {
  let cursor = superior;
  return [...diagrama.pools].sort(porOrden).map((pool) => {
    const lanes = diagrama.lanes.filter((lane) => lane.poolId === pool.id).sort(porOrden);
    const banda = ubicarPool(pool, lanes, nodos, x, derecha, cursor, advertencias);
    cursor = banda.y + banda.alto + SEPARACION_POOLS;
    return banda;
  });
}

function ubicarPool(
  pool: PoolRespuesta,
  lanes: LaneRespuesta[],
  nodos: NodoDiagrama[],
  x: number,
  derecha: number,
  superior: number,
  advertencias: string[],
): BandaPool {
  const propios = nodos.filter((actual) => actual.poolId === pool.id);
  const xLanes = x + ANCHO_CABECERA_POOL;
  const bandas: BandaLane[] = [];
  let fondo = superior;
  for (const lane of lanes) {
    const contenido = propios.filter((actual) => actual.laneId === lane.id);
    const alto = Math.max(ALTO_MINIMO_LANE, ...contenido.map((actual) => fondoDe(actual.caja) - fondo));
    contenido
      .filter((actual) => actual.caja.y < fondo)
      .forEach((actual) =>
        advertencias.push(`«${actual.nombre}» está por encima de la lane «${lane.nombre}» a la que pertenece`),
      );
    bandas.push({ lane, x: xLanes, y: fondo, ancho: derecha - xLanes, alto });
    fondo += alto;
  }

  const minimo = pool.cajaNegra ? ALTO_CAJA_NEGRA : lanes.length > 0 ? fondo - superior : ALTO_MINIMO_POOL;
  const alto = Math.max(minimo, ...propios.map((actual) => fondoDe(actual.caja) - superior));
  const ultima = bandas.at(-1);
  if (ultima && superior + alto > fondo) {
    ultima.alto += superior + alto - fondo;
  }

  propios
    .filter((actual) => !bandas.some((banda) => banda.lane.id === actual.laneId) && actual.caja.y < superior)
    .forEach((actual) => advertencias.push(`«${actual.nombre}» está por encima del pool «${pool.nombre}»`));
  if (pool.cajaNegra && propios.length > 0) {
    advertencias.push(`El pool «${pool.nombre}» es una caja negra, pero el diagrama le asigna elementos`);
  }
  return { pool, x, y: superior, ancho: derecha - x, alto, lanes: bandas };
}

function revisarPertenencia(diagrama: DiagramaProceso, nodos: NodoDiagrama[], advertencias: string[]): void {
  const pools = new Set(diagrama.pools.map((pool) => pool.id));
  const lanes = new Set(diagrama.lanes.map((lane) => lane.id));
  for (const actual of nodos) {
    if (actual.poolId === null || !pools.has(actual.poolId)) {
      advertencias.push(`«${actual.nombre}» pertenece a un pool que no aparece en el diagrama`);
    } else if (actual.laneId !== null && !lanes.has(actual.laneId)) {
      advertencias.push(`«${actual.nombre}» pertenece a una lane que no aparece en el diagrama`);
    }
  }
}

function ubicarArco(arco: ArcoRespuesta, indice: Map<string, NodoDiagrama>, advertencias: string[]): ArcoUbicado {
  const descripcion = `${arco.origenNombre} → ${arco.destinoNombre}`;
  if (!indice.has(claveNodo(arco.origenTipo, arco.origenId)) || !indice.has(claveNodo(arco.destinoTipo, arco.destinoId))) {
    advertencias.push(`El arco «${descripcion}» conecta un elemento que no aparece en el diagrama`);
  }
  if (arco.origenX === null || arco.origenY === null || arco.destinoX === null || arco.destinoY === null) {
    advertencias.push(`El arco «${descripcion}» no tiene coordenadas y no se puede dibujar`);
    return { arco, segmento: null };
  }
  return {
    arco,
    segmento: { desde: { x: arco.origenX, y: arco.origenY }, hasta: { x: arco.destinoX, y: arco.destinoY } },
  };
}

function ubicarFlujo(
  flujo: FlujoMensaje,
  indice: Map<string, NodoDiagrama>,
  pools: BandaPool[],
  advertencias: string[],
): FlujoUbicado {
  const origen = indice.get(claveNodo('EVENTO', flujo.eventoId));
  const receptor = flujo.catchDestinoId === null ? undefined : indice.get(claveNodo('EVENTO', flujo.catchDestinoId));
  const banda = pools.find((actual) => actual.pool.id === flujo.poolDestinoId);
  if (!origen || (!receptor && !banda)) {
    advertencias.push(`El flujo de mensaje «${flujo.nombreMensaje}» no tiene un origen o un destino en el diagrama`);
    return { flujo, segmento: null };
  }
  const centroOrigen = centro(origen.caja);
  const hasta = receptor
    ? llegada(centroOrigen, centro(receptor.caja), receptor.caja)
    : bordeDeBanda(banda as BandaPool, centroOrigen);
  return { flujo, segmento: { desde: llegada(hasta, centroOrigen, origen.caja), hasta } };
}

function bordeDeBanda(banda: BandaPool, desde: Punto): Punto {
  const minimo = banda.x + ANCHO_CABECERA_POOL + RELLENO;
  const maximo = banda.x + banda.ancho - RELLENO;
  return {
    x: Math.min(Math.max(desde.x, minimo), maximo),
    y: banda.y >= desde.y ? banda.y : banda.y + banda.alto,
  };
}
