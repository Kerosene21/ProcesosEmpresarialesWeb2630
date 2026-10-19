import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NEVER, Observable, of, throwError } from 'rxjs';
import type { MockInstance } from 'vitest';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { Credenciales, Sesion } from '../../core/modelos/sesion';
import { SesionService } from '../../core/servicios/sesion-service';
import { InicioSesion, destinoSeguro } from './inicio-sesion';

const SESION: Sesion = {
  autenticado: true,
  correo: 'admin@alpes.com',
  rol: 'ADMINISTRADOR',
  empresaId: 10,
  empresaNombre: 'Alpes Logistica',
};

function fallo(estado: number, codigo: string | null, detalle: string | null): FalloHttp {
  const tipo = estado === 401 ? 'no-autenticado' : estado === 403 ? 'sin-permiso' : 'servidor';
  return { tipo, estado, codigo, titulo: 'Título del fallo', detalle };
}

describe('InicioSesion', () => {
  let fixture: ComponentFixture<InicioSesion>;
  let elemento: HTMLElement;
  let navegar: MockInstance;
  const iniciar = vi.fn<(credenciales: Credenciales) => Observable<Sesion>>();
  const cargar = vi.fn<() => Observable<Sesion>>();

  async function crear(entradas: Record<string, string> = {}): Promise<void> {
    iniciar.mockReset();
    cargar.mockReset().mockReturnValue(of(SESION));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SesionService, useValue: { iniciar, cargar } }],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(InicioSesion);
    Object.entries(entradas).forEach(([nombre, valor]) => fixture.componentRef.setInput(nombre, valor));
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  }

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

  async function completar(): Promise<void> {
    escribir('#campo-correo', 'admin@alpes.com');
    escribir('#campo-password', 'Clave-Inicial-2026');
    await enviar();
  }

  it('exige correo y contraseña antes de llamar a la API', async () => {
    await crear();

    await enviar();

    expect(errores()).toEqual(['El correo es obligatorio', 'La contraseña es obligatoria']);
    expect(iniciar).not.toHaveBeenCalled();
  });

  it('valida el formato del correo', async () => {
    await crear();

    escribir('#campo-correo', 'no-es-correo');
    escribir('#campo-password', 'Clave');
    await enviar();

    expect(errores()).toEqual(['El correo no tiene un formato válido']);
    expect(iniciar).not.toHaveBeenCalled();
  });

  it('inicia sesión con las credenciales escritas y vuelve a la ruta solicitada', async () => {
    await crear({ retorno: '/procesos/5' });
    iniciar.mockReturnValue(of(SESION));

    await completar();

    expect(iniciar).toHaveBeenCalledWith({ correo: 'admin@alpes.com', password: 'Clave-Inicial-2026' });
    expect(navegar).toHaveBeenCalledWith('/procesos/5');
  });

  it('sin ruta de retorno lleva al listado de procesos', async () => {
    await crear();
    iniciar.mockReturnValue(of(SESION));

    await completar();

    expect(navegar).toHaveBeenCalledWith('/procesos');
  });

  it('muestra el error genérico de credenciales, borra la contraseña y no navega', async () => {
    await crear();
    iniciar.mockReturnValue(throwError(() => fallo(401, 'CREDENCIALES_INVALIDAS', 'Correo o contraseña incorrectos')));

    await completar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No se pudo iniciar sesión');
    expect(elemento.textContent).toContain('Correo o contraseña incorrectos');
    expect(elemento.querySelector<HTMLInputElement>('#campo-password')?.value).toBe('');
    expect(elemento.querySelector<HTMLInputElement>('#campo-correo')?.value).toBe('admin@alpes.com');
    expect(elemento.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('renueva el token CSRF cuando el servidor rechaza la solicitud', async () => {
    await crear();
    iniciar.mockReturnValue(throwError(() => fallo(403, 'USUARIO_SIN_PERMISO', 'No tienes permiso')));

    await completar();

    expect(cargar).toHaveBeenCalledTimes(1);
    expect(elemento.textContent).toContain('La solicitud no se pudo validar');
  });

  it('informa un fallo del servidor sin presentarlo como credenciales incorrectas', async () => {
    await crear();
    iniciar.mockReturnValue(throwError(() => ({ ...fallo(500, null, null), titulo: 'El servidor no pudo atender la solicitud' })));

    await completar();

    expect(elemento.textContent).toContain('El servidor no pudo atender la solicitud');
    expect(elemento.textContent).not.toContain('No se pudo iniciar sesión');
  });

  it('deshabilita el envío mientras espera la respuesta', async () => {
    await crear();
    iniciar.mockReturnValue(NEVER);

    await completar();

    const boton = elemento.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(boton.disabled).toBe(true);
    expect(boton.textContent?.trim()).toBe('Iniciando sesión…');
  });

  it('explica por qué se pide iniciar sesión de nuevo', async () => {
    await crear({ motivo: 'expirada' });

    expect(elemento.textContent).toContain('Tu sesión expiró');
  });

  it('solo acepta rutas de retorno internas', () => {
    expect(destinoSeguro('/procesos/5?alcance=TODOS')).toBe('/procesos/5?alcance=TODOS');
    expect(destinoSeguro(undefined)).toBe('/procesos');
    expect(destinoSeguro('https://ejemplo.com')).toBe('/procesos');
    expect(destinoSeguro('//ejemplo.com')).toBe('/procesos');
    expect(destinoSeguro('/\\ejemplo.com')).toBe('/procesos');
    expect(destinoSeguro('/iniciar-sesion?retorno=/procesos')).toBe('/procesos');
  });
});
