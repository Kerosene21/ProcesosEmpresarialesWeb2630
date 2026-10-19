import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { Empresa } from '../../core/modelos/empresa';
import { Sesion } from '../../core/modelos/sesion';
import { EmpresaService } from '../../core/servicios/empresa-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { MiEmpresa } from './mi-empresa';

const EMPRESA: Empresa = {
  id: 10,
  nombre: 'Alpes Logistica',
  nit: '900123456-7',
  correoContacto: 'contacto@alpes.com',
  administradorUsername: 'admin@alpes.com',
};

const SESION: Sesion = {
  autenticado: true,
  correo: 'editor@alpes.com',
  rol: 'EDITOR',
  empresaId: 10,
  empresaNombre: 'Alpes Logistica',
};

describe('MiEmpresa', () => {
  let fixture: ComponentFixture<MiEmpresa>;
  let elemento: HTMLElement;
  const actual = vi.fn<() => Observable<Empresa>>();

  async function crear(respuesta: Observable<Empresa>): Promise<void> {
    actual.mockReset().mockReturnValue(respuesta);
    TestBed.configureTestingModule({
      providers: [
        { provide: EmpresaService, useValue: { actual } },
        { provide: SesionService, useValue: { sesion: signal(SESION) } },
      ],
    });
    fixture = TestBed.createComponent(MiEmpresa);
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  }

  it('muestra los datos reales de la empresa y la sesión del usuario', async () => {
    await crear(of(EMPRESA));

    expect(actual).toHaveBeenCalledTimes(1);
    expect(elemento.querySelector('#titulo-empresa')?.textContent).toBe('Alpes Logistica');
    expect(elemento.textContent).toContain('900123456-7');
    expect(elemento.textContent).toContain('admin@alpes.com');
    expect(elemento.textContent).toContain('editor@alpes.com');
    expect(elemento.textContent).toContain('Editor');
  });

  it('informa el error y permite reintentar la consulta', async () => {
    await crear(
      throwError(() => ({ tipo: 'servidor', estado: 500, codigo: null, titulo: 'El servidor no pudo atender la solicitud', detalle: null })),
    );

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('El servidor no pudo atender la solicitud');
    actual.mockReturnValue(of(EMPRESA));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(actual).toHaveBeenCalledTimes(2);
    expect(elemento.querySelector('#titulo-empresa')?.textContent).toBe('Alpes Logistica');
  });
});
