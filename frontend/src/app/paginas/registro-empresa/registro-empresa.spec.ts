import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import type { MockInstance } from 'vitest';

import { Empresa, RegistroEmpresa as DatosRegistro } from '../../core/modelos/empresa';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { EmpresaService } from '../../core/servicios/empresa-service';
import { RegistroEmpresa } from './registro-empresa';

const EMPRESA: Empresa = {
  id: 10,
  nombre: 'Alpes Logistica',
  nit: '900123456-7',
  correoContacto: 'admin@alpes.com',
  administradorUsername: 'admin@alpes.com',
};

describe('RegistroEmpresa', () => {
  let fixture: ComponentFixture<RegistroEmpresa>;
  let elemento: HTMLElement;
  let navegar: MockInstance;
  const registrar = vi.fn<(datos: DatosRegistro) => Observable<Empresa>>();

  beforeEach(async () => {
    registrar.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: EmpresaService, useValue: { registrar } }],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(RegistroEmpresa);
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  });

  function escribir(selector: string, valor: string): void {
    const control = elemento.querySelector<HTMLInputElement>(selector)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
  }

  async function enviar(): Promise<void> {
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errores(): string[] {
    return Array.from(elemento.querySelectorAll('.campo__error'))
      .map((error) => error.textContent?.trim() ?? '')
      .filter((texto) => texto.length > 0);
  }

  async function completar(password = 'Clave-Inicial-2026'): Promise<void> {
    escribir('#campo-nombre', 'Alpes Logistica');
    escribir('#campo-nit', '900123456-7');
    escribir('#campo-correo', 'admin@alpes.com');
    escribir('#campo-password', password);
    await enviar();
  }

  it('exige los cuatro campos del DTO antes de llamar a la API', async () => {
    await enviar();

    expect(errores()).toEqual([
      'El nombre de la empresa es obligatorio',
      'El NIT es obligatorio',
      'El correo de contacto es obligatorio',
      'La contraseña del administrador inicial es obligatoria',
    ]);
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Revisa los campos marcados');
    expect(registrar).not.toHaveBeenCalled();
  });

  it('aplica la longitud mínima de la contraseña, el formato del correo y el NIT en blanco', async () => {
    escribir('#campo-nombre', 'Alpes');
    escribir('#campo-nit', '   ');
    escribir('#campo-correo', 'no-es-correo');
    escribir('#campo-password', 'corta12');
    await enviar();

    expect(errores()).toEqual([
      'El NIT es obligatorio',
      'El correo de contacto no tiene un formato válido',
      'La contraseña debe tener entre 8 y 100 caracteres',
    ]);
    expect(registrar).not.toHaveBeenCalled();
  });

  it('registra la empresa con los datos reales y lleva al inicio de sesión', async () => {
    registrar.mockReturnValue(of(EMPRESA));

    await completar();

    expect(registrar).toHaveBeenCalledWith({
      nombre: 'Alpes Logistica',
      nit: '900123456-7',
      correoContacto: 'admin@alpes.com',
      passwordAdministrador: 'Clave-Inicial-2026',
    });
    expect(navegar).toHaveBeenCalledWith(['/iniciar-sesion'], { queryParams: { motivo: 'registrada' } });
  });

  it('muestra el NIT duplicado junto al campo y no simula el registro', async () => {
    const conflicto: FalloHttp = {
      tipo: 'conflicto',
      estado: 409,
      codigo: 'EMPRESA_NIT_DUPLICADO',
      titulo: 'Conflicto',
      detalle: 'Ya existe una empresa registrada con el NIT 900123456-7',
    };
    registrar.mockReturnValue(throwError(() => conflicto));

    await completar();

    expect(errores()).toEqual(['Ya existe una empresa registrada con el NIT 900123456-7']);
    expect(elemento.querySelector<HTMLInputElement>('#campo-nit')?.getAttribute('aria-invalid')).toBe('true');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('asocia a cada campo los errores de validación del backend', async () => {
    const invalido: FalloHttp = {
      tipo: 'datos-no-validos',
      estado: 400,
      codigo: 'DATOS_NO_VALIDOS',
      titulo: 'Los datos enviados no son válidos',
      detalle: 'Hay campos con valores no válidos',
      campos: { correoContacto: 'El correo de contacto no tiene un formato valido' },
    };
    registrar.mockReturnValue(throwError(() => invalido));

    await completar();

    expect(errores()).toEqual(['El correo de contacto no tiene un formato valido']);
  });

  it('informa los fallos generales sin presentarlos como registro exitoso', async () => {
    registrar.mockReturnValue(
      throwError(() => ({ tipo: 'conexion', estado: 0, codigo: null, titulo: 'No hay conexión con el servidor', detalle: null })),
    );

    await completar();

    expect(elemento.textContent).toContain('No hay conexión con el servidor');
    expect(navegar).not.toHaveBeenCalled();
    expect(elemento.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });
});
