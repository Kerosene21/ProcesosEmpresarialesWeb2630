import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto } from '../../../testing/diagramas';
import { MessageThrowRespuesta } from '../../core/modelos/diagrama';
import { CrearMessageThrow, EditarMessageThrow } from '../../core/modelos/evento';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { MessageThrowService } from '../../core/servicios/message-throw-service';
import { PropuestaEvento } from '../eventos';
import { calcularGeometria } from '../geometria';
import { FormularioMessageThrow } from './formulario-message-throw';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('FormularioMessageThrow', () => {
  let fixture: ComponentFixture<FormularioMessageThrow>;
  let elemento: HTMLElement;
  let guardados: MessageThrowRespuesta[];
  let cancelaciones: number;
  const crearApi = vi.fn<(procesoId: number, datos: CrearMessageThrow) => Observable<MessageThrowRespuesta>>();
  const editarApi = vi.fn<(procesoId: number, id: number, datos: EditarMessageThrow) => Observable<MessageThrowRespuesta>>();

  async function crear(actual: MessageThrowRespuesta | null, propuesta: PropuestaEvento | null): Promise<void> {
    crearApi.mockReset();
    editarApi.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: MessageThrowService, useValue: { crear: crearApi, editar: editarApi } }],
    });
    fixture = TestBed.createComponent(FormularioMessageThrow);
    elemento = fixture.nativeElement;
    guardados = [];
    cancelaciones = 0;
    fixture.componentInstance.guardado.subscribe((guardado) => guardados.push(guardado));
    fixture.componentInstance.cancelado.subscribe(() => cancelaciones++);
    const diagrama = diagramaCompleto();
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('messageThrow', actual);
    fixture.componentRef.setInput('propuesta', propuesta);
    await fixture.whenStable();
  }

  function control<T extends HTMLElement>(nombre: string): T {
    return elemento.querySelector<T>(`[formControlName="${nombre}"]`)!;
  }

  function opciones(nombre: string): string[] {
    return Array.from(control<HTMLSelectElement>(nombre).options).map((opcion) => opcion.textContent?.trim() ?? '');
  }

  function errores(): string[] {
    return Array.from(elemento.querySelectorAll('.campo__error'))
      .map((error) => error.textContent?.trim() ?? '')
      .filter((texto) => texto.length > 0);
  }

  async function escribir(nombre: string, valor: string): Promise<void> {
    const campo = control<HTMLInputElement>(nombre);
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function elegir(nombre: string, texto: string): Promise<void> {
    const lista = control<HTMLSelectElement>(nombre);
    lista.selectedIndex = Array.from(lista.options).findIndex((opcion) => opcion.textContent?.trim() === texto);
    lista.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function enviar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('crea el evento en el pool y la posición propuestos hacia otro pool del proceso', async () => {
    const existente = diagramaCompleto().messageThrows[0];
    await crear(null, { poolId: 80, posicion: { x: 180, y: 300 } });
    crearApi.mockReturnValue(of({ ...existente, id: 9 }));

    expect(opciones('poolOrigenId')).toEqual(['Elige un pool', 'Empresa de prueba (propietario)', 'Cliente (externo)']);
    expect(opciones('poolDestinoId')).toEqual([
      'Elige el pool que recibe el mensaje',
      'Cliente (externo)',
      'Servicio de correo (externo, caja negra)',
    ]);
    await escribir('nombreMensaje', '  FacturaAprobada ');
    await escribir('contenido', 'numeroFactura, valorTotal');
    await elegir('poolDestinoId', 'Cliente (externo)');
    await escribir('claveCorrelacion', ' numeroFactura ');
    await elegir('comportamientoSinCaso', 'Descartar el mensaje');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, {
      nombreMensaje: 'FacturaAprobada',
      contenido: 'numeroFactura, valorTotal',
      poolDestinoId: 81,
      claveCorrelacion: 'numeroFactura',
      comportamientoSinCaso: 'DESCARTAR',
      poolOrigenId: 80,
      posicionX: 180,
      posicionY: 300,
    });
    expect(guardados.map((guardado) => guardado.id)).toEqual([9]);
  });

  it('no deja elegir como receptor el mismo pool emisor', async () => {
    await crear(null, { poolId: 80, posicion: { x: 180, y: 300 } });
    await elegir('poolDestinoId', 'Cliente (externo)');

    await elegir('poolOrigenId', 'Cliente (externo)');

    expect(opciones('poolDestinoId')).toEqual([
      'Elige el pool que recibe el mensaje',
      'Empresa de prueba (propietario)',
      'Servicio de correo (externo, caja negra)',
    ]);
    expect(control<HTMLSelectElement>('poolDestinoId').selectedIndex).toBe(0);
  });

  it('valida los campos obligatorios y la longitud antes de llamar a la API', async () => {
    await crear(null, { poolId: 80, posicion: { x: 180, y: 300 } });

    await enviar();
    expect(errores()).toEqual([
      'El nombre del mensaje es obligatorio',
      'Documenta el contenido del mensaje',
      'Elige el pool que recibe el mensaje',
    ]);
    await escribir('nombreMensaje', 'x'.repeat(151));
    await escribir('claveCorrelacion', 'c'.repeat(101));

    expect(errores()).toContain('No puede superar 150 caracteres');
    expect(errores()).toContain('No puede superar 100 caracteres');
    expect(crearApi).not.toHaveBeenCalled();
  });

  it('edita el mensaje sin cambiar el pool emisor ni la posición y muestra las advertencias actuales', async () => {
    const actual = {
      ...diagramaCompleto().messageThrows[0],
      advertencias: ["No existe un Message Catch 'Solicitud de servicio' en el pool destino 'Servicio de correo'"],
    };
    await crear(actual, null);
    editarApi.mockReturnValue(of(actual));

    expect(control<HTMLSelectElement>('poolOrigenId')).toBeNull();
    expect(elemento.textContent).toContain('Emite desde el pool «Cliente» en x 60, y 380');
    expect(elemento.querySelector('.formulario__advertencias')?.textContent).toContain('No existe un Message Catch');
    expect(control<HTMLInputElement>('claveCorrelacion').value).toBe('numeroSolicitud');
    await elegir('poolDestinoId', 'Servicio de correo (externo, caja negra)');
    await escribir('claveCorrelacion', '');
    await elegir('comportamientoSinCaso', 'Sin documentar');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 6, {
      nombreMensaje: 'Solicitud de servicio',
      contenido: 'Datos de la solicitud de servicio del cliente',
      poolDestinoId: 82,
      claveCorrelacion: null,
      comportamientoSinCaso: null,
    });
  });

  it('muestra junto al receptor el rechazo de un destino no permitido', async () => {
    await crear(diagramaCompleto().messageThrows[0], null);
    editarApi.mockReturnValue(
      throwError(() => fallo('datos-no-validos', 400, 'POOL_MENSAJE_NO_VALIDO', 'El pool destino del mensaje no es un pool activo')),
    );

    await enviar();

    expect(errores()).toEqual(['El pool destino del mensaje no es un pool activo']);
    expect(guardados).toEqual([]);
  });

  it('muestra como error general un rechazo sin campo y permite cancelar', async () => {
    await crear(diagramaCompleto().messageThrows[0], null);
    editarApi.mockReturnValue(
      throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'Solo un administrador o editor puede crear o modificar Message Throw')),
    );

    await enviar();
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Solo un administrador o editor');

    Array.from(elemento.querySelectorAll<HTMLButtonElement>('button'))
      .find((boton) => boton.textContent?.trim() === 'Cancelar')!
      .click();
    expect(cancelaciones).toBe(1);
  });
});
