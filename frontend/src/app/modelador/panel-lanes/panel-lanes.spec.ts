import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto } from '../../../testing/diagramas';
import { DiagramaProceso, LaneRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { CrearLane, ReordenarLanes } from '../../core/modelos/lane';
import { RecolocarNodos } from '../../core/modelos/movimiento';
import { RolProceso } from '../../core/modelos/rol-proceso';
import { LaneService } from '../../core/servicios/lane-service';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { PanelLanes, PermisosLanes } from './panel-lanes';

const ROLES: RolProceso[] = [
  { id: 21, nombre: 'Supervisor', descripcion: 'Aprueba', activo: true },
  { id: 22, nombre: 'Analista', descripcion: 'Evalúa', activo: true },
  { id: 23, nombre: 'Auditor', descripcion: 'Revisa', activo: true },
];

const TODOS: PermisosLanes = { crearLane: true, editarLane: true, eliminarLane: true };

function conflicto(codigo: string, detalle: string): FalloHttp {
  return { tipo: 'conflicto', estado: 409, codigo, titulo: 'La operación entra en conflicto con los datos actuales', detalle };
}

describe('PanelLanes', () => {
  let fixture: ComponentFixture<PanelLanes>;
  let elemento: HTMLElement;
  let cambios: number;
  const rolesDisponibles = vi.fn<(procesoId: number, poolId: number) => Observable<RolProceso[]>>();
  const crearLane = vi.fn<(procesoId: number, poolId: number, datos: CrearLane) => Observable<LaneRespuesta>>();
  const editar = vi.fn<(procesoId: number, poolId: number, laneId: number, datos: CrearLane) => Observable<LaneRespuesta>>();
  const reordenar = vi.fn<(procesoId: number, poolId: number, datos: ReordenarLanes) => Observable<LaneRespuesta[]>>();
  const eliminar = vi.fn<(procesoId: number, poolId: number, laneId: number) => Observable<void>>();
  const recolocarNodos = vi.fn<(procesoId: number, datos: RecolocarNodos) => Observable<void>>();

  async function crear(permisos: PermisosLanes = TODOS, diagrama: DiagramaProceso = diagramaCompleto()): Promise<void> {
    [rolesDisponibles, crearLane, editar, reordenar, eliminar, recolocarNodos].forEach((simulado) => simulado.mockReset());
    rolesDisponibles.mockReturnValue(of(ROLES));
    recolocarNodos.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: LaneService, useValue: { rolesDisponibles, crear: crearLane, editar, reordenar, eliminar } },
        { provide: SesionService, useValue: { tieneRol: () => true } },
        { provide: ProcesoService, useValue: { recolocarNodos } },
      ],
    });
    fixture = TestBed.createComponent(PanelLanes);
    elemento = fixture.nativeElement;
    cambios = 0;
    fixture.componentInstance.cambio.subscribe(() => cambios++);
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('permisos', permisos);
    await fixture.whenStable();
  }

  function boton(etiqueta: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.getAttribute('aria-label') === etiqueta || candidato.textContent?.trim() === etiqueta,
    );
  }

  async function elegir(selector: string, indice: number): Promise<void> {
    const lista = elemento.querySelector<HTMLSelectElement>(selector)!;
    lista.selectedIndex = indice;
    lista.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function enviar(selector: string): Promise<void> {
    elemento.querySelector(selector)!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function opciones(selector: string): string[] {
    return Array.from(elemento.querySelectorAll(`${selector} option`)).map((opcion) => opcion.textContent?.trim() ?? '');
  }

  it('lista las lanes del pool propietario en su orden con sus actividades reales', async () => {
    await crear();

    expect(opciones('#lanes-pool')).toEqual(['Empresa de prueba', 'Cliente']);
    const lanes = Array.from(elemento.querySelectorAll('.panel-lanes__lane')).map((lane) => lane.textContent?.replace(/\s+/g, ' ').trim());
    expect(lanes[0]).toContain('1. General Lane inicial sin rol de proceso · 2 actividades');
    expect(lanes[1]).toContain('2. Supervisor Rol de proceso asignado · 1 actividad');
    expect(rolesDisponibles).toHaveBeenCalledWith(5, 80);
  });

  it('crea una lane con un rol libre y avisa al modelador para recargar el diagrama', async () => {
    await crear();
    crearLane.mockReturnValue(of({ id: 13, nombre: 'Analista', rolProcesoId: 22, poolId: 80, orden: 3 }));

    expect(opciones('#nueva-lane-rol')).toEqual(['Elige un rol', 'Analista', 'Auditor']);
    await elegir('#nueva-lane-rol', 1);
    await enviar('.panel-lanes__creacion');

    expect(crearLane).toHaveBeenCalledWith(5, 80, { rolProcesoId: 22, orden: null });
    expect(cambios).toBe(1);
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('La lane se creó');
  });

  it('exige el rol antes de crear y muestra en su campo la lane duplicada', async () => {
    await crear();
    await enviar('.panel-lanes__creacion');
    expect(crearLane).not.toHaveBeenCalled();
    expect(elemento.querySelector('.panel-lanes__creacion .campo__error')?.textContent).toBe('Elige un rol de proceso');

    crearLane.mockReturnValue(throwError(() => conflicto('LANE_DUPLICADA', 'El pool ya tiene una lane activa para ese rol de proceso')));
    await elegir('#nueva-lane-rol', 2);
    await enviar('.panel-lanes__creacion');

    expect(elemento.querySelector('.panel-lanes__creacion .campo__error')?.textContent).toBe(
      'El pool ya tiene una lane activa para ese rol de proceso',
    );
    expect(cambios).toBe(0);
  });

  it('asigna un rol de proceso a la lane inicial', async () => {
    await crear();
    editar.mockReturnValue(of({ id: 11, nombre: 'Auditor', rolProcesoId: 23, poolId: 80, orden: 1 }));

    boton('Cambiar el rol de la lane General')!.click();
    await fixture.whenStable();
    expect(opciones('#lane-rol-11')).toEqual(['Elige un rol', 'Analista', 'Auditor']);
    await elegir('#lane-rol-11', 2);
    await enviar('.panel-lanes__edicion');

    expect(editar).toHaveBeenCalledWith(5, 80, 11, { rolProcesoId: 23, orden: null });
    expect(cambios).toBe(1);
    expect(elemento.querySelector('.panel-lanes__edicion')).toBeNull();
  });

  it('reordena enviando todas las lanes del pool en el nuevo orden', async () => {
    await crear();
    reordenar.mockReturnValue(of([]));

    expect(boton('Subir la lane General')?.disabled).toBe(true);
    expect(boton('Bajar la lane Supervisor')?.disabled).toBe(true);
    boton('Bajar la lane General')!.click();
    await fixture.whenStable();

    expect(reordenar).toHaveBeenCalledWith(5, 80, { lanes: [12, 11] });
    expect(cambios).toBe(1);
  });

  it('no permite eliminar lanes con actividades y elimina las vacías tras confirmar', async () => {
    const diagrama = diagramaCompleto();
    diagrama.actividades = diagrama.actividades.filter((actividad) => actividad.laneId !== 12);
    await crear(TODOS, diagrama);
    eliminar.mockReturnValue(of(undefined));

    expect(boton('Eliminar la lane General')?.disabled).toBe(true);
    boton('Eliminar la lane Supervisor')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')?.textContent).toContain('«Supervisor»');
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(eliminar).toHaveBeenCalledWith(5, 80, 12);
    expect(cambios).toBe(1);
    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('muestra en el diálogo el rechazo del backend al eliminar', async () => {
    const diagrama = diagramaCompleto();
    diagrama.actividades = [];
    await crear(TODOS, diagrama);
    eliminar.mockReturnValue(throwError(() => conflicto('LANE_CON_ACTIVIDADES', "La lane 'Supervisor' tiene 1 actividades activas")));

    boton('Eliminar la lane Supervisor')!.click();
    await fixture.whenStable();
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alertdialog"] [role="alert"]')?.textContent).toContain('tiene 1 actividades activas');
    expect(cambios).toBe(0);
  });

  it('respeta los permisos de estructura del rol y no consulta roles sin necesidad', async () => {
    await crear({ crearLane: false, editarLane: false, eliminarLane: false });

    expect(rolesDisponibles).not.toHaveBeenCalled();
    expect(elemento.querySelector('.panel-lanes__creacion')).toBeNull();
    expect(elemento.querySelector('.panel-lanes__acciones')).toBeNull();
    expect(elemento.textContent).toContain('Tu rol no tiene permisos de estructura');
  });

  it('permite solo las operaciones concedidas al editor', async () => {
    await crear({ crearLane: true, editarLane: true, eliminarLane: false });

    expect(boton('Eliminar la lane Supervisor')).toBeUndefined();
    expect(boton('Cambiar el rol de la lane Supervisor')).toBeDefined();
    expect(elemento.querySelector('.panel-lanes__creacion')).not.toBeNull();
  });

  it('cambia de pool, consulta sus roles y conserva la elección al recargar el diagrama', async () => {
    await crear();

    await elegir('#lanes-pool', 1);
    expect(rolesDisponibles).toHaveBeenLastCalledWith(5, 81);
    expect(elemento.textContent).toContain('Este pool todavía no tiene lanes');

    fixture.componentRef.setInput('diagrama', diagramaCompleto());
    await fixture.whenStable();

    expect(elemento.textContent).toContain('Este pool todavía no tiene lanes');
  });

  it('muestra el rechazo 403 del backend como error general', async () => {
    await crear();
    reordenar.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'sin-permiso',
        estado: 403,
        codigo: 'USUARIO_SIN_PERMISO',
        titulo: 'No tienes permiso para esta consulta',
        detalle: 'El rol EDITOR no tiene permiso para editar lanes en este proceso',
      })),
    );

    let rechazos = 0;
    fixture.componentInstance.rechazo.subscribe(() => rechazos++);

    boton('Bajar la lane General')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('no tiene permiso para editar lanes');
    expect(cambios).toBe(0);
    expect(rechazos).toBe(1);
  });

  it('al reordenar recoloca en la misma secuencia los elementos para que acompañen a su lane', async () => {
    await crear();
    reordenar.mockReturnValue(of([]));

    boton('Bajar la lane General')!.click();
    await fixture.whenStable();

    expect(reordenar).toHaveBeenCalledWith(5, 80, { lanes: [12, 11] });
    expect(recolocarNodos).toHaveBeenCalledTimes(1);
    expect(recolocarNodos.mock.calls[0][1].nodos).toContainEqual({ tipo: 'ACTIVIDAD', id: 3, posicionX: 620, posicionY: 60 });
    expect(recolocarNodos.mock.calls[0][1].nodos).toContainEqual({ tipo: 'ACTIVIDAD', id: 1, posicionX: 160, posicionY: 200 });
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain(
      'Se recolocaron 6 elementos del diagrama para conservar su lane.',
    );
    expect(cambios).toBe(1);
  });

  it('al crear una lane intermedia recoloca las lanes inferiores y los pools de debajo', async () => {
    await crear();
    crearLane.mockReturnValue(of({ id: 13, nombre: 'Analista', rolProcesoId: 22, poolId: 80, orden: 1 }));

    await elegir('#nueva-lane-rol', 1);
    const orden = elemento.querySelector<HTMLInputElement>('#nueva-lane-orden')!;
    orden.value = '1';
    orden.dispatchEvent(new Event('input'));
    await enviar('.panel-lanes__creacion');

    expect(crearLane).toHaveBeenCalledWith(5, 80, { rolProcesoId: 22, orden: 1 });
    expect(recolocarNodos.mock.calls[0][1].nodos).toContainEqual({ tipo: 'EVENTO', id: 6, posicionX: 60, posicionY: 520 });
    expect(recolocarNodos.mock.calls[0][1].nodos).toContainEqual({ tipo: 'ACTIVIDAD', id: 3, posicionX: 620, posicionY: 340 });
  });

  it('no envía recolocación si la estructura no desplaza ningún elemento', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools = diagrama.pools.slice(0, 1);
    diagrama.messageThrows = [];
    diagrama.enviosExternos = [];
    diagrama.flujosMensaje = [];
    diagrama.arcos = diagrama.arcos.filter((arco) => arco.destinoTipo !== 'EVENTO');
    await crear(TODOS, diagrama);
    crearLane.mockReturnValue(of({ id: 13, nombre: 'Analista', rolProcesoId: 22, poolId: 80, orden: 3 }));

    await elegir('#nueva-lane-rol', 1);
    await enviar('.panel-lanes__creacion');

    expect(crearLane).toHaveBeenCalled();
    expect(recolocarNodos).not.toHaveBeenCalled();
    expect(elemento.querySelector('[role="status"]')?.textContent?.trim()).toBe(
      'La lane se creó y el diagrama se actualizó con los datos del servidor.',
    );
  });

  it('si la recolocación falla conserva el cambio de lanes, lo explica y no anuncia una recolocación', async () => {
    await crear();
    reordenar.mockReturnValue(of([]));
    recolocarNodos.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'no-encontrado',
        estado: 404,
        codigo: 'RECURSO_NO_ENCONTRADO',
        titulo: 'No encontramos el recurso solicitado',
        detalle: 'El gateway #4 no existe en este proceso o ya fue eliminado',
      })),
    );

    boton('Bajar la lane General')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="status"]')?.textContent?.trim()).toBe('La lane «General» cambió de posición.');
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('El gateway #4 no existe en este proceso');
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('arrastra los elementos a su lane');
    expect(cambios).toBe(1);
  });

  it('cierra la edición y la confirmación de eliminación si se revocan los permisos', async () => {
    const diagrama = diagramaCompleto();
    diagrama.actividades = diagrama.actividades.filter((actividad) => actividad.laneId !== 12);
    await crear(TODOS, diagrama);

    boton('Cambiar el rol de la lane General')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('.panel-lanes__edicion')).not.toBeNull();
    fixture.componentRef.setInput('permisos', { crearLane: true, editarLane: false, eliminarLane: true });
    await fixture.whenStable();
    expect(elemento.querySelector('.panel-lanes__edicion')).toBeNull();

    boton('Eliminar la lane Supervisor')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')).not.toBeNull();
    fixture.componentRef.setInput('permisos', { crearLane: true, editarLane: false, eliminarLane: false });
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
  });
});
