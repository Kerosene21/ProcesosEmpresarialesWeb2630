import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { PROCESO_PRUEBA } from '../../../testing/diagramas';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { ConfigurarPermisoEstructura, PermisoEstructura } from '../../core/modelos/permiso-estructura';
import { Proceso } from '../../core/modelos/proceso';
import { RolUsuario } from '../../core/modelos/sesion';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { PanelPermisos } from './panel-permisos';

function matriz(editor: Partial<PermisoEstructura> = {}): PermisoEstructura[] {
  return [
    { rol: 'ADMINISTRADOR', configurable: false, crearPool: true, editarPool: true, eliminarPool: true, crearLane: true, editarLane: true, eliminarLane: true },
    { rol: 'EDITOR', configurable: true, crearPool: true, editarPool: true, eliminarPool: false, crearLane: true, editarLane: true, eliminarLane: false, ...editor },
    { rol: 'SOLO_LECTURA', configurable: false, crearPool: false, editarPool: false, eliminarPool: false, crearLane: false, editarLane: false, eliminarLane: false },
  ];
}

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Error del servidor', detalle };
}

describe('PanelPermisos', () => {
  let fixture: ComponentFixture<PanelPermisos>;
  let elemento: HTMLElement;
  const permisosEstructura = vi.fn<(id: number) => Observable<PermisoEstructura[]>>();
  const configurarPermisosEstructura =
    vi.fn<(id: number, rol: RolUsuario, datos: ConfigurarPermisoEstructura) => Observable<PermisoEstructura[]>>();

  async function crear(
    rol: RolUsuario = 'ADMINISTRADOR',
    proceso: Proceso = PROCESO_PRUEBA,
    respuesta: Observable<PermisoEstructura[]> = of(matriz()),
  ): Promise<void> {
    permisosEstructura.mockReset().mockReturnValue(respuesta);
    configurarPermisosEstructura.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: ProcesoService, useValue: { permisosEstructura, configurarPermisosEstructura } },
        { provide: SesionService, useValue: { tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol) } },
      ],
    });
    fixture = TestBed.createComponent(PanelPermisos);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('proceso', proceso);
    await fixture.whenStable();
  }

  function casillas(rol: RolUsuario): HTMLInputElement[] {
    return Array.from(elemento.querySelectorAll<HTMLInputElement>(`tr[data-rol="${rol}"] input[type="checkbox"]`));
  }

  function valores(rol: RolUsuario): boolean[] {
    return casillas(rol).map((casilla) => casilla.checked);
  }

  function boton(texto: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.textContent?.trim() === texto,
    );
  }

  async function alternar(rol: RolUsuario, indice: number): Promise<void> {
    casillas(rol)[indice].click();
    await fixture.whenStable();
  }

  async function guardar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('muestra la matriz real por rol de acceso con las operaciones sobre pools y lanes', async () => {
    await crear();
    const cabeceras = Array.from(elemento.querySelectorAll('thead th')).map((celda) => celda.textContent?.trim());

    expect(permisosEstructura).toHaveBeenCalledWith(5);
    expect(cabeceras).toEqual(['Rol de acceso', 'Pools', 'Lanes', 'Crear', 'Editar', 'Eliminar', 'Crear', 'Editar', 'Eliminar']);
    expect(valores('ADMINISTRADOR')).toEqual([true, true, true, true, true, true]);
    expect(valores('EDITOR')).toEqual([true, true, false, true, true, false]);
    expect(valores('SOLO_LECTURA')).toEqual([false, false, false, false, false, false]);
    expect(elemento.querySelector('tr[data-rol="EDITOR"] th')?.textContent).toContain('Configurable');
    expect(elemento.querySelector('tr[data-rol="ADMINISTRADOR"] th')?.textContent).toContain('Fijo');
    expect(casillas('EDITOR')[2].getAttribute('aria-label')).toBe('Eliminar pools: Editor');
  });

  it('distingue los roles de acceso de los roles de proceso', async () => {
    await crear();

    expect(elemento.textContent).toContain('rol de acceso');
    expect(elemento.textContent).toContain('No confundir con los roles de proceso');
  });

  it('el administrador solo puede cambiar la fila configurable', async () => {
    await crear();

    expect(casillas('ADMINISTRADOR').every((casilla) => casilla.disabled)).toBe(true);
    expect(casillas('SOLO_LECTURA').every((casilla) => casilla.disabled)).toBe(true);
    expect(casillas('EDITOR').some((casilla) => casilla.disabled)).toBe(false);
    expect(boton('Guardar permisos')?.disabled).toBe(true);
  });

  it('guarda los permisos del editor y vuelve a consultar la matriz', async () => {
    await crear();
    configurarPermisosEstructura.mockReturnValue(of(matriz({ eliminarPool: true, editarLane: false })));
    permisosEstructura.mockReturnValue(of(matriz({ eliminarPool: true, editarLane: false })));

    await alternar('EDITOR', 2);
    await alternar('EDITOR', 4);
    expect(boton('Guardar permisos')?.disabled).toBe(false);
    await guardar();

    expect(configurarPermisosEstructura).toHaveBeenCalledTimes(1);
    expect(configurarPermisosEstructura).toHaveBeenCalledWith(5, 'EDITOR', {
      crearPool: true,
      editarPool: true,
      eliminarPool: true,
      crearLane: true,
      editarLane: false,
      eliminarLane: false,
    });
    expect(permisosEstructura).toHaveBeenCalledTimes(2);
    expect(valores('EDITOR')).toEqual([true, true, true, true, false, false]);
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('Los permisos se guardaron');
    expect(boton('Guardar permisos')?.disabled).toBe(true);
  });

  it('descarta los cambios y restaura el estado vigente sin llamar a la API', async () => {
    await crear();

    await alternar('EDITOR', 0);
    expect(valores('EDITOR')[0]).toBe(false);
    boton('Descartar cambios')!.click();
    await fixture.whenStable();

    expect(valores('EDITOR')).toEqual([true, true, false, true, true, false]);
    expect(configurarPermisosEstructura).not.toHaveBeenCalled();
    expect(boton('Descartar cambios')?.disabled).toBe(true);
  });

  it('muestra el rechazo del backend y conserva lo marcado para corregirlo', async () => {
    await crear();
    configurarPermisosEstructura.mockReturnValue(
      throwError(() =>
        fallo('datos-no-validos', 400, 'PERMISO_ESTRUCTURA_NO_VALIDO', 'El rol SOLO_LECTURA nunca puede modificar la estructura del proceso'),
      ),
    );

    await alternar('EDITOR', 0);
    await guardar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('nunca puede modificar la estructura');
    expect(valores('EDITOR')[0]).toBe(false);
    expect(permisosEstructura).toHaveBeenCalledTimes(1);
  });

  it('informa el 403 cuando el servidor no reconoce al usuario como administrador', async () => {
    await crear();
    configurarPermisosEstructura.mockReturnValue(
      throwError(() =>
        fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'Solo un administrador de la empresa propietaria puede configurar los permisos de estructura'),
      ),
    );

    await alternar('EDITOR', 1);
    await guardar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Solo un administrador de la empresa propietaria');
  });

  it('el editor y el usuario de solo lectura consultan la matriz sin modificarla', async () => {
    for (const rol of ['EDITOR', 'SOLO_LECTURA'] as const) {
      TestBed.resetTestingModule();
      await crear(rol);

      expect(valores('EDITOR')).toEqual([true, true, false, true, true, false]);
      expect(elemento.querySelectorAll('input[type="checkbox"]:not(:disabled)').length).toBe(0);
      expect(boton('Guardar permisos')).toBeUndefined();
      expect(elemento.textContent).toContain('Solo un administrador puede cambiar los permisos del editor');
    }
  });

  it('en un proceso eliminado muestra la configuración solo para consulta', async () => {
    await crear('ADMINISTRADOR', { ...PROCESO_PRUEBA, eliminado: true });

    expect(elemento.querySelectorAll('input[type="checkbox"]:not(:disabled)').length).toBe(0);
    expect(boton('Guardar permisos')).toBeUndefined();
    expect(elemento.textContent).toContain('El proceso fue eliminado');
  });

  it('informa si no puede consultar la matriz, por ejemplo en un proceso ajeno', async () => {
    await crear(
      'ADMINISTRADOR',
      PROCESO_PRUEBA,
      throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'El proceso no pertenece a la empresa del usuario')),
    );

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No se pudieron consultar los permisos de estructura');
    expect(elemento.querySelector('table')).toBeNull();
    permisosEstructura.mockReturnValue(of(matriz()));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('table')).not.toBeNull();
  });
});
