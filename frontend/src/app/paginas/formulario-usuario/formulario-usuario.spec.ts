import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { CrearUsuario, Usuario } from '../../core/modelos/usuario';
import { UsuarioService } from '../../core/servicios/usuario-service';
import { FormularioUsuario } from './formulario-usuario';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string, campos?: Record<string, string>): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle, ...(campos ? { campos } : {}) };
}

describe('FormularioUsuario', () => {
  let fixture: ComponentFixture<FormularioUsuario>;
  let elemento: HTMLElement;
  let navegar: ReturnType<typeof vi.spyOn>;
  const crearApi = vi.fn<(datos: CrearUsuario) => Observable<Usuario>>();

  beforeEach(async () => {
    crearApi.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: UsuarioService, useValue: { crear: crearApi } }],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(FormularioUsuario);
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  });

  async function escribir(id: string, valor: string): Promise<void> {
    const campo = elemento.querySelector<HTMLInputElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function elegirRol(texto: string): Promise<void> {
    const lista = elemento.querySelector<HTMLSelectElement>('#campo-usuario-rol')!;
    lista.selectedIndex = Array.from(lista.options).findIndex((opcion) => opcion.textContent?.trim() === texto);
    lista.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function completar(): Promise<void> {
    await escribir('campo-usuario-correo', ' editor@alpes.com ');
    await escribir('campo-usuario-password', 'Clave-Inicial-2026');
    await elegirRol('Editor');
  }

  async function enviar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function error(id: string): string {
    return elemento.querySelector(`#${id}`)?.textContent?.trim() ?? '';
  }

  it('solo pide los campos del DTO y usa un campo de contraseña sin autocompletar', () => {
    expect(Array.from(elemento.querySelectorAll('input, select')).map((campo) => campo.id)).toEqual([
      'campo-usuario-correo',
      'campo-usuario-password',
      'campo-usuario-rol',
    ]);
    const password = elemento.querySelector<HTMLInputElement>('#campo-usuario-password')!;
    expect(password.type).toBe('password');
    expect(password.getAttribute('autocomplete')).toBe('new-password');
  });

  it('valida correo, longitud de la contraseña y rol sin llamar a la API', async () => {
    await escribir('campo-usuario-correo', 'sin-arroba');
    await escribir('campo-usuario-password', 'corta');
    await enviar();

    expect(crearApi).not.toHaveBeenCalled();
    expect(error('error-usuario-correo')).toBe('El correo no tiene un formato válido');
    expect(error('error-usuario-password')).toBe('La contraseña debe tener entre 8 y 100 caracteres');
    expect(error('error-usuario-rol')).toBe('El rol de acceso es obligatorio');
    expect(elemento.textContent).toContain('Revisa los campos marcados');
  });

  it('crea el usuario con el correo sin espacios y abre su detalle', async () => {
    crearApi.mockReturnValue(of({ id: 15, correo: 'editor@alpes.com', rol: 'EDITOR', activo: true }));
    await completar();
    await enviar();

    expect(crearApi).toHaveBeenCalledWith({ correo: 'editor@alpes.com', password: 'Clave-Inicial-2026', rol: 'EDITOR' });
    expect(navegar).toHaveBeenCalledWith(['/usuarios', 15]);
  });

  it('marca el correo en uso que informa el servidor', async () => {
    crearApi.mockReturnValue(
      throwError(() => fallo('conflicto', 409, 'USUARIO_CORREO_EN_USO', 'El correo editor@alpes.com ya esta asociado a otro usuario')),
    );
    await completar();
    await enviar();

    expect(error('error-usuario-correo')).toBe('El correo editor@alpes.com ya esta asociado a otro usuario');
    expect(elemento.querySelector('#campo-usuario-correo')?.getAttribute('aria-invalid')).toBe('true');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('muestra los errores por campo del servidor y los rechazos sin campo como aviso', async () => {
    crearApi.mockReturnValue(
      throwError(() =>
        fallo('datos-no-validos', 400, 'DATOS_NO_VALIDOS', 'Hay campos con valores no válidos', {
          correo: 'El correo no tiene un formato valido',
        }),
      ),
    );
    await completar();
    await enviar();

    expect(error('error-usuario-correo')).toBe('El correo no tiene un formato valido');

    crearApi.mockReturnValue(
      throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'Solo un administrador puede gestionar los usuarios de la empresa')),
    );
    await escribir('campo-usuario-correo', 'otro@alpes.com');
    await enviar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Solo un administrador puede gestionar');
  });
});
