import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import {
  TEXTO_DESTINO_EXTERNO,
  TEXTO_FALLO_ENVIO,
  TEXTO_SIN_CASO,
  TEXTO_TIPO_ACTIVIDAD,
  TEXTO_TIPO_ELEMENTO,
  TEXTO_TIPO_GATEWAY,
  TEXTO_VARIANTE_CATCH,
} from '../../compartido/textos';
import { ArcoRespuesta, DiagramaProceso, TipoNodoFlujo } from '../../core/modelos/diagrama';
import { Seleccion } from '../../core/modelos/seleccion';

export interface Fila {
  etiqueta: string;
  valor: string;
}

export interface DescripcionElemento {
  tipo: string;
  nombre: string;
  filas: Fila[];
  advertencias: string[];
}

const SIN_DATO = 'Sin definir';

@Component({
  selector: 'app-panel-elemento',
  templateUrl: './panel-elemento.html',
  styleUrl: './panel-elemento.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelElemento {
  readonly diagrama = input.required<DiagramaProceso>();
  readonly seleccion = input<Seleccion | null>(null);
  readonly limpiar = output<void>();

  protected readonly descripcion = computed(() => {
    const seleccion = this.seleccion();
    return seleccion ? describirElemento(this.diagrama(), seleccion) : null;
  });
}

export function describirElemento(diagrama: DiagramaProceso, seleccion: Seleccion): DescripcionElemento | null {
  const pools = new Map(diagrama.pools.map((pool) => [pool.id, pool.nombre]));
  const nombrePool = (id: number | null) => (id === null ? SIN_DATO : (pools.get(id) ?? `Pool #${id}`));
  const tipo = TEXTO_TIPO_ELEMENTO[seleccion.tipo];
  switch (seleccion.tipo) {
    case 'ACTIVIDAD': {
      const actividad = diagrama.actividades.find((actual) => actual.id === seleccion.id);
      return actividad
        ? {
            tipo,
            nombre: actividad.nombre,
            filas: [
              { etiqueta: 'Tipo', valor: TEXTO_TIPO_ACTIVIDAD[actividad.tipo] },
              { etiqueta: 'Lane', valor: actividad.laneNombre },
              { etiqueta: 'Pool', valor: nombrePool(actividad.poolId) },
              { etiqueta: 'Posición', valor: posicion(actividad.posicionX, actividad.posicionY) },
              { etiqueta: 'Conexiones', valor: conexiones(diagrama.arcos, 'ACTIVIDAD', actividad.id) },
            ],
            advertencias: actividad.advertencias,
          }
        : null;
    }
    case 'GATEWAY': {
      const gateway = diagrama.gateways.find((actual) => actual.id === seleccion.id);
      if (!gateway) {
        return null;
      }
      const salidas = diagrama.arcos.filter((arco) => arco.origenTipo === 'GATEWAY' && arco.origenId === gateway.id);
      return {
        tipo,
        nombre: gateway.etiqueta,
        filas: [
          { etiqueta: 'Tipo', valor: `${TEXTO_TIPO_GATEWAY[gateway.tipo]} (${gateway.simbolo})` },
          { etiqueta: 'Pool', valor: nombrePool(gateway.poolId) },
          { etiqueta: 'Posición', valor: posicion(gateway.posicionX, gateway.posicionY) },
          { etiqueta: 'Conexiones', valor: conexiones(diagrama.arcos, 'GATEWAY', gateway.id) },
          ...salidas.map((arco) => ({
            etiqueta: `Salida a ${arco.destinoNombre}`,
            valor: arco.condicion ?? 'Sin condición',
          })),
        ],
        advertencias: gateway.advertencias,
      };
    }
    case 'ARCO': {
      const arco = diagrama.arcos.find((actual) => actual.id === seleccion.id);
      return arco
        ? {
            tipo,
            nombre: `${arco.origenNombre} → ${arco.destinoNombre}`,
            filas: [
              { etiqueta: 'Origen', valor: arco.origenNombre },
              { etiqueta: 'Destino', valor: arco.destinoNombre },
              { etiqueta: 'Etiqueta', valor: arco.etiqueta ?? SIN_DATO },
              { etiqueta: 'Condición', valor: arco.condicion ?? 'Sin condición' },
            ],
            advertencias: arco.advertencias,
          }
        : null;
    }
    case 'MESSAGE_THROW': {
      const evento = diagrama.messageThrows.find((actual) => actual.id === seleccion.id);
      return evento
        ? {
            tipo,
            nombre: evento.nombreMensaje,
            filas: [
              { etiqueta: 'Contenido', valor: evento.contenido },
              { etiqueta: 'Pool emisor', valor: evento.poolOrigenNombre },
              { etiqueta: 'Pool receptor', valor: evento.poolDestinoNombre },
              { etiqueta: 'Clave de correlación', valor: evento.claveCorrelacion ?? SIN_DATO },
              {
                etiqueta: 'Sin caso abierto',
                valor: evento.comportamientoSinCaso ? TEXTO_SIN_CASO[evento.comportamientoSinCaso] : SIN_DATO,
              },
              { etiqueta: 'Catch homólogo', valor: catchHomologo(diagrama, evento.catchHomologoId) },
              { etiqueta: 'Posición', valor: posicion(evento.posicionX, evento.posicionY) },
            ],
            advertencias: evento.advertencias,
          }
        : null;
    }
    case 'MESSAGE_CATCH': {
      const evento = diagrama.messageCatches.find((actual) => actual.id === seleccion.id);
      return evento
        ? {
            tipo,
            nombre: evento.nombreMensaje,
            filas: [
              { etiqueta: 'Variante', valor: TEXTO_VARIANTE_CATCH[evento.variante] },
              { etiqueta: 'Pool', valor: evento.poolNombre },
              { etiqueta: 'Datos esperados', valor: evento.datosEsperados },
              { etiqueta: 'Actividades que lo usan', valor: evento.actividadesUso },
              { etiqueta: 'Origen externo', valor: evento.origenExterno ? 'Sí' : 'No' },
              { etiqueta: 'Clave de correlación', valor: evento.claveCorrelacion ?? SIN_DATO },
              {
                etiqueta: 'Throw homólogo',
                valor: evento.origenExterno ? 'No aplica: origen externo' : throwHomologo(diagrama, evento.throwHomologoId),
              },
              { etiqueta: 'Posición', valor: posicion(evento.posicionX, evento.posicionY) },
            ],
            advertencias: evento.advertencias,
          }
        : null;
    }
    case 'ENVIO_EXTERNO': {
      const evento = diagrama.enviosExternos.find((actual) => actual.id === seleccion.id);
      return evento
        ? {
            tipo,
            nombre: evento.nombreMensaje,
            filas: [
              { etiqueta: 'Pool emisor', valor: evento.poolOrigenNombre },
              { etiqueta: 'Destino externo', valor: evento.poolDestinoNombre },
              { etiqueta: 'Medio', valor: TEXTO_DESTINO_EXTERNO[evento.tipoDestino] },
              { etiqueta: 'Datos enviados', valor: evento.datosEnviados },
              { etiqueta: 'Momento', valor: evento.momentoProceso },
              { etiqueta: 'Si falla', valor: TEXTO_FALLO_ENVIO[evento.comportamientoFallo] },
              { etiqueta: 'Clave de correlación', valor: evento.claveCorrelacion ?? SIN_DATO },
              { etiqueta: 'Posición', valor: posicion(evento.posicionX, evento.posicionY) },
            ],
            advertencias: evento.advertencias,
          }
        : null;
    }
  }
}

function catchHomologo(diagrama: DiagramaProceso, id: number | null): string {
  const homologo = id === null ? undefined : diagrama.messageCatches.find((actual) => actual.id === id);
  if (!homologo) {
    return id === null ? 'No existe' : `#${id}`;
  }
  return `«${homologo.nombreMensaje}» en ${homologo.poolNombre} (#${homologo.id})`;
}

function throwHomologo(diagrama: DiagramaProceso, id: number | null): string {
  const homologo = id === null ? undefined : diagrama.messageThrows.find((actual) => actual.id === id);
  if (!homologo) {
    return id === null ? 'No existe' : `#${id}`;
  }
  return `«${homologo.nombreMensaje}» desde ${homologo.poolOrigenNombre} (#${homologo.id})`;
}

function posicion(x: number | null, y: number | null): string {
  return x === null || y === null ? SIN_DATO : `x ${x}, y ${y}`;
}

function conexiones(arcos: ArcoRespuesta[], tipo: TipoNodoFlujo, id: number): string {
  const entradas = arcos.filter((arco) => arco.destinoTipo === tipo && arco.destinoId === id).length;
  const salidas = arcos.filter((arco) => arco.origenTipo === tipo && arco.origenId === id).length;
  return `${entradas} de entrada, ${salidas} de salida`;
}
