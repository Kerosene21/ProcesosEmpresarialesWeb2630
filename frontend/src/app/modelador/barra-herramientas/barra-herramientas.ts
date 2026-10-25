import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export type Herramienta =
  | 'SELECCION'
  | 'ACTIVIDAD'
  | 'GATEWAY'
  | 'ARCO'
  | 'MESSAGE_THROW'
  | 'MESSAGE_CATCH'
  | 'ENVIO_EXTERNO';

export type ElementoNuevo = Exclude<Herramienta, 'SELECCION'>;

interface OpcionHerramienta {
  valor: Herramienta;
  nombre: string;
  ayuda: string;
}

const HERRAMIENTAS: readonly OpcionHerramienta[] = [
  { valor: 'SELECCION', nombre: 'Seleccionar', ayuda: 'Pulsa un elemento del diagrama para consultarlo o editarlo.' },
  { valor: 'ACTIVIDAD', nombre: 'Actividad', ayuda: 'Pulsa dentro de una lane para ubicar la nueva actividad.' },
  { valor: 'GATEWAY', nombre: 'Gateway', ayuda: 'Pulsa dentro de un pool para ubicar el nuevo gateway.' },
  { valor: 'ARCO', nombre: 'Arco', ayuda: 'Pulsa el nodo de origen y después el nodo de destino.' },
  {
    valor: 'MESSAGE_THROW',
    nombre: 'Message Throw',
    ayuda: 'Pulsa dentro del pool que emite el mensaje. El pool receptor se elige en el formulario.',
  },
  {
    valor: 'MESSAGE_CATCH',
    nombre: 'Message Catch',
    ayuda: 'Pulsa dentro del pool que recibe el mensaje para ubicar el evento.',
  },
  {
    valor: 'ENVIO_EXTERNO',
    nombre: 'Envío externo',
    ayuda: 'Pulsa dentro del pool que envía. El destino es un pool externo de caja negra.',
  },
];

const NUEVOS: readonly { valor: ElementoNuevo; nombre: string }[] = [
  { valor: 'ACTIVIDAD', nombre: 'Nueva actividad' },
  { valor: 'GATEWAY', nombre: 'Nuevo gateway' },
  { valor: 'ARCO', nombre: 'Nuevo arco' },
  { valor: 'MESSAGE_THROW', nombre: 'Nuevo Message Throw' },
  { valor: 'MESSAGE_CATCH', nombre: 'Nuevo Message Catch' },
  { valor: 'ENVIO_EXTERNO', nombre: 'Nuevo envío externo' },
];

@Component({
  selector: 'app-barra-herramientas',
  templateUrl: './barra-herramientas.html',
  styleUrl: './barra-herramientas.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarraHerramientas {
  readonly herramienta = input.required<Herramienta>();
  readonly indicacion = input<string | null>(null);
  readonly herramientaElegida = output<Herramienta>();
  readonly nuevo = output<ElementoNuevo>();

  protected readonly herramientas = HERRAMIENTAS;
  protected readonly nuevos = NUEVOS;
  protected readonly ayuda = computed(
    () => this.indicacion() ?? HERRAMIENTAS.find((opcion) => opcion.valor === this.herramienta())?.ayuda ?? '',
  );
}
