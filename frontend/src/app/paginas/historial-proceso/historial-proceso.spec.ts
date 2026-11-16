import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Historial } from '../../core/modelos/historial';
import { Proceso } from '../../core/modelos/proceso';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { HistorialProceso } from './historial-proceso';

const PROCESO: Proceso = {
  id: 5,
  nombre: 'Atención de solicitudes',
  descripcion: 'Recepción de solicitudes',
  categoria: 'Servicio',
  estado: 'PUBLICADO',
  poolId: 80,
  poolNombre: 'Empresa de prueba',
  eliminado: false,
  empresaPropietariaId: 7,
  empresaPropietariaNombre: 'Empresa de prueba',
  soloLectura: false,
};

const HISTORIAL: Historial[] = [
  {
    fecha: '2026-10-18T10:15:30',
    usuarioCorreo: 'admin@alpes.com',
    estadoAnterior: 'BORRADOR',
    cambiosRealizados: 'estado: BORRADOR -> PUBLICADO',
  },
  {
    fecha: '2026-10-17T08:05:00',
    usuarioCorreo: 'editor@alpes.com',
    estadoAnterior: null,
    cambiosRealizados: 'proceso creado',
  },
];

describe('HistorialProceso', () => {
  let fixture: ComponentFixture<HistorialProceso>;
  let elemento: HTMLElement;
  const obtener = vi.fn<(id: number) => Observable<Proceso>>();
  const historial = vi.fn<(id: number) => Observable<Historial[]>>();

  async function crear(id: string, entradas: Observable<Historial[]>): Promise<void> {
    obtener.mockReset().mockReturnValue(of(PROCESO));
    historial.mockReset().mockReturnValue(entradas);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProcesoService, useValue: { obtener, historial } }],
    });
    fixture = TestBed.createComponent(HistorialProceso);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('id', id);
    await fixture.whenStable();
  }

  function celdas(fila: number): string[] {
    return Array.from(elemento.querySelectorAll('tbody tr')[fila].querySelectorAll('td')).map(
      (celda) => celda.textContent?.trim() ?? '',
    );
  }

  it('muestra cada cambio con su fecha, usuario, estado anterior y descripción reales', async () => {
    await crear('5', of(HISTORIAL));

    expect(historial).toHaveBeenCalledWith(5);
    expect(elemento.querySelector('h1')?.textContent).toBe('Atención de solicitudes');
    expect(celdas(0)).toEqual(['18/10/2026 10:15', 'admin@alpes.com', 'Borrador', 'estado: BORRADOR -> PUBLICADO']);
    expect(celdas(1)).toEqual(['17/10/2026 08:05', 'editor@alpes.com', 'Sin estado previo', 'proceso creado']);
    expect(elemento.querySelector('time')?.getAttribute('datetime')).toBe('2026-10-18T10:15:30');
  });

  it('indica cuando no hay cambios registrados', async () => {
    await crear('5', of([]));

    expect(elemento.textContent).toContain('El proceso todavía no tiene cambios registrados');
    expect(elemento.querySelector('table')).toBeNull();
  });

  it('muestra el rechazo del backend para procesos de otra empresa y permite reintentar', async () => {
    await crear(
      '5',
      throwError(() => ({
        tipo: 'sin-permiso',
        estado: 403,
        codigo: 'USUARIO_SIN_PERMISO',
        titulo: 'No tienes permiso para esta consulta',
        detalle: 'El proceso no pertenece a tu empresa',
      })),
    );

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso para esta consulta');
    historial.mockReturnValue(of(HISTORIAL));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(elemento.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('rechaza un identificador no numérico sin llamar al backend', async () => {
    await crear('abc', of(HISTORIAL));

    expect(historial).not.toHaveBeenCalled();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('no es válido');
  });
});
