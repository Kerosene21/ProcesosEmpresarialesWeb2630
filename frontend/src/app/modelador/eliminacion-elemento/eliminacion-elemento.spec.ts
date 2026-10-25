import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto } from '../../../testing/diagramas';
import { GatewayRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { ActividadService } from '../../core/servicios/actividad-service';
import { ArcoService } from '../../core/servicios/arco-service';
import { GatewayService } from '../../core/servicios/gateway-service';
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

  async function crear(seleccion: ElementoEliminable): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        { provide: ActividadService, useValue: { eliminar: eliminarActividad } },
        { provide: GatewayService, useValue: { eliminar: eliminarGateway, previsualizarEliminacion: previsualizar } },
        { provide: ArcoService, useValue: { eliminar: eliminarArco } },
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
    [eliminarActividad, eliminarGateway, eliminarArco, previsualizar].forEach((simulado) => simulado.mockReset());
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
