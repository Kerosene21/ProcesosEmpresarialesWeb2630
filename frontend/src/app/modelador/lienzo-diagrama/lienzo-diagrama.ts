import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DiagramaProceso } from '../../core/modelos/diagrama';
import { Seleccion, TipoElemento } from '../../core/modelos/seleccion';
import { ArcoSecuencia } from '../arco-secuencia/arco-secuencia';
import { FlujoMensaje } from '../flujo-mensaje/flujo-mensaje';
import {
  ANCHO_CABECERA_LANE,
  ANCHO_CABECERA_POOL,
  BandaLane,
  BandaPool,
  EventoDiagrama,
  GeometriaDiagrama,
  Punto,
  recortar,
} from '../geometria';
import { NodoActividad } from '../nodo-actividad/nodo-actividad';
import { NodoEvento } from '../nodo-evento/nodo-evento';
import { NodoGateway } from '../nodo-gateway/nodo-gateway';

interface Rotulo {
  x: number;
  y: number;
  texto: string;
  giro: string;
}

const ANCHO_LETRA = 7;

let siguienteLienzo = 0;

@Component({
  selector: 'app-lienzo-diagrama',
  imports: [ArcoSecuencia, FlujoMensaje, NodoActividad, NodoEvento, NodoGateway],
  templateUrl: './lienzo-diagrama.html',
  styleUrl: './lienzo-diagrama.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LienzoDiagrama {
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly seleccion = input<Seleccion | null>(null);
  readonly marcados = input<readonly string[]>([]);
  readonly colocando = input(false);
  readonly elementoElegido = output<Seleccion>();
  readonly fondoPulsado = output<Punto>();

  protected readonly marcador = `punta-arco-${++siguienteLienzo}`;
  protected readonly marcadorMensaje = `${this.marcador}-mensaje`;
  protected readonly cabeceraPool = ANCHO_CABECERA_POOL;
  protected readonly cabeceraLane = ANCHO_CABECERA_LANE;

  protected readonly vista = computed(() => this.geometria().vista);
  protected readonly cajaVista = computed(() => {
    const { x, y, ancho, alto } = this.vista();
    return `${x} ${y} ${ancho} ${alto}`;
  });
  protected readonly arcos = computed(() =>
    this.geometria().arcos.flatMap(({ arco, segmento }) => (segmento ? [{ arco, segmento }] : [])),
  );
  protected readonly flujos = computed(() =>
    this.geometria().flujos.flatMap(({ flujo, segmento }) => (segmento ? [{ flujo, segmento }] : [])),
  );
  protected readonly eventos = computed<EventoDiagrama[]>(() => {
    const diagrama = this.diagrama();
    return [
      ...diagrama.messageCatches.map((datos): EventoDiagrama => ({ tipo: 'MESSAGE_CATCH', datos })),
      ...diagrama.messageThrows.map((datos): EventoDiagrama => ({ tipo: 'MESSAGE_THROW', datos })),
      ...diagrama.enviosExternos.map((datos): EventoDiagrama => ({ tipo: 'ENVIO_EXTERNO', datos })),
    ];
  });
  private readonly claveSeleccion = computed(() => {
    const seleccion = this.seleccion();
    return seleccion ? `${seleccion.tipo}:${seleccion.id}` : null;
  });

  protected seleccionado(tipo: TipoElemento, id: number): boolean {
    return this.claveSeleccion() === `${tipo}:${id}`;
  }

  protected marcado(tipo: TipoElemento, id: number): boolean {
    return this.marcados().includes(`${tipo}:${id}`);
  }

  protected elegir(tipo: TipoElemento, id: number): void {
    this.elementoElegido.emit({ tipo, id });
  }

  protected rotuloPool(banda: BandaPool): Rotulo {
    return rotulo(banda.x + ANCHO_CABECERA_POOL / 2, banda.y, banda.alto, banda.pool.nombre);
  }

  protected rotuloLane(banda: BandaLane): Rotulo {
    return rotulo(banda.x + ANCHO_CABECERA_LANE / 2, banda.y, banda.alto, banda.lane.nombre);
  }

  protected pulsarFondo(evento: MouseEvent): void {
    const marco = (evento.currentTarget as Element).getBoundingClientRect();
    const vista = this.vista();
    const escalaX = marco.width > 0 ? vista.ancho / marco.width : 1;
    const escalaY = marco.height > 0 ? vista.alto / marco.height : 1;
    this.fondoPulsado.emit({
      x: Math.round((evento.clientX - marco.left) * escalaX + vista.x),
      y: Math.round((evento.clientY - marco.top) * escalaY + vista.y),
    });
  }
}

function rotulo(x: number, y: number, alto: number, nombre: string): Rotulo {
  const medio = y + alto / 2;
  return {
    x,
    y: medio,
    texto: recortar(nombre, Math.max(4, Math.floor((alto - 16) / ANCHO_LETRA))),
    giro: `rotate(-90 ${x} ${medio})`,
  };
}
