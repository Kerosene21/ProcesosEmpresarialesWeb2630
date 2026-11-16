import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Observable, of, switchMap } from 'rxjs';

import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { Seleccion } from '../../core/modelos/seleccion';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ActividadService } from '../../core/servicios/actividad-service';
import { ArcoService } from '../../core/servicios/arco-service';
import { EnvioExternoService } from '../../core/servicios/envio-externo-service';
import { GatewayService } from '../../core/servicios/gateway-service';
import { MessageCatchService } from '../../core/servicios/message-catch-service';
import { MessageThrowService } from '../../core/servicios/message-throw-service';
import { arcosConectados } from '../conexiones';
import { GeometriaDiagrama, buscarNodo } from '../geometria';

export type ElementoEliminable = Seleccion;

type ConVistaPrevia = Seleccion & { tipo: 'GATEWAY' | 'MESSAGE_THROW' | 'MESSAGE_CATCH' | 'ENVIO_EXTERNO' };

const NOMBRE_TIPO: Record<ElementoEliminable['tipo'], string> = {
  ACTIVIDAD: 'la actividad',
  GATEWAY: 'el gateway',
  ARCO: 'el arco',
  MESSAGE_THROW: 'el Message Throw',
  MESSAGE_CATCH: 'el Message Catch',
  ENVIO_EXTERNO: 'el envío externo',
};

@Component({
  selector: 'app-eliminacion-elemento',
  imports: [DialogoConfirmacion],
  templateUrl: './eliminacion-elemento.html',
  styleUrl: './eliminacion-elemento.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EliminacionElemento {
  private readonly actividades = inject(ActividadService);
  private readonly gateways = inject(GatewayService);
  private readonly arcos = inject(ArcoService);
  private readonly messageThrows = inject(MessageThrowService);
  private readonly messageCatches = inject(MessageCatchService);
  private readonly envios = inject(EnvioExternoService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly elemento = input.required<ElementoEliminable>();
  readonly eliminado = output<string>();
  readonly cancelado = output<void>();

  protected readonly vistaPrevia = toSignal(
    toObservable(computed(() => ({ procesoId: this.procesoId(), elemento: this.elemento() }))).pipe(
      switchMap(({ procesoId, elemento }) => this.consultarVistaPrevia(procesoId, elemento)),
    ),
    { initialValue: null },
  );
  protected readonly consultando = computed(() => this.vistaPrevia()?.tipo === 'cargando');
  protected readonly falloVistaPrevia = computed(() => {
    const estado = this.vistaPrevia();
    return estado?.tipo === 'error' ? estado.fallo : null;
  });
  protected readonly nombre = computed(() => {
    const elemento = this.elemento();
    const diagrama = this.diagrama();
    if (elemento.tipo === 'ARCO') {
      const arco = diagrama.arcos.find((actual) => actual.id === elemento.id);
      return arco ? `${arco.origenNombre} → ${arco.destinoNombre}` : `Arco #${elemento.id}`;
    }
    return nombreDeMensaje(diagrama, elemento) ?? buscarNodo(this.geometria(), elemento)?.nombre ?? `#${elemento.id}`;
  });
  protected readonly titulo = computed(() => `¿Eliminar ${NOMBRE_TIPO[this.elemento().tipo]}?`);
  protected readonly consecuencias = computed(() => {
    const elemento = this.elemento();
    switch (elemento.tipo) {
      case 'ACTIVIDAD': {
        const nodo = buscarNodo(this.geometria(), elemento);
        const conectados = nodo ? arcosConectados(this.diagrama().arcos, nodo) : [];
        return conectados.length === 0
          ? ['La actividad no tiene arcos conectados.']
          : [
              `Se desactivarán ${conectados.length === 1 ? 'el arco conectado' : `los ${conectados.length} arcos conectados`}:`,
              ...conectados.map((arco) => `${arco.origenNombre} → ${arco.destinoNombre}`),
            ];
      }
      case 'GATEWAY':
      case 'MESSAGE_THROW':
      case 'MESSAGE_CATCH':
      case 'ENVIO_EXTERNO': {
        const estado = this.vistaPrevia();
        if (estado?.tipo !== 'listo') {
          return [];
        }
        return estado.datos.advertencias.length > 0
          ? estado.datos.advertencias
          : ['El servidor no informa consecuencias adicionales.'];
      }
      case 'ARCO': {
        const arco = this.diagrama().arcos.find((actual) => actual.id === elemento.id);
        return arco?.condicion ? [`Se perderá la condición «${arco.condicion}».`] : [];
      }
    }
  });

  protected readonly eliminando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected confirmar(): void {
    if (this.eliminando() || this.consultando()) {
      return;
    }
    const elemento = this.elemento();
    const nombre = this.nombre();
    this.eliminando.set(true);
    this.error.set(null);
    this.solicitud(elemento).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.eliminado.emit(`Se eliminó ${NOMBRE_TIPO[elemento.tipo]} «${nombre}».`);
      },
      error: (fallo: FalloHttp) => {
        this.eliminando.set(false);
        this.error.set(fallo.detalle ?? fallo.titulo);
      },
    });
  }

  private solicitud(elemento: ElementoEliminable): Observable<void> {
    const procesoId = this.procesoId();
    switch (elemento.tipo) {
      case 'ACTIVIDAD':
        return this.actividades.eliminar(procesoId, elemento.id);
      case 'GATEWAY':
        return this.gateways.eliminar(procesoId, elemento.id);
      case 'ARCO':
        return this.arcos.eliminar(procesoId, elemento.id);
      case 'MESSAGE_THROW':
        return this.messageThrows.eliminar(procesoId, elemento.id);
      case 'MESSAGE_CATCH':
        return this.messageCatches.eliminar(procesoId, elemento.id);
      case 'ENVIO_EXTERNO':
        return this.envios.eliminar(procesoId, elemento.id);
    }
  }

  private consultarVistaPrevia(
    procesoId: number,
    elemento: ElementoEliminable,
  ): Observable<EstadoCarga<{ advertencias: string[] }> | null> {
    return tieneVistaPrevia(elemento)
      ? this.previsualizar(procesoId, elemento).pipe(conEstadoCarga())
      : of(null);
  }

  private previsualizar(procesoId: number, elemento: ConVistaPrevia): Observable<{ advertencias: string[] }> {
    switch (elemento.tipo) {
      case 'GATEWAY':
        return this.gateways.previsualizarEliminacion(procesoId, elemento.id);
      case 'MESSAGE_THROW':
        return this.messageThrows.previsualizarEliminacion(procesoId, elemento.id);
      case 'MESSAGE_CATCH':
        return this.messageCatches.previsualizarEliminacion(procesoId, elemento.id);
      case 'ENVIO_EXTERNO':
        return this.envios.previsualizarEliminacion(procesoId, elemento.id);
    }
  }
}

function nombreDeMensaje(diagrama: DiagramaProceso, elemento: ElementoEliminable): string | null {
  switch (elemento.tipo) {
    case 'MESSAGE_THROW':
      return diagrama.messageThrows.find((evento) => evento.id === elemento.id)?.nombreMensaje ?? null;
    case 'MESSAGE_CATCH':
      return diagrama.messageCatches.find((evento) => evento.id === elemento.id)?.nombreMensaje ?? null;
    case 'ENVIO_EXTERNO':
      return diagrama.enviosExternos.find((evento) => evento.id === elemento.id)?.nombreMensaje ?? null;
    default:
      return null;
  }
}

function tieneVistaPrevia(elemento: ElementoEliminable): elemento is ConVistaPrevia {
  return elemento.tipo !== 'ACTIVIDAD' && elemento.tipo !== 'ARCO';
}
