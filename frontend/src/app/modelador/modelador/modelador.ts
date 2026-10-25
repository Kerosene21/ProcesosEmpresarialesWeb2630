import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_TIPO_ELEMENTO, TEXTO_TIPO_GATEWAY } from '../../compartido/textos';
import { ActividadRespuesta, ArcoRespuesta, DiagramaProceso, GatewayRespuesta } from '../../core/modelos/diagrama';
import { Proceso } from '../../core/modelos/proceso';
import { Seleccion } from '../../core/modelos/seleccion';
import { BarraHerramientas, ElementoNuevo, Herramienta } from '../barra-herramientas/barra-herramientas';
import { motivoRechazo } from '../conexiones';
import { EliminacionElemento, ElementoEliminable } from '../eliminacion-elemento/eliminacion-elemento';
import { FormularioActividad, PropuestaActividad } from '../formulario-actividad/formulario-actividad';
import { FormularioArco, PropuestaArco } from '../formulario-arco/formulario-arco';
import { FormularioGateway, PropuestaGateway } from '../formulario-gateway/formulario-gateway';
import {
  Punto,
  buscarNodo,
  calcularGeometria,
  colocarEnBanda,
  existeElemento,
  posicionLibre,
  ubicacionEn,
} from '../geometria';
import { LienzoDiagrama } from '../lienzo-diagrama/lienzo-diagrama';
import { PanelElemento } from '../panel-elemento/panel-elemento';

export interface PermisosEdicion {
  escribir: boolean;
  eliminar: boolean;
}

interface ElementoListado {
  seleccion: Seleccion;
  nombre: string;
}

interface GrupoElementos {
  titulo: string;
  elementos: ElementoListado[];
}

type Solicitud =
  | { tipo: 'ACTIVIDAD'; actividad: ActividadRespuesta | null; propuesta: PropuestaActividad | null }
  | { tipo: 'GATEWAY'; gateway: GatewayRespuesta | null; propuesta: PropuestaGateway | null }
  | { tipo: 'ARCO'; arco: ArcoRespuesta | null; propuesta: PropuestaArco | null };

type SolicitudDe<T extends Solicitud['tipo']> = Extract<Solicitud, { tipo: T }>;

@Component({
  selector: 'app-modelador',
  imports: [
    Aviso,
    BarraHerramientas,
    EliminacionElemento,
    FormularioActividad,
    FormularioArco,
    FormularioGateway,
    LienzoDiagrama,
    PanelElemento,
  ],
  templateUrl: './modelador.html',
  styleUrl: './modelador.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Modelador {
  readonly proceso = input.required<Proceso>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly edicion = input<PermisosEdicion | null>(null);
  readonly cambio = output<void>();

  protected readonly geometria = computed(() => calcularGeometria(this.diagrama()));
  private readonly elegida = signal<Seleccion | null>(null);
  protected readonly seleccion = computed(() => {
    const elegida = this.elegida();
    return elegida && existeElemento(this.diagrama(), elegida) ? elegida : null;
  });
  protected readonly vacio = computed(() => this.geometria().nodos.length === 0 && this.diagrama().arcos.length === 0);
  protected readonly grupos = computed<GrupoElementos[]>(() => {
    const diagrama = this.diagrama();
    const grupos: GrupoElementos[] = [
      {
        titulo: 'Actividades',
        elementos: diagrama.actividades.map((actividad) => ({
          seleccion: { tipo: 'ACTIVIDAD', id: actividad.id },
          nombre: actividad.nombre,
        })),
      },
      {
        titulo: 'Gateways',
        elementos: diagrama.gateways.map((gateway) => ({
          seleccion: { tipo: 'GATEWAY', id: gateway.id },
          nombre: `${TEXTO_TIPO_GATEWAY[gateway.tipo]} #${gateway.id}`,
        })),
      },
      {
        titulo: 'Arcos',
        elementos: diagrama.arcos.map((arco) => ({
          seleccion: { tipo: 'ARCO', id: arco.id },
          nombre: `${arco.origenNombre} → ${arco.destinoNombre}`,
        })),
      },
      {
        titulo: 'Eventos',
        elementos: [
          ...diagrama.messageCatches.map((evento): ElementoListado => ({
            seleccion: { tipo: 'MESSAGE_CATCH', id: evento.id },
            nombre: `${TEXTO_TIPO_ELEMENTO.MESSAGE_CATCH}: ${evento.nombreMensaje}`,
          })),
          ...diagrama.messageThrows.map((evento): ElementoListado => ({
            seleccion: { tipo: 'MESSAGE_THROW', id: evento.id },
            nombre: `${TEXTO_TIPO_ELEMENTO.MESSAGE_THROW}: ${evento.nombreMensaje}`,
          })),
          ...diagrama.enviosExternos.map((evento): ElementoListado => ({
            seleccion: { tipo: 'ENVIO_EXTERNO', id: evento.id },
            nombre: `${TEXTO_TIPO_ELEMENTO.ENVIO_EXTERNO}: ${evento.nombreMensaje}`,
          })),
        ],
      },
    ];
    return grupos.filter((grupo) => grupo.elementos.length > 0);
  });

  protected readonly escritura = computed(() => this.edicion()?.escribir === true);
  protected readonly herramienta = signal<Herramienta>('SELECCION');
  protected readonly indicacion = signal<string | null>(null);
  private readonly origenArco = signal<Seleccion | null>(null);
  protected readonly marcados = computed(() => {
    const origen = this.origenArco();
    return origen ? [`${origen.tipo}:${origen.id}`] : [];
  });
  protected readonly colocando = computed(
    () => this.escritura() && (this.herramienta() === 'ACTIVIDAD' || this.herramienta() === 'GATEWAY'),
  );
  protected readonly solicitud = signal<Solicitud | null>(null);
  protected readonly solicitudActividad = computed(() => this.solicitudDe('ACTIVIDAD'));
  protected readonly solicitudGateway = computed(() => this.solicitudDe('GATEWAY'));
  protected readonly solicitudArco = computed(() => this.solicitudDe('ARCO'));
  protected readonly porEliminar = signal<ElementoEliminable | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly modificable = computed(() => {
    const seleccion = this.seleccion();
    return seleccion !== null && esModificable(seleccion) ? seleccion : null;
  });
  protected readonly puedeEditar = computed(() => this.escritura() && this.modificable() !== null);
  protected readonly puedeEliminar = computed(() => this.edicion()?.eliminar === true && this.modificable() !== null);

  protected seleccionar(seleccion: Seleccion): void {
    this.elegida.set(seleccion);
  }

  protected limpiar(): void {
    this.elegida.set(null);
  }

  protected estaSeleccionado(seleccion: Seleccion): boolean {
    const actual = this.seleccion();
    return actual !== null && actual.tipo === seleccion.tipo && actual.id === seleccion.id;
  }

  protected elegir(seleccion: Seleccion): void {
    if (this.escritura() && this.herramienta() === 'ARCO' && seleccion.tipo !== 'ARCO') {
      this.elegirExtremo(seleccion);
    } else {
      this.seleccionar(seleccion);
    }
  }

  protected pulsarFondo(punto: Punto): void {
    switch (this.escritura() ? this.herramienta() : 'SELECCION') {
      case 'ACTIVIDAD':
        this.proponerActividad(punto);
        break;
      case 'GATEWAY':
        this.proponerGateway(punto);
        break;
      case 'ARCO':
        this.origenArco.set(null);
        this.indicacion.set(null);
        break;
      case 'SELECCION':
        this.limpiar();
    }
  }

  protected elegirHerramienta(herramienta: Herramienta): void {
    this.herramienta.set(herramienta);
    this.origenArco.set(null);
    this.indicacion.set(null);
  }

  protected nuevo(tipo: ElementoNuevo): void {
    const geometria = this.geometria();
    const pools = geometria.pools.filter((banda) => !banda.pool.cajaNegra);
    switch (tipo) {
      case 'ACTIVIDAD': {
        const lane = pools.flatMap((banda) => banda.lanes)[0];
        if (!lane) {
          this.indicacion.set('El proceso no tiene lanes. Crea una lane antes de agregar actividades.');
          return;
        }
        this.abrir({
          tipo,
          actividad: null,
          propuesta: { laneId: lane.lane.id, posicion: posicionLibre(geometria, 'ACTIVIDAD', lane) },
        });
        return;
      }
      case 'GATEWAY': {
        const pool = pools[0];
        if (!pool) {
          this.indicacion.set('El proceso no tiene pools que admitan gateways.');
          return;
        }
        this.abrir({
          tipo,
          gateway: null,
          propuesta: { poolId: pool.pool.id, posicion: posicionLibre(geometria, 'GATEWAY', pool.lanes[0] ?? pool) },
        });
        return;
      }
      case 'ARCO': {
        const seleccion = this.seleccion();
        const origen = seleccion && seleccion.tipo !== 'ARCO' ? seleccion : null;
        this.abrir({ tipo, arco: null, propuesta: { origen, destino: null } });
      }
    }
  }

  protected editar(): void {
    const seleccion = this.modificable();
    const diagrama = this.diagrama();
    if (!seleccion) {
      return;
    }
    switch (seleccion.tipo) {
      case 'ACTIVIDAD': {
        const actividad = diagrama.actividades.find((actual) => actual.id === seleccion.id) ?? null;
        this.abrir({ tipo: 'ACTIVIDAD', actividad, propuesta: null });
        return;
      }
      case 'GATEWAY': {
        const gateway = diagrama.gateways.find((actual) => actual.id === seleccion.id) ?? null;
        this.abrir({ tipo: 'GATEWAY', gateway, propuesta: null });
        return;
      }
      case 'ARCO': {
        const arco = diagrama.arcos.find((actual) => actual.id === seleccion.id) ?? null;
        this.abrir({ tipo: 'ARCO', arco, propuesta: null });
      }
    }
  }

  protected eliminar(): void {
    this.mensaje.set(null);
    this.porEliminar.set(this.modificable());
  }

  protected cancelar(): void {
    this.solicitud.set(null);
  }

  protected actividadGuardada(actividad: ActividadRespuesta): void {
    this.confirmar({ tipo: 'ACTIVIDAD', id: actividad.id }, `La actividad «${actividad.nombre}» se guardó en el servidor.`);
  }

  protected gatewayGuardado(gateway: GatewayRespuesta): void {
    this.confirmar({ tipo: 'GATEWAY', id: gateway.id }, `«${gateway.etiqueta}» se guardó en el servidor.`);
  }

  protected arcoGuardado(arco: ArcoRespuesta): void {
    this.confirmar(
      { tipo: 'ARCO', id: arco.id },
      `El arco «${arco.origenNombre} → ${arco.destinoNombre}» se guardó en el servidor.`,
    );
  }

  protected eliminado(texto: string): void {
    this.porEliminar.set(null);
    this.confirmar(null, texto);
  }

  private confirmar(seleccion: Seleccion | null, texto: string): void {
    this.solicitud.set(null);
    this.elegida.set(seleccion);
    this.mensaje.set(texto);
    this.cambio.emit();
  }

  private abrir(solicitud: Solicitud): void {
    this.mensaje.set(null);
    this.solicitud.set(solicitud);
  }

  private solicitudDe<T extends Solicitud['tipo']>(tipo: T): SolicitudDe<T> | null {
    const solicitud = this.solicitud();
    return solicitud?.tipo === tipo ? (solicitud as SolicitudDe<T>) : null;
  }

  private proponerActividad(punto: Punto): void {
    const geometria = this.geometria();
    const ubicacion = ubicacionEn(geometria, punto);
    if (!ubicacion?.lane || ubicacion.pool.pool.cajaNegra) {
      this.indicacion.set('Ese punto no está dentro de una lane. Pulsa dentro de una lane para ubicar la actividad.');
      return;
    }
    this.indicacion.set(null);
    this.abrir({
      tipo: 'ACTIVIDAD',
      actividad: null,
      propuesta: {
        laneId: ubicacion.lane.lane.id,
        posicion: colocarEnBanda('ACTIVIDAD', punto, ubicacion.lane, geometria.inicioContenido),
      },
    });
  }

  private proponerGateway(punto: Punto): void {
    const geometria = this.geometria();
    const ubicacion = ubicacionEn(geometria, punto);
    if (!ubicacion || ubicacion.pool.pool.cajaNegra) {
      this.indicacion.set('Ese punto no está dentro de un pool que admita gateways. Pulsa dentro del pool.');
      return;
    }
    this.indicacion.set(null);
    this.abrir({
      tipo: 'GATEWAY',
      gateway: null,
      propuesta: {
        poolId: ubicacion.pool.pool.id,
        posicion: colocarEnBanda('GATEWAY', punto, ubicacion.lane ?? ubicacion.pool, geometria.inicioContenido),
      },
    });
  }

  private elegirExtremo(seleccion: Seleccion): void {
    const geometria = this.geometria();
    const nodo = buscarNodo(geometria, seleccion);
    if (!nodo) {
      return;
    }
    const origen = buscarNodo(geometria, this.origenArco());
    if (!origen) {
      this.origenArco.set(seleccion);
      this.indicacion.set(`Origen: «${nodo.nombre}». Pulsa el nodo de destino.`);
      return;
    }
    const motivo = motivoRechazo(origen, nodo);
    if (motivo) {
      this.indicacion.set(`${motivo}. Elige otro destino o pulsa el fondo para empezar de nuevo.`);
      return;
    }
    this.origenArco.set(null);
    this.indicacion.set(null);
    this.abrir({ tipo: 'ARCO', arco: null, propuesta: { origen: origen.elemento, destino: nodo.elemento } });
  }
}

function esModificable(seleccion: Seleccion): seleccion is ElementoEliminable {
  return seleccion.tipo === 'ACTIVIDAD' || seleccion.tipo === 'GATEWAY' || seleccion.tipo === 'ARCO';
}
