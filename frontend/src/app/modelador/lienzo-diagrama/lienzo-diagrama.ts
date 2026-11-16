import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { TEXTO_TIPO_POOL } from '../../compartido/textos';
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
  Rectangulo,
  buscarNodo,
  desplazarNodo,
  recortar,
} from '../geometria';
import { NodoActividad } from '../nodo-actividad/nodo-actividad';
import { NodoEvento } from '../nodo-evento/nodo-evento';
import { NodoGateway } from '../nodo-gateway/nodo-gateway';

export interface MovimientoNodo {
  elemento: Seleccion;
  esquina: Punto;
}

interface Arrastre {
  elemento: Seleccion;
  inicio: Punto;
  caja: Rectangulo;
  actual: Punto;
  moviendo: boolean;
}

interface Rotulo {
  x: number;
  y: number;
  texto: string;
  giro: string;
}

const ANCHO_LETRA = 7;
const UMBRAL_ARRASTRE = 4;
const TIPOS_MOVIBLES: readonly TipoElemento[] = ['ACTIVIDAD', 'GATEWAY', 'MESSAGE_THROW', 'MESSAGE_CATCH', 'ENVIO_EXTERNO'];

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
  readonly movible = input(false);
  readonly provisional = input<MovimientoNodo | null>(null);
  readonly elementoElegido = output<Seleccion>();
  readonly fondoPulsado = output<Punto>();
  readonly nodoMovido = output<MovimientoNodo>();

  protected readonly marcador = `punta-arco-${++siguienteLienzo}`;
  protected readonly marcadorMensaje = `${this.marcador}-mensaje`;
  protected readonly cabeceraPool = ANCHO_CABECERA_POOL;
  protected readonly cabeceraLane = ANCHO_CABECERA_LANE;
  protected readonly textoTipoPool = TEXTO_TIPO_POOL;

  protected readonly vista = computed(() => this.geometria().vista);
  protected readonly cajaVista = computed(() => {
    const { x, y, ancho, alto } = this.vista();
    return `${x} ${y} ${ancho} ${alto}`;
  });
  private readonly arrastre = signal<Arrastre | null>(null);
  private ignorarClic = false;
  protected readonly arrastrando = computed(() => this.arrastre()?.moviendo === true);
  protected readonly desplazado = computed<MovimientoNodo | null>(() => {
    const arrastre = this.arrastre();
    return arrastre?.moviendo ? { elemento: arrastre.elemento, esquina: esquinaDe(arrastre) } : this.provisional();
  });
  private readonly geometriaVisible = computed(() => {
    const desplazado = this.desplazado();
    return desplazado ? desplazarNodo(this.geometria(), desplazado.elemento, desplazado.esquina) : this.geometria();
  });
  protected readonly arcos = computed(() =>
    this.geometriaVisible().arcos.flatMap(({ arco, segmento }) => (segmento ? [{ arco, segmento }] : [])),
  );
  protected readonly flujos = computed(() =>
    this.geometriaVisible().flujos.flatMap(({ flujo, segmento }) => (segmento ? [{ flujo, segmento }] : [])),
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
    if (this.descartarClic()) {
      return;
    }
    this.elementoElegido.emit({ tipo, id });
  }

  protected traslado(tipo: TipoElemento, id: number): string | null {
    const desplazado = this.desplazado();
    if (!desplazado || desplazado.elemento.tipo !== tipo || desplazado.elemento.id !== id) {
      return null;
    }
    const nodo = buscarNodo(this.geometria(), desplazado.elemento);
    return nodo ? `translate(${desplazado.esquina.x - nodo.caja.x} ${desplazado.esquina.y - nodo.caja.y})` : null;
  }

  protected iniciarArrastre(evento: PointerEvent): void {
    this.ignorarClic = false;
    if (!this.movible() || evento.button !== 0) {
      return;
    }
    const elemento = nodoBajo(evento.target);
    const nodo = elemento ? buscarNodo(this.geometria(), elemento) : null;
    if (!elemento || !nodo) {
      return;
    }
    const punto = this.puntoEnLienzo(evento);
    this.arrastre.set({ elemento, inicio: punto, caja: nodo.caja, actual: punto, moviendo: false });
  }

  protected arrastrar(evento: PointerEvent): void {
    const arrastre = this.arrastre();
    if (!arrastre) {
      return;
    }
    const actual = this.puntoEnLienzo(evento);
    const moviendo =
      arrastre.moviendo ||
      Math.hypot(actual.x - arrastre.inicio.x, actual.y - arrastre.inicio.y) >= UMBRAL_ARRASTRE;
    if (moviendo && !arrastre.moviendo) {
      const lienzo = evento.currentTarget as Element;
      if (typeof lienzo.setPointerCapture === 'function') {
        lienzo.setPointerCapture(evento.pointerId);
      }
    }
    this.arrastre.set({ ...arrastre, actual, moviendo });
  }

  protected soltar(evento: PointerEvent): void {
    const arrastre = this.arrastre();
    this.arrastre.set(null);
    if (!arrastre?.moviendo) {
      return;
    }
    this.ignorarClic = true;
    this.nodoMovido.emit({ elemento: arrastre.elemento, esquina: esquinaDe({ ...arrastre, actual: this.puntoEnLienzo(evento) }) });
  }

  protected cancelarArrastre(): void {
    this.arrastre.set(null);
  }

  protected rotuloPool(banda: BandaPool): Rotulo {
    return rotulo(banda.x + ANCHO_CABECERA_POOL / 2, banda.y, banda.alto, banda.pool.nombre);
  }

  protected descripcionPool(banda: BandaPool): string {
    const { nombre, tipo, empresaNombre, cajaNegra } = banda.pool;
    const empresa = empresaNombre ? ` · ${empresaNombre}` : '';
    return `Pool ${TEXTO_TIPO_POOL[tipo].toLowerCase()} «${nombre}»${empresa}${cajaNegra ? ' · caja negra' : ''}`;
  }

  protected rotuloLane(banda: BandaLane): Rotulo {
    return rotulo(banda.x + ANCHO_CABECERA_LANE / 2, banda.y, banda.alto, banda.lane.nombre);
  }

  protected pulsarFondo(evento: MouseEvent): void {
    if (this.descartarClic()) {
      return;
    }
    this.fondoPulsado.emit(this.puntoEnLienzo(evento));
  }

  private descartarClic(): boolean {
    const descartar = this.ignorarClic;
    this.ignorarClic = false;
    return descartar;
  }

  private puntoEnLienzo(evento: MouseEvent): Punto {
    const marco = (evento.currentTarget as Element).getBoundingClientRect();
    const vista = this.vista();
    const escalaX = marco.width > 0 ? vista.ancho / marco.width : 1;
    const escalaY = marco.height > 0 ? vista.alto / marco.height : 1;
    return {
      x: Math.round((evento.clientX - marco.left) * escalaX + vista.x),
      y: Math.round((evento.clientY - marco.top) * escalaY + vista.y),
    };
  }
}

function esquinaDe(arrastre: Arrastre): Punto {
  return {
    x: Math.round(arrastre.caja.x + arrastre.actual.x - arrastre.inicio.x),
    y: Math.round(arrastre.caja.y + arrastre.actual.y - arrastre.inicio.y),
  };
}

function nodoBajo(destino: EventTarget | null): Seleccion | null {
  const clave = destino instanceof Element ? destino.closest('[data-nodo]')?.getAttribute('data-nodo') : null;
  const [tipo, id] = clave?.split(':') ?? [];
  const elegido = TIPOS_MOVIBLES.find((actual) => actual === tipo);
  return elegido && Number.isInteger(Number(id)) ? { tipo: elegido, id: Number(id) } : null;
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
