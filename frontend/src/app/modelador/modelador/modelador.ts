import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { EMPTY, Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_TIPO_ELEMENTO, TEXTO_TIPO_GATEWAY } from '../../compartido/textos';
import {
  ActividadRespuesta,
  ArcoRespuesta,
  DiagramaProceso,
  EnvioExternoRespuesta,
  GatewayRespuesta,
  MessageCatchRespuesta,
  MessageThrowRespuesta,
} from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { Proceso } from '../../core/modelos/proceso';
import { Seleccion } from '../../core/modelos/seleccion';
import { ActividadService } from '../../core/servicios/actividad-service';
import { EnvioExternoService } from '../../core/servicios/envio-externo-service';
import { GatewayService } from '../../core/servicios/gateway-service';
import { MessageCatchService } from '../../core/servicios/message-catch-service';
import { MessageThrowService } from '../../core/servicios/message-throw-service';
import { BarraHerramientas, ElementoNuevo, Herramienta } from '../barra-herramientas/barra-herramientas';
import { motivoRechazo } from '../conexiones';
import { EliminacionElemento, ElementoEliminable } from '../eliminacion-elemento/eliminacion-elemento';
import { PropuestaEvento, sistemasExternos } from '../eventos';
import { FormularioActividad, PropuestaActividad } from '../formulario-actividad/formulario-actividad';
import { FormularioArco, PropuestaArco } from '../formulario-arco/formulario-arco';
import { FormularioEnvioExterno } from '../formulario-envio-externo/formulario-envio-externo';
import { FormularioGateway, PropuestaGateway } from '../formulario-gateway/formulario-gateway';
import { FormularioMessageCatch } from '../formulario-message-catch/formulario-message-catch';
import { FormularioMessageThrow } from '../formulario-message-throw/formulario-message-throw';
import {
  DestinoMovimiento,
  Punto,
  buscarNodo,
  calcularGeometria,
  colocarEnBanda,
  existeElemento,
  posicionLibre,
  ubicacionEn,
  ubicarMovimiento,
} from '../geometria';
import { LienzoDiagrama, MovimientoNodo } from '../lienzo-diagrama/lienzo-diagrama';
import { motivoMovimientoNoValido, movimientoSinCambios } from '../movimientos';
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
  | { tipo: 'ARCO'; arco: ArcoRespuesta | null; propuesta: PropuestaArco | null }
  | { tipo: 'MESSAGE_THROW'; evento: MessageThrowRespuesta | null; propuesta: PropuestaEvento | null }
  | { tipo: 'MESSAGE_CATCH'; evento: MessageCatchRespuesta | null; propuesta: PropuestaEvento | null }
  | { tipo: 'ENVIO_EXTERNO'; evento: EnvioExternoRespuesta | null; propuesta: PropuestaEvento | null };

type TipoEventoNuevo = 'MESSAGE_THROW' | 'MESSAGE_CATCH' | 'ENVIO_EXTERNO';

type SolicitudDe<T extends Solicitud['tipo']> = Extract<Solicitud, { tipo: T }>;

@Component({
  selector: 'app-modelador',
  imports: [
    Aviso,
    BarraHerramientas,
    EliminacionElemento,
    FormularioActividad,
    FormularioArco,
    FormularioEnvioExterno,
    FormularioGateway,
    FormularioMessageCatch,
    FormularioMessageThrow,
    LienzoDiagrama,
    PanelElemento,
  ],
  templateUrl: './modelador.html',
  styleUrl: './modelador.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Modelador {
  private readonly actividades = inject(ActividadService);
  private readonly gateways = inject(GatewayService);
  private readonly messageThrows = inject(MessageThrowService);
  private readonly messageCatches = inject(MessageCatchService);
  private readonly envios = inject(EnvioExternoService);

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
    () => this.escritura() && this.herramienta() !== 'SELECCION' && this.herramienta() !== 'ARCO',
  );
  protected readonly solicitud = signal<Solicitud | null>(null);
  protected readonly solicitudActividad = computed(() => this.solicitudDe('ACTIVIDAD'));
  protected readonly solicitudGateway = computed(() => this.solicitudDe('GATEWAY'));
  protected readonly solicitudArco = computed(() => this.solicitudDe('ARCO'));
  protected readonly solicitudThrow = computed(() => this.solicitudDe('MESSAGE_THROW'));
  protected readonly solicitudCatch = computed(() => this.solicitudDe('MESSAGE_CATCH'));
  protected readonly solicitudEnvio = computed(() => this.solicitudDe('ENVIO_EXTERNO'));
  protected readonly porEliminar = signal<ElementoEliminable | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly modificable = computed<ElementoEliminable | null>(() => this.seleccion());
  protected readonly puedeEditar = computed(() => this.escritura() && this.modificable() !== null);
  protected readonly puedeEliminar = computed(() => this.edicion()?.eliminar === true && this.modificable() !== null);
  protected readonly provisional = linkedSignal<DiagramaProceso, MovimientoNodo | null>({
    source: this.diagrama,
    computation: () => null,
  });
  protected readonly errorMovimiento = signal<string | null>(null);
  protected readonly movible = computed(
    () =>
      this.escritura() &&
      this.herramienta() === 'SELECCION' &&
      this.solicitud() === null &&
      this.porEliminar() === null &&
      this.provisional() === null,
  );

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
        break;
      default:
        this.proponerEvento(this.herramienta() as TipoEventoNuevo, punto);
    }
  }

  protected mover({ elemento, esquina }: MovimientoNodo): void {
    const diagrama = this.diagrama();
    const geometria = this.geometria();
    const nodo = buscarNodo(geometria, elemento);
    if (!nodo || !this.movible()) {
      return;
    }
    this.mensaje.set(null);
    this.errorMovimiento.set(null);
    const destino = ubicarMovimiento(diagrama, geometria, elemento, esquina);
    const motivo = motivoMovimientoNoValido(geometria, nodo, destino, diagrama.arcos);
    if (motivo !== null || destino === null) {
      this.errorMovimiento.set(motivo);
      return;
    }
    if (movimientoSinCambios(nodo, destino)) {
      return;
    }
    const otraLane = elemento.tipo === 'ACTIVIDAD' && destino.lane !== null && destino.lane.id !== nodo.laneId;
    this.provisional.set({ elemento, esquina: destino.posicion });
    this.guardarMovimiento(elemento, destino).subscribe({
      next: () =>
        this.confirmar(
          elemento,
          `Se guardó la nueva posición de «${nodo.nombre}»${otraLane ? ` en la lane «${destino.lane?.nombre}»` : ''}.`,
        ),
      error: (fallo: FalloHttp) => {
        this.provisional.set(null);
        this.errorMovimiento.set(
          `No se pudo mover «${nodo.nombre}»: ${fallo.detalle ?? fallo.titulo}. Se mantiene su posición anterior.`,
        );
      },
    });
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
        return;
      }
      default: {
        const pool = pools[0];
        if (!pool) {
          this.indicacion.set('El proceso no tiene pools que admitan eventos.');
          return;
        }
        this.abrirEvento(tipo, {
          poolId: pool.pool.id,
          posicion: posicionLibre(geometria, 'EVENTO', pool.lanes[0] ?? pool),
        });
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
        return;
      }
      case 'MESSAGE_THROW': {
        const evento = diagrama.messageThrows.find((actual) => actual.id === seleccion.id) ?? null;
        this.abrir({ tipo: 'MESSAGE_THROW', evento, propuesta: null });
        return;
      }
      case 'MESSAGE_CATCH': {
        const evento = diagrama.messageCatches.find((actual) => actual.id === seleccion.id) ?? null;
        this.abrir({ tipo: 'MESSAGE_CATCH', evento, propuesta: null });
        return;
      }
      case 'ENVIO_EXTERNO': {
        const evento = diagrama.enviosExternos.find((actual) => actual.id === seleccion.id) ?? null;
        this.abrir({ tipo: 'ENVIO_EXTERNO', evento, propuesta: null });
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

  protected throwGuardado(evento: MessageThrowRespuesta): void {
    this.confirmar(
      { tipo: 'MESSAGE_THROW', id: evento.id },
      conAdvertencias(`El Message Throw «${evento.nombreMensaje}» se guardó en el servidor.`, evento.advertencias),
    );
  }

  protected catchGuardado(evento: MessageCatchRespuesta): void {
    this.confirmar(
      { tipo: 'MESSAGE_CATCH', id: evento.id },
      conAdvertencias(`El Message Catch «${evento.nombreMensaje}» se guardó en el servidor.`, evento.advertencias),
    );
  }

  protected envioGuardado(evento: EnvioExternoRespuesta): void {
    this.confirmar(
      { tipo: 'ENVIO_EXTERNO', id: evento.id },
      conAdvertencias(`El envío externo «${evento.nombreMensaje}» se guardó en el servidor.`, evento.advertencias),
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
    this.errorMovimiento.set(null);
    this.solicitud.set(solicitud);
  }

  private guardarMovimiento(elemento: Seleccion, destino: DestinoMovimiento): Observable<unknown> {
    const procesoId = this.proceso().id;
    const posicion = { posicionX: destino.posicion.x, posicionY: destino.posicion.y };
    switch (elemento.tipo) {
      case 'ACTIVIDAD':
        return destino.lane === null
          ? EMPTY
          : this.actividades.mover(procesoId, elemento.id, { laneId: destino.lane.id, ...posicion });
      case 'GATEWAY':
        return this.gateways.mover(procesoId, elemento.id, posicion);
      case 'MESSAGE_THROW':
        return this.messageThrows.mover(procesoId, elemento.id, posicion);
      case 'MESSAGE_CATCH':
        return this.messageCatches.mover(procesoId, elemento.id, posicion);
      case 'ENVIO_EXTERNO':
        return this.envios.mover(procesoId, elemento.id, posicion);
      case 'ARCO':
        return EMPTY;
    }
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

  private proponerEvento(tipo: TipoEventoNuevo, punto: Punto): void {
    const geometria = this.geometria();
    const ubicacion = ubicacionEn(geometria, punto);
    if (!ubicacion || ubicacion.pool.pool.cajaNegra) {
      this.indicacion.set('Ese punto no está dentro de un pool que admita eventos. Pulsa dentro de un pool sin caja negra.');
      return;
    }
    this.abrirEvento(tipo, {
      poolId: ubicacion.pool.pool.id,
      posicion: colocarEnBanda('EVENTO', punto, ubicacion.lane ?? ubicacion.pool, geometria.inicioContenido),
    });
  }

  private abrirEvento(tipo: TipoEventoNuevo, propuesta: PropuestaEvento): void {
    const diagrama = this.diagrama();
    if (tipo === 'MESSAGE_THROW' && diagrama.pools.length < 2) {
      this.indicacion.set('Un Message Throw necesita otro pool como receptor. Crea antes otro pool del proceso.');
      return;
    }
    if (tipo === 'ENVIO_EXTERNO' && sistemasExternos(diagrama).length === 0) {
      this.indicacion.set('Un envío externo se dirige a un pool externo de caja negra. Crea antes ese pool.');
      return;
    }
    this.indicacion.set(null);
    switch (tipo) {
      case 'MESSAGE_THROW':
        this.abrir({ tipo, evento: null, propuesta });
        return;
      case 'MESSAGE_CATCH':
        this.abrir({ tipo, evento: null, propuesta });
        return;
      case 'ENVIO_EXTERNO':
        this.abrir({ tipo, evento: null, propuesta });
    }
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

function conAdvertencias(texto: string, advertencias: readonly string[]): string {
  if (advertencias.length === 0) {
    return texto;
  }
  const cuantas = advertencias.length === 1 ? 'una advertencia' : `${advertencias.length} advertencias`;
  return `${texto} El servidor informa ${cuantas}: revísalas en el panel del elemento.`;
}
