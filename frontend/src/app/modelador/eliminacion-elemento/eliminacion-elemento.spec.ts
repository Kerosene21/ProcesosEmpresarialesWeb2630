import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto } from '../../../testing/diagramas';
import { GatewayRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { ActividadService } from '../../core/servicios/actividad-service';
import { ArcoService } from '../../core/servicios/arco-service';
import { EnvioExternoService } from '../../core/servicios/envio-externo-service';
import { GatewayService } from '../../core/servicios/gateway-service';
import { MessageCatchService } from '../../core/servicios/message-catch-service';
import { MessageThrowService } from '../../core/servicios/message-throw-service';
import { calcularGeometria } from '../geometria';
import { ElementoEliminable, EliminacionElemento } from './eliminacion-elemento';

const SIN_PERMISO: FalloHttp = {
  tipo: 'sin-permiso',
  estado: 403,
  codigo: 'USUARIO_SIN_PERMISO',
  titulo: 'No tienes permiso para esta consulta',
  detalle: 'Solo un administrador puede eliminar gateways',
};

describe('EliminacionElemento', () => {
  let fixture: ComponentFixture<EliminacionElemento>;
  let elemento: HTMLElement;
  let eliminados: string[];
  let cancelaciones: number;
  const eliminarActividad = vi.fn<(procesoId: number, id: number) => Observable<void>>();
  const eliminarGateway = vi.fn<(procesoId: number, id: number) => Observable<void>>();
  const eliminarArco = vi.fn<(procesoId: number, id: number) => Observable<void>>();
  const previsualizar = vi.fn<(procesoId: number, id: number) => Observable<GatewayRespuesta>>();
  const eliminarThrow = vi.fn<(procesoId: number, id: number) => Observable<void>>();
  const previsualizarThrow = vi.fn<(procesoId: number, id: number) => Observable<{ advertencias: string[] }>>();
  const eliminarCatch = vi.fn<(procesoId: number, id: number) => Observable<void>>();
  const previsualizarCatch = vi.fn<(procesoId: number, id: number) => Observable<{ advertencias: string[] }>>();
  const eliminarEnvio = vi.fn<(procesoId: number, id: number) => Observable<void>>();
  const previsualizarEnvio = vi.fn<(procesoId: number, id: number) => Observable<{ advertencias: string[] }>>();

  async function crear(seleccion: ElementoEliminable): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActividadService, useValue: { eliminar: eliminarActividad } },
        { provide: GatewayService, useValue: { eliminar: eliminarGateway, previsualizarEliminacion: previsualizar } },
        { provide: ArcoService, useValue: { eliminar: eliminarArco } },
        { provide: MessageThrowService, useValue: { eliminar: eliminarThrow, previsualizarEliminacion: previsualizarThrow } },
        { provide: MessageCatchService, useValue: { eliminar: eliminarCatch, previsualizarEliminacion: previsualizarCatch } },
        { provide: EnvioExternoService, useValue: { eliminar: eliminarEnvio, previsualizarEliminacion: previsualizarEnvio } },
      ],
    });
    fixture = TestBed.createComponent(EliminacionElemento);
    elemento = fixture.nativeElement;
    eliminados = [];
    cancelaciones = 0;
    fixture.componentInstance.eliminado.subscribe((texto) => eliminados.push(texto));
    fixture.componentInstance.cancelado.subscribe(() => cancelaciones++);
    const diagrama = diagramaCompleto();
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('elemento', seleccion);
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll('button')).find((actual) => actual.textContent?.trim() === texto)!;
  }

  function consecuencias(): string[] {
    return Array.from(elemento.querySelectorAll('.eliminacion__consecuencias li')).map((item) => item.textContent?.trim() ?? '');
  }

  beforeEach(() => {
    [
      eliminarActividad,
      eliminarGateway,
      eliminarArco,
      previsualizar,
      eliminarThrow,
      previsualizarThrow,
      eliminarCatch,
      previsualizarCatch,
      eliminarEnvio,
      previsualizarEnvio,
    ].forEach((simulado) => simulado.mockReset());
  });

  it('informa los arcos que se desactivarán con la actividad y la elimina al confirmar', async () => {
    eliminarActividad.mockReturnValue(of(undefined));
    await crear({ tipo: 'ACTIVIDAD', id: 2 });

    expect(elemento.querySelector('[role="alertdialog"] h2')?.textContent).toBe('¿Eliminar la actividad?');
    expect(consecuencias()).toEqual([
      'Se desactivarán los 2 arcos conectados:',
      'Registrar solicitud → Evaluar solicitud',
      'Evaluar solicitud → Gateway EXCLUSIVO #4',
    ]);
    boton('Eliminar').click();
    await fixture.whenStable();

    expect(eliminarActividad).toHaveBeenCalledWith(5, 2);
    expect(eliminados).toEqual(['Se eliminó la actividad «Evaluar solicitud».']);
  });

  it('usa la vista previa del servidor antes de eliminar un gateway', async () => {
    const advertencias = ['Se desactivarán 3 arcos conectados a Gateway EXCLUSIVO #4.', 'La ramificación queda sin punto de decisión.'];
    previsualizar.mockReturnValue(of({ ...diagramaCompleto().gateways[0], advertencias }));
    eliminarGateway.mockReturnValue(of(undefined));
    await crear({ tipo: 'GATEWAY', id: 4 });

    expect(previsualizar).toHaveBeenCalledWith(5, 4);
    expect(consecuencias()).toEqual(advertencias);
    boton('Eliminar').click();
    await fixture.whenStable();

    expect(eliminarGateway).toHaveBeenCalledWith(5, 4);
    expect(eliminados).toEqual(['Se eliminó el gateway «Gateway EXCLUSIVO #4».']);
  });

  it('avisa si no puede consultar la vista previa y muestra el rechazo de la eliminación', async () => {
    previsualizar.mockReturnValue(throwError(() => SIN_PERMISO));
    eliminarGateway.mockReturnValue(throwError(() => SIN_PERMISO));
    await crear({ tipo: 'GATEWAY', id: 4 });

    expect(elemento.textContent).toContain('No se pudieron consultar las consecuencias: Solo un administrador puede eliminar gateways');
    boton('Eliminar').click();
    await fixture.whenStable();

    expect(elemento.querySelector('.dialogo__error')?.textContent).toContain('Solo un administrador puede eliminar gateways');
    expect(eliminados).toEqual([]);
  });

  it('muestra los arcos y los homólogos afectados antes de eliminar un Message Throw', async () => {
    const advertencias = [
      'Se desactivarán 0 arcos conectados.',
      "El Message Catch 'Solicitud de servicio' del pool 'Empresa de prueba' quedará sin Message Throw homólogo.",
    ];
    previsualizarThrow.mockReturnValue(of({ advertencias }));
    eliminarThrow.mockReturnValue(of(undefined));
    await crear({ tipo: 'MESSAGE_THROW', id: 6 });

    expect(previsualizarThrow).toHaveBeenCalledWith(5, 6);
    expect(elemento.querySelector('[role="alertdialog"] h2')?.textContent).toBe('¿Eliminar el Message Throw?');
    expect(consecuencias()).toEqual(advertencias);
    boton('Eliminar').click();
    await fixture.whenStable();

    expect(eliminarThrow).toHaveBeenCalledWith(5, 6);
    expect(eliminados).toEqual(['Se eliminó el Message Throw «Solicitud de servicio».']);
  });

  it('elimina un Message Catch y un envío externo con su propio endpoint', async () => {
    previsualizarCatch.mockReturnValue(of({ advertencias: [] }));
    eliminarCatch.mockReturnValue(of(undefined));
    await crear({ tipo: 'MESSAGE_CATCH', id: 5 });
    expect(consecuencias()).toEqual(['El servidor no informa consecuencias adicionales.']);
    boton('Eliminar').click();
    await fixture.whenStable();
    expect(eliminarCatch).toHaveBeenCalledWith(5, 5);

    TestBed.resetTestingModule();
    previsualizarEnvio.mockReturnValue(of({ advertencias: ['Se desactivarán 2 arcos conectados.'] }));
    eliminarEnvio.mockReturnValue(throwError(() => ({ ...SIN_PERMISO, detalle: 'Solo un administrador puede eliminar envíos externos' })));
    await crear({ tipo: 'ENVIO_EXTERNO', id: 7 });
    expect(elemento.querySelector('[role="alertdialog"] h2')?.textContent).toBe('¿Eliminar el envío externo?');
    boton('Eliminar').click();
    await fixture.whenStable();

    expect(eliminarEnvio).toHaveBeenCalledWith(5, 7);
    expect(elemento.querySelector('.dialogo__error')?.textContent).toContain('Solo un administrador puede eliminar envíos externos');
    expect(eliminados).toEqual([]);
  });

  it('elimina un arco advirtiendo la condición que se pierde y permite cancelar', async () => {
    eliminarArco.mockReturnValue(of(undefined));
    await crear({ tipo: 'ARCO', id: 34 });

    expect(elemento.textContent).toContain('Se eliminará «Gateway EXCLUSIVO #4 → Aprobar solicitud» del modelo');
    expect(consecuencias()).toEqual(['Se perderá la condición «Aprobada».']);
    boton('Cancelar').click();

    expect(cancelaciones).toBe(1);
    expect(eliminarArco).not.toHaveBeenCalled();
  });
});
