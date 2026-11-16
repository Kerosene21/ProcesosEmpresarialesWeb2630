import { ArcoRespuesta } from '../core/modelos/diagrama';
import { NodoDiagrama, claveNodo } from './geometria';

export function mismoNodo(uno: NodoDiagrama, otro: NodoDiagrama): boolean {
  return uno.tipoFlujo === otro.tipoFlujo && uno.elemento.id === otro.elemento.id;
}

export function motivoRechazo(origen: NodoDiagrama, destino: NodoDiagrama): string | null {
  if (mismoNodo(origen, destino)) {
    return 'El origen y el destino no pueden ser el mismo nodo';
  }
  if (origen.poolId !== destino.poolId) {
    return `«${origen.nombre}» y «${destino.nombre}» están en pools distintos: entre pools solo se admiten flujos de mensaje`;
  }
  if (!destino.admiteEntradas) {
    return `«${destino.nombre}» es un Message Catch de inicio: no admite flujos de secuencia entrantes`;
  }
  return null;
}

export function destinosPermitidos(nodos: readonly NodoDiagrama[], origen: NodoDiagrama): NodoDiagrama[] {
  return nodos.filter((destino) => motivoRechazo(origen, destino) === null);
}

export function arcoEntre(
  arcos: readonly ArcoRespuesta[],
  origen: NodoDiagrama,
  destino: NodoDiagrama,
  excluido: number | null = null,
): ArcoRespuesta | null {
  return (
    arcos.find(
      (arco) =>
        arco.id !== excluido &&
        claveNodo(arco.origenTipo, arco.origenId) === claveNodo(origen.tipoFlujo, origen.elemento.id) &&
        claveNodo(arco.destinoTipo, arco.destinoId) === claveNodo(destino.tipoFlujo, destino.elemento.id),
    ) ?? null
  );
}

export function arcosConectados(arcos: readonly ArcoRespuesta[], nodo: NodoDiagrama): ArcoRespuesta[] {
  const clave = claveNodo(nodo.tipoFlujo, nodo.elemento.id);
  return arcos.filter(
    (arco) => claveNodo(arco.origenTipo, arco.origenId) === clave || claveNodo(arco.destinoTipo, arco.destinoId) === clave,
  );
}
