import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { PROCESO_PRUEBA } from '../../../testing/diagramas';
import { EmpresaInvitada } from '../../core/modelos/comparticion';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { Proceso } from '../../core/modelos/proceso';
import { RolUsuario } from '../../core/modelos/sesion';
import { ComparticionService } from '../../core/servicios/comparticion-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { PanelComparticion } from './panel-comparticion';

const ANDES: EmpresaInvitada = { id: 9, nombre: 'Andes SAS' };
const PACIFICO: EmpresaInvitada = { id: 12, nombre: 'Pacífico Ltda' };

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string | null, detalle: string | null): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Error del servidor', detalle };
}

describe('PanelComparticion', () => {
  let fixture: ComponentFixture<PanelComparticion>;
  let elemento: HTMLElement;
  const listar = vi.fn<(procesoId: number) => Observable<EmpresaInvitada[]>>();
  const compartir = vi.fn<(procesoId: number, empresaId: number) => Observable<EmpresaInvitada>>();
  const dejarDeCompartir = vi.fn<(procesoId: number, empresaId: number) => Observable<void>>();

  async function crear(
    rol: RolUsuario = 'ADMINISTRADOR',
    proceso: Proceso = PROCESO_PRUEBA,
    invitadas: Observable<EmpresaInvitada[]> = of([ANDES]),
  ): Promise<void> {
    listar.mockReset().mockReturnValue(invitadas);
    compartir.mockReset();
    dejarDeCompartir.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: ComparticionService, useValue: { listar, compartir, dejarDeCompartir } },
        { provide: SesionService, useValue: { tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol) } },
      ],
    });
    fixture = TestBed.createComponent(PanelComparticion);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('proceso', proceso);
    await fixture.whenStable();
  }

  function boton(etiqueta: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.getAttribute('aria-label') === etiqueta || candidato.textContent?.trim() === etiqueta,
    );
  }

  async function compartirCon(valor: string): Promise<void> {
    const campo = elemento.querySelector<HTMLInputElement>('#comparticion-empresa')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function empresas(): string[] {
    return Array.from(elemento.querySelectorAll('.comparticion__empresa strong')).map((nombre) => nombre.textContent ?? '');
  }

  function errorDelCampo(): string | null | undefined {
    return elemento.querySelector('.comparticion__formulario .campo__error')?.textContent;
  }

  it('consulta y muestra las empresas con acceso de solo lectura', async () => {
    await crear();

    expect(listar).toHaveBeenCalledWith(5);
    expect(empresas()).toEqual(['Andes SAS']);
    expect(elemento.querySelector('.comparticion__empresa')?.textContent).toContain('identificador 9');
  });

  it('indica que el proceso es privado cuando no se comparte con nadie', async () => {
    await crear('ADMINISTRADOR', PROCESO_PRUEBA, of([]));

    expect(elemento.textContent).toContain('El proceso es privado');
  });

  it('informa el fallo de la consulta y permite reintentarla', async () => {
    await crear('ADMINISTRADOR', PROCESO_PRUEBA, throwError(() => fallo('servidor', 500, null, null)));

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No se pudo consultar con quién se comparte');
    listar.mockReturnValue(of([ANDES]));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(empresas()).toEqual(['Andes SAS']);
  });

  it('el administrador comparte por identificador y vuelve a consultar la lista', async () => {
    await crear();
    compartir.mockReturnValue(of(PACIFICO));
    listar.mockReturnValue(of([ANDES, PACIFICO]));

    await compartirCon('12');

    expect(compartir).toHaveBeenCalledWith(5, 12);
    expect(listar).toHaveBeenCalledTimes(2);
    expect(empresas()).toEqual(['Andes SAS', 'Pacífico Ltda']);
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('se compartió en solo lectura con «Pacífico Ltda»');
    expect(elemento.querySelector<HTMLInputElement>('#comparticion-empresa')?.value).toBe('');
  });

  it('valida el identificador y evita compartir dos veces con la misma empresa', async () => {
    await crear();

    await compartirCon('');
    expect(errorDelCampo()).toBe('Indica el identificador de la empresa');
    await compartirCon('0');
    expect(errorDelCampo()).toBe('Usa un identificador entero mayor que cero');
    await compartirCon('9');
    expect(errorDelCampo()).toBe('El proceso ya está compartido con esa empresa');

    expect(compartir).not.toHaveBeenCalled();
  });

  it('muestra en el campo los rechazos de negocio del backend', async () => {
    await crear();
    compartir.mockReturnValue(
      throwError(() => fallo('datos-no-validos', 400, 'COMPARTICION_NO_VALIDA', 'Un proceso no se comparte con su propia empresa')),
    );

    await compartirCon('7');
    expect(errorDelCampo()).toBe('Un proceso no se comparte con su propia empresa');

    compartir.mockReturnValue(
      throwError(() => fallo('conflicto', 409, 'PROCESO_YA_COMPARTIDO', 'El proceso ya está compartido con esa empresa')),
    );
    await compartirCon('15');
    expect(errorDelCampo()).toBe('El proceso ya está compartido con esa empresa');
    expect(listar).toHaveBeenCalledTimes(1);
  });

  it('informa como error general que la empresa no existe o que falta el permiso', async () => {
    await crear();
    compartir.mockReturnValue(throwError(() => fallo('no-encontrado', 404, 'RECURSO_NO_ENCONTRADO', 'La empresa no existe')));

    await compartirCon('999');
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('La empresa no existe');

    compartir.mockReturnValue(
      throwError(() =>
        fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'Solo un administrador de la empresa propietaria puede cambiar con quién se comparte el proceso'),
      ),
    );
    await compartirCon('15');
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Solo un administrador de la empresa propietaria');
  });

  it('retira el acceso tras confirmar y actualiza la lista', async () => {
    await crear();
    dejarDeCompartir.mockReturnValue(of(undefined));
    listar.mockReturnValue(of([]));

    boton('Retirar el acceso de Andes SAS')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')?.textContent).toContain('«Andes SAS» dejará de ver este proceso');
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(dejarDeCompartir).toHaveBeenCalledWith(5, 9);
    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('«Andes SAS» ya no puede consultar el proceso');
    expect(elemento.textContent).toContain('El proceso es privado');
  });

  it('no retira el acceso si se cancela y muestra el rechazo dentro del diálogo', async () => {
    await crear();

    boton('Retirar el acceso de Andes SAS')!.click();
    await fixture.whenStable();
    boton('Cancelar')!.click();
    await fixture.whenStable();
    expect(dejarDeCompartir).not.toHaveBeenCalled();

    dejarDeCompartir.mockReturnValue(throwError(() => fallo('no-encontrado', 404, 'RECURSO_NO_ENCONTRADO', 'El proceso no está compartido con esa empresa')));
    boton('Retirar el acceso de Andes SAS')!.click();
    await fixture.whenStable();
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alertdialog"] [role="alert"]')?.textContent).toContain('no está compartido con esa empresa');
  });

  it('el editor y el usuario de solo lectura consultan la lista pero no cambian el acceso', async () => {
    for (const rol of ['EDITOR', 'SOLO_LECTURA'] as const) {
      TestBed.resetTestingModule();
      await crear(rol);

      expect(empresas()).toEqual(['Andes SAS']);
      expect(elemento.querySelector('form')).toBeNull();
      expect(boton('Retirar el acceso de Andes SAS')).toBeUndefined();
      expect(elemento.textContent).toContain('Solo un administrador de tu empresa puede cambiar');
    }
  });

  it('en un proceso eliminado conserva la lista sin permitir cambios', async () => {
    await crear('ADMINISTRADOR', { ...PROCESO_PRUEBA, eliminado: true });

    expect(empresas()).toEqual(['Andes SAS']);
    expect(elemento.querySelector('form')).toBeNull();
    expect(boton('Retirar el acceso de Andes SAS')).toBeUndefined();
    expect(elemento.textContent).toContain('El proceso fue eliminado');
  });
});
