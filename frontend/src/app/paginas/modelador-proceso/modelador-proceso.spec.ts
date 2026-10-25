import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { PROCESO_PRUEBA, actividad, diagramaCompleto } from '../../../testing/diagramas';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { PermisoEstructura } from '../../core/modelos/permiso-estructura';
import { Proceso } from '../../core/modelos/proceso';
import { RolProceso } from '../../core/modelos/rol-proceso';
import { RolUsuario } from '../../core/modelos/sesion';
import { ActividadService } from '../../core/servicios/actividad-service';
import { LaneService } from '../../core/servicios/lane-service';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { ModeladorProceso } from './modelador-proceso';

const MATRIZ: PermisoEstructura[] = [
  { rol: 'ADMINISTRADOR', configurable: false, crearPool: true, editarPool: true, eliminarPool: true, crearLane: true, editarLane: true, eliminarLane: true },
  { rol: 'EDITOR', configurable: true, crearPool: false, editarPool: true, eliminarPool: false, crearLane: true, editarLane: true, eliminarLane: false },
  { rol: 'SOLO_LECTURA', configurable: false, crearPool: false, editarPool: false, eliminarPool: false, crearLane: false, editarLane: false, eliminarLane: false },
];

const FALLO_SERVIDOR: FalloHttp = {
  tipo: 'servidor',
  estado: 500,
  codigo: null,
  titulo: 'El servidor no pudo atender la solicitud',
  detalle: null,
};

describe('ModeladorProceso', () => {
  let fixture: ComponentFixture<ModeladorProceso>;
  let elemento: HTMLElement;
  const obtener = vi.fn<(id: number) => Observable<Proceso>>();
  const obtenerDiagrama = vi.fn<(id: number) => Observable<DiagramaProceso>>();
  const permisosEstructura = vi.fn<(id: number) => Observable<PermisoEstructura[]>>();
  const reordenar = vi.fn<() => Observable<unknown>>();
  const crearActividad = vi.fn();

  async function crear(proceso: Proceso = PROCESO_PRUEBA, rol: RolUsuario = 'EDITOR', permisos = of(MATRIZ)): Promise<void> {
    obtener.mockReset().mockReturnValue(of(proceso));
    obtenerDiagrama.mockReset().mockReturnValue(of(diagramaCompleto()));
    permisosEstructura.mockReset().mockReturnValue(permisos);
    reordenar.mockReset().mockReturnValue(of([]));
    crearActividad.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ProcesoService, useValue: { obtener, obtenerDiagrama, permisosEstructura } },
        { provide: LaneService, useValue: { rolesDisponibles: () => of<RolProceso[]>([]), reordenar } },
        { provide: ActividadService, useValue: { crear: crearActividad } },
        { provide: SesionService, useValue: { rol: signal(rol), tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol) } },
      ],
    });
    fixture = TestBed.createComponent(ModeladorProceso);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('id', '5');
    await fixture.whenStable();
  }

  function boton(etiqueta: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.getAttribute('aria-label') === etiqueta,
    );
  }

  function botonConTexto(texto: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.textContent?.trim() === texto,
    );
  }

  async function seleccionarEnLista(texto: string): Promise<void> {
    Array.from(elemento.querySelectorAll<HTMLButtonElement>('.modelador__elemento'))
      .find((candidato) => candidato.textContent?.trim() === texto)!
      .click();
    await fixture.whenStable();
  }

  it('carga el proceso, el diagrama y los permisos de estructura del rol de la sesión', async () => {
    await crear();

    expect(obtener).toHaveBeenCalledWith(5);
    expect(obtenerDiagrama).toHaveBeenCalledWith(5);
    expect(permisosEstructura).toHaveBeenCalledWith(5);
    expect(elemento.querySelector('h1')?.textContent).toBe('Atención de solicitudes');
    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(3);
    expect(boton('Cambiar el rol de la lane General')).toBeDefined();
    expect(boton('Eliminar la lane General')).toBeUndefined();
    const volver = Array.from(elemento.querySelectorAll('a')).find((enlace) => enlace.textContent === 'Volver al proceso');
    expect(volver?.getAttribute('href')).toBe('/procesos/5');
  });

  it('recarga solo el diagrama tras un cambio de lanes y muestra los datos persistidos', async () => {
    await crear();
    const actualizado = diagramaCompleto();
    actualizado.actividades.push(actividad(8, 'Archivar solicitud', 12, 820, 200));
    obtenerDiagrama.mockReturnValue(of(actualizado));

    boton('Bajar la lane General')!.click();
    await fixture.whenStable();

    expect(reordenar).toHaveBeenCalled();
    expect(obtenerDiagrama).toHaveBeenCalledTimes(2);
    expect(obtener).toHaveBeenCalledTimes(1);
    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(4);
  });

  it('conserva el diagrama anterior si la recarga falla y permite reintentarla', async () => {
    await crear();
    obtenerDiagrama.mockReturnValue(throwError(() => FALLO_SERVIDOR));

    boton('Bajar la lane General')!.click();
    await fixture.whenStable();

    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(3);
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No se pudo actualizar el diagrama');
    obtenerDiagrama.mockReturnValue(of(diagramaCompleto()));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(elemento.textContent).not.toContain('No se pudo actualizar el diagrama');
  });

  it('en un proceso compartido en solo lectura no ofrece cambios ni consulta permisos', async () => {
    await crear({ ...PROCESO_PRUEBA, soloLectura: true });

    expect(elemento.textContent).toContain('Este proceso solo se puede consultar');
    expect(elemento.textContent).toContain('Pertenece a otra empresa');
    expect(permisosEstructura).not.toHaveBeenCalled();
    expect(elemento.querySelector('app-panel-lanes')).toBeNull();
    expect(elemento.querySelector('[role="toolbar"]')).toBeNull();
    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(3);
  });

  it('en un proceso eliminado tampoco permite modificar el modelo', async () => {
    await crear({ ...PROCESO_PRUEBA, eliminado: true });

    expect(elemento.textContent).toContain('El proceso fue eliminado');
    expect(elemento.querySelector('app-panel-lanes')).toBeNull();
    expect(elemento.querySelector('[role="toolbar"]')).toBeNull();
    await seleccionarEnLista('Evaluar solicitud');
    expect(botonConTexto('Editar')).toBeUndefined();
  });

  it('si no puede consultar los permisos lo informa y no habilita operaciones', async () => {
    await crear(PROCESO_PRUEBA, 'ADMINISTRADOR', throwError(() => FALLO_SERVIDOR));

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No se pudieron consultar los permisos de estructura');
    expect(boton('Bajar la lane General')).toBeUndefined();
  });

  it('habilita la edición al editor y recarga el diagrama con lo que persiste el servidor', async () => {
    await crear();
    const creada = actividad(9, 'Archivar solicitud', 11, 896, 47);
    const actualizado = diagramaCompleto();
    actualizado.actividades.push(creada);
    crearActividad.mockReturnValue(of(creada));
    obtenerDiagrama.mockReturnValue(of(actualizado));

    expect(elemento.querySelector('[role="toolbar"]')).not.toBeNull();
    botonConTexto('Nueva actividad')!.click();
    await fixture.whenStable();
    const nombre = elemento.querySelector<HTMLInputElement>('[formControlName="nombre"]')!;
    nombre.value = 'Archivar solicitud';
    nombre.dispatchEvent(new Event('input'));
    const tipo = elemento.querySelector<HTMLSelectElement>('[formControlName="tipo"]')!;
    tipo.selectedIndex = 1;
    tipo.dispatchEvent(new Event('change'));
    elemento.querySelector('app-formulario-actividad form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(crearActividad).toHaveBeenCalledWith(5, expect.objectContaining({ nombre: 'Archivar solicitud', laneId: 11 }));
    expect(obtenerDiagrama).toHaveBeenCalledTimes(2);
    expect(obtener).toHaveBeenCalledTimes(1);
    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(4);
    expect(elemento.querySelector('.panel__nombre')?.textContent).toBe('Archivar solicitud');
    expect(botonConTexto('Editar')).toBeDefined();
    expect(botonConTexto('Eliminar')).toBeUndefined();
  });

  it('solo el administrador puede eliminar actividades, gateways y arcos', async () => {
    await crear(PROCESO_PRUEBA, 'ADMINISTRADOR');
    await seleccionarEnLista('Evaluar solicitud');

    expect(botonConTexto('Editar')).toBeDefined();
    expect(botonConTexto('Eliminar')).toBeDefined();
  });
});
