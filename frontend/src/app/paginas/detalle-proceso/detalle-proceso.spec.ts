import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { DiagramaProceso } from '../../core/modelos/diagrama';
import { Proceso } from '../../core/modelos/proceso';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { DetalleProceso } from './detalle-proceso';

const PROCESO: Proceso = {
  id: 5,
  nombre: 'Atención de solicitudes',
  descripcion: 'Recepción y evaluación de solicitudes',
  categoria: 'Servicio al cliente',
  estado: 'BORRADOR',
  poolId: 80,
  poolNombre: 'Empresa de prueba',
  eliminado: false,
  empresaPropietariaId: 7,
  empresaPropietariaNombre: 'Empresa de prueba',
  soloLectura: false,
};

const DIAGRAMA: DiagramaProceso = {
  procesoId: 5,
  pools: [
    {
      id: 80,
      procesoId: 5,
      nombre: 'Empresa de prueba',
      tipo: 'PROPIETARIO',
      orden: 1,
      cajaNegra: false,
      activo: true,
      empresaId: 7,
      empresaNombre: 'Empresa de prueba',
    },
    {
      id: 81,
      procesoId: 5,
      nombre: 'Servicio de correo',
      tipo: 'EXTERNO',
      orden: 2,
      cajaNegra: true,
      activo: true,
      empresaId: null,
      empresaNombre: null,
    },
  ],
  lanes: [{ id: 11, nombre: 'General', rolProcesoId: null, poolId: 80, orden: 1 }],
  actividades: [1, 2].map((id) => ({
    id,
    nombre: `Actividad ${id}`,
    tipo: 'TAREA_USUARIO' as const,
    procesoId: 5,
    poolId: 80,
    laneId: 11,
    laneNombre: 'General',
    posicionX: 100 * id,
    posicionY: 60,
    activo: true,
    arcosDesactivados: 0,
    advertencias: [],
  })),
  gateways: [],
  arcos: [],
  messageThrows: [],
  messageCatches: [],
  enviosExternos: [],
  flujosMensaje: [],
};

describe('DetalleProceso', () => {
  let fixture: ComponentFixture<DetalleProceso>;
  let elemento: HTMLElement;
  let obtener: ReturnType<typeof vi.fn<(id: number) => Observable<Proceso>>>;
  let obtenerDiagrama: ReturnType<typeof vi.fn<(id: number) => Observable<DiagramaProceso>>>;

  async function crear(id: string, proceso: Observable<Proceso>, diagrama = of(DIAGRAMA)): Promise<void> {
    obtener = vi.fn<(id: number) => Observable<Proceso>>().mockReturnValue(proceso);
    obtenerDiagrama = vi.fn<(id: number) => Observable<DiagramaProceso>>().mockReturnValue(diagrama);
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProcesoService, useValue: { obtener, obtenerDiagrama } }],
    });
    fixture = TestBed.createComponent(DetalleProceso);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('id', id);
    await fixture.whenStable();
  }

  function metrica(etiqueta: string): string | undefined {
    return Array.from(elemento.querySelectorAll('.metrica'))
      .find((item) => item.querySelector('.metrica__etiqueta')?.textContent === etiqueta)
      ?.querySelector('.metrica__valor')?.textContent?.trim();
  }

  it('muestra los datos del DTO y el resumen del diagrama', async () => {
    await crear('5', of(PROCESO));

    expect(obtener).toHaveBeenCalledWith(5);
    expect(obtenerDiagrama).toHaveBeenCalledWith(5);
    expect(elemento.querySelector('h1')?.textContent).toBe('Atención de solicitudes');
    expect(elemento.querySelector('.lista-datos')?.textContent).toContain('Servicio al cliente');
    expect(metrica('Pools')).toBe('2');
    expect(metrica('Actividades')).toBe('2');
    expect(elemento.querySelectorAll('.pool').length).toBe(2);
    expect(elemento.textContent).toContain('Caja negra');
    expect(elemento.querySelector('.lane')?.textContent).toContain('2 actividades');
    expect(elemento.querySelectorAll('tbody tr').length).toBe(2);
    const editar = Array.from(elemento.querySelectorAll('a')).find((enlace) => enlace.textContent === 'Editar proceso');
    expect(editar?.getAttribute('href')).toBe('/procesos/5/editar');
  });

  it('no ofrece edición en un proceso compartido en solo lectura', async () => {
    await crear('5', of({ ...PROCESO, soloLectura: true }));

    expect(elemento.textContent).toContain('Solo lectura');
    expect(elemento.textContent).not.toContain('Editar proceso');
  });

  it('rechaza un identificador no numérico sin llamar al backend', async () => {
    await crear('abc', of(PROCESO));

    expect(obtener).not.toHaveBeenCalled();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('no es válido');
  });

  it('muestra el error del backend en lugar del contenido', async () => {
    await crear(
      '9',
      throwError(() => ({
        tipo: 'no-encontrado',
        estado: 404,
        codigo: 'RECURSO_NO_ENCONTRADO',
        titulo: 'El recurso solicitado no existe',
        detalle: 'El proceso no existe',
      })),
    );

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('El recurso solicitado no existe');
    expect(elemento.querySelector('.metricas')).toBeNull();
  });
});
