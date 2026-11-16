import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { HistorialRolProceso, RolProcesoResumen } from '../../core/modelos/rol-proceso';
import { RolUsuario } from '../../core/modelos/sesion';
import { RolProcesoService } from '../../core/servicios/rol-proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { DetalleRol } from './detalle-rol';

const LIBRE: RolProcesoResumen = {
  id: 22,
  nombre: 'Analista',
  descripcion: 'Registra y evalúa las solicitudes',
  activo: true,
  enUso: false,
  puedeEliminar: true,
  procesos: [],
};

const EN_USO: RolProcesoResumen = {
  ...LIBRE,
  id: 21,
  nombre: 'Supervisor',
  enUso: true,
  puedeEliminar: false,
  procesos: [{ id: 5, nombre: 'Atención de solicitudes', lanes: 1, actividadesActivas: 2 }],
};

const HISTORIAL: HistorialRolProceso[] = [
  { fecha: '2026-10-18T09:30:00', usuarioCorreo: 'admin@alpes.com', accion: 'EDICION', cambiosRealizados: "descripcion: 'A' -> 'B'" },
  { fecha: '2026-10-17T08:00:00', usuarioCorreo: 'admin@alpes.com', accion: 'CREACION', cambiosRealizados: "rol de proceso creado: 'Analista'" },
];

describe('DetalleRol', () => {
  let fixture: ComponentFixture<DetalleRol>;
  let elemento: HTMLElement;
  const obtener = vi.fn<(id: number) => Observable<RolProcesoResumen>>();
  const historial = vi.fn<(id: number) => Observable<HistorialRolProceso[]>>();
  const previsualizarEliminacion = vi.fn<(id: number) => Observable<RolProcesoResumen>>();
  const eliminar = vi.fn<(id: number) => Observable<void>>();

  async function crear(rol: RolProcesoResumen, sesion: RolUsuario = 'ADMINISTRADOR', id = String(rol.id)): Promise<void> {
    obtener.mockReset().mockReturnValue(of(rol));
    historial.mockReset().mockReturnValue(of(HISTORIAL));
    previsualizarEliminacion.mockReset().mockReturnValue(of(rol));
    eliminar.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RolProcesoService, useValue: { obtener, historial, previsualizarEliminacion, eliminar } },
        { provide: SesionService, useValue: { tieneRol: (roles: readonly RolUsuario[]) => roles.includes(sesion) } },
      ],
    });
    fixture = TestBed.createComponent(DetalleRol);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('id', id);
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((candidato) => candidato.textContent?.trim() === texto);
  }

  function acciones(): string[] {
    return Array.from(elemento.querySelectorAll('.encabezado-pagina__acciones a, .encabezado-pagina__acciones button')).map(
      (accion) => accion.textContent?.trim() ?? '',
    );
  }

  it('muestra los datos, el uso en procesos y el historial del rol', async () => {
    await crear(EN_USO);

    expect(obtener).toHaveBeenCalledWith(21);
    expect(historial).toHaveBeenCalledWith(21);
    expect(elemento.querySelector('h1')?.textContent).toBe('Supervisor');
    expect(elemento.textContent).toContain('En uso');
    const uso = elemento.querySelectorAll('[aria-labelledby="titulo-uso-rol"] tbody tr');
    expect(uso.length).toBe(1);
    expect(uso[0].querySelector('a')?.getAttribute('href')).toBe('/procesos/5');
    expect(uso[0].textContent).toContain('2');
    const cambios = elemento.querySelectorAll('[aria-labelledby="titulo-historial-rol"] tbody tr');
    expect(Array.from(cambios[0].querySelectorAll('td')).map((celda) => celda.textContent?.trim())).toEqual([
      '18/10/2026 09:30',
      'admin@alpes.com',
      'Edición',
      "descripcion: 'A' -> 'B'",
    ]);
  });

  it('no permite eliminar un rol en uso y explica el motivo', async () => {
    await crear(EN_USO);

    expect(boton('Eliminar rol')?.disabled).toBe(true);
    expect(boton('Eliminar rol')?.getAttribute('aria-describedby')).toBe('aviso-rol-en-uso');
    expect(elemento.querySelector('#aviso-rol-en-uso')?.textContent).toContain('lo usan lanes activas');
  });

  it('confirma con la vista previa del backend y elimina un rol libre', async () => {
    await crear(LIBRE);
    eliminar.mockReturnValue(of(undefined));
    obtener.mockReturnValue(of({ ...LIBRE, activo: false, puedeEliminar: false }));

    boton('Eliminar rol')!.click();
    await fixture.whenStable();
    expect(previsualizarEliminacion).toHaveBeenCalledWith(22);
    expect(elemento.querySelector('[role="alertdialog"]')?.textContent).toContain('«Analista»');

    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(eliminar).toHaveBeenCalledWith(22);
    expect(elemento.textContent).toContain('El rol de proceso se eliminó');
    expect(elemento.querySelector('.chip--peligro')?.textContent).toBe('Eliminado');
    expect(acciones()).toEqual(['Volver al listado']);
  });

  it('no abre la confirmación si la vista previa revela que el rol pasó a estar en uso', async () => {
    await crear(LIBRE);
    previsualizarEliminacion.mockReturnValue(of({ ...EN_USO, id: 22 }));

    boton('Eliminar rol')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Lo usan lanes de: Atención de solicitudes.');
    expect(eliminar).not.toHaveBeenCalled();
  });

  it('muestra en el diálogo el conflicto 409 del backend', async () => {
    await crear(LIBRE);
    eliminar.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'conflicto',
        estado: 409,
        codigo: 'ROL_PROCESO_EN_USO',
        titulo: 'La operación entra en conflicto con los datos actuales',
        detalle: "El rol de proceso 'Analista' no se puede eliminar porque lo usan lanes de los procesos: Compras",
      })),
    );

    boton('Eliminar rol')!.click();
    await fixture.whenStable();
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alertdialog"] [role="alert"]')?.textContent).toContain('procesos: Compras');
    expect(obtener).toHaveBeenCalledTimes(1);
  });

  it('al editor le permite editar pero no eliminar, y al de solo lectura solo consultar', async () => {
    await crear(LIBRE, 'EDITOR');
    expect(acciones()).toEqual(['Volver al listado', 'Editar rol']);

    TestBed.resetTestingModule();
    await crear(LIBRE, 'SOLO_LECTURA');
    expect(acciones()).toEqual(['Volver al listado']);
  });

  it('muestra el rechazo de un rol de otra empresa', async () => {
    await crear(LIBRE);
    obtener.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'sin-permiso',
        estado: 403,
        codigo: 'USUARIO_SIN_PERMISO',
        titulo: 'No tienes permiso para esta consulta',
        detalle: 'El rol de proceso no pertenece a la empresa del usuario',
      })),
    );
    fixture.componentRef.setInput('id', '99');
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso para esta consulta');
    expect(elemento.querySelector('h1')).toBeNull();
  });
});
