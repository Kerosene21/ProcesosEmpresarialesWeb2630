import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { arco, diagramaCompleto } from '../../../testing/diagramas';
import { CrearArco, EditarArco } from '../../core/modelos/arco';
import { ArcoRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { ArcoService } from '../../core/servicios/arco-service';
import { calcularGeometria } from '../geometria';
import { FormularioArco, PropuestaArco } from './formulario-arco';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('FormularioArco', () => {
  let fixture: ComponentFixture<FormularioArco>;
  let elemento: HTMLElement;
  let guardados: ArcoRespuesta[];
  const crearApi = vi.fn<(procesoId: number, datos: CrearArco) => Observable<ArcoRespuesta>>();
  const editarApi = vi.fn<(procesoId: number, id: number, datos: EditarArco) => Observable<ArcoRespuesta>>();
  const creado = arco(40, ['ACTIVIDAD', 1, 'Registrar solicitud'], ['ACTIVIDAD', 3, 'Aprobar solicitud'], [225, 83, 640, 200]);

  async function crear(actual: ArcoRespuesta | null, propuesta: PropuestaArco | null): Promise<void> {
    crearApi.mockReset().mockReturnValue(of(creado));
    editarApi.mockReset().mockReturnValue(of(creado));
    TestBed.configureTestingModule({
      providers: [{ provide: ArcoService, useValue: { crear: crearApi, editar: editarApi } }],
    });
    fixture = TestBed.createComponent(FormularioArco);
    elemento = fixture.nativeElement;
    guardados = [];
    fixture.componentInstance.guardado.subscribe((guardado) => guardados.push(guardado));
    const diagrama = diagramaCompleto();
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('arco', actual);
    fixture.componentRef.setInput('propuesta', propuesta);
    await fixture.whenStable();
  }

  function control<T extends HTMLElement>(nombre: string): T {
    return elemento.querySelector<T>(`[formControlName="${nombre}"]`)!;
  }

  function opciones(nombre: string): string[] {
    return Array.from(control<HTMLSelectElement>(nombre).options).map((opcion) => opcion.textContent?.trim() ?? '');
  }

  function elegido(nombre: string): string | undefined {
    return control<HTMLSelectElement>(nombre).selectedOptions[0]?.textContent?.trim();
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

  it('crea el arco entre los extremos elegidos en el lienzo sin condición fuera de un gateway', async () => {
    await crear(null, { origen: { tipo: 'ACTIVIDAD', id: 1 }, destino: { tipo: 'ACTIVIDAD', id: 3 } });

    expect(elegido('origen')).toBe('Registrar solicitud');
    expect(elegido('destino')).toBe('Aprobar solicitud');
    expect(control('condicion')).toBeNull();
    expect(elemento.textContent).toContain('Solo los arcos que salen de un gateway exclusivo o inclusivo llevan condición');
    await escribir('etiqueta', ' Continuar ');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, {
      origenTipo: 'ACTIVIDAD',
      origenId: 1,
      destinoTipo: 'ACTIVIDAD',
      destinoId: 3,
      etiqueta: 'Continuar',
      condicion: null,
    });
    expect(guardados).toEqual([creado]);
  });

  it('solo ofrece destinos del mismo pool que admiten entradas y descarta el destino que deja de ser válido', async () => {
    await crear(null, { origen: { tipo: 'ACTIVIDAD', id: 1 }, destino: { tipo: 'ACTIVIDAD', id: 3 } });

    expect(opciones('destino')).toEqual([
      'Elige el nodo de destino',
      'Evaluar solicitud',
      'Aprobar solicitud',
      'Gateway EXCLUSIVO #4',
      'Envío externo: Notificación de resultado',
    ]);
    await elegir('origen', 'Message Throw: Solicitud de servicio');

    expect(opciones('destino')).toEqual(['Elige el nodo de destino']);
    expect(elemento.textContent).toContain('No hay destinos válidos para este origen en su pool');
    await enviar();

    expect(crearApi).not.toHaveBeenCalled();
    expect(elemento.textContent).toContain('El nodo destino es obligatorio');
  });

  it('exige la condición cuando el origen es un gateway exclusivo', async () => {
    await crear(null, { origen: { tipo: 'GATEWAY', id: 4 }, destino: null });
    await elegir('destino', 'Evaluar solicitud');
    await enviar();

    expect(crearApi).not.toHaveBeenCalled();
    expect(elemento.textContent).toContain('Un arco que sale de un gateway exclusivo o inclusivo necesita condición');

    await escribir('condicion', 'Requiere revisión');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, expect.objectContaining({ origenTipo: 'GATEWAY', origenId: 4, condicion: 'Requiere revisión' }));
  });

  it('advierte del arco duplicado y no lo envía', async () => {
    await crear(null, { origen: { tipo: 'ACTIVIDAD', id: 2 }, destino: { tipo: 'GATEWAY', id: 4 } });

    expect(elemento.textContent).toContain('Ya existe el arco «Evaluar solicitud → Gateway EXCLUSIVO #4»');
    expect(elemento.querySelector<HTMLButtonElement>('button[type="submit"]')!.disabled).toBe(true);
    await enviar();

    expect(crearApi).not.toHaveBeenCalled();
  });

  it('edita extremos, etiqueta y condición de un arco existente', async () => {
    const original = diagramaCompleto().arcos.find((actual) => actual.id === 35)!;
    await crear(original, null);

    expect(elegido('origen')).toBe('Gateway EXCLUSIVO #4');
    expect(elegido('destino')).toBe('Envío externo: Notificación de resultado');
    expect(control<HTMLInputElement>('etiqueta').value).toBe('No procede');
    expect(control<HTMLInputElement>('condicion').value).toBe('Rechazada');
    await elegir('destino', 'Evaluar solicitud');
    await escribir('etiqueta', '');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 35, {
      origenTipo: 'GATEWAY',
      origenId: 4,
      destinoTipo: 'ACTIVIDAD',
      destinoId: 2,
      etiqueta: null,
      condicion: 'Rechazada',
    });
  });

  it('muestra junto al destino los rechazos de reglas BPMN y como aviso los demás', async () => {
    await crear(null, { origen: { tipo: 'ACTIVIDAD', id: 1 }, destino: { tipo: 'ACTIVIDAD', id: 3 } });
    crearApi.mockReturnValue(
      throwError(() => fallo('conflicto', 409, 'ARCO_DUPLICADO', 'Ya existe un arco activo entre ese origen y ese destino')),
    );
    await enviar();

    expect(control('destino').getAttribute('aria-invalid')).toBe('true');
    expect(elemento.textContent).toContain('Ya existe un arco activo entre ese origen y ese destino');

    crearApi.mockReturnValue(throwError(() => fallo('datos-no-validos', 400, 'NODO_NO_VALIDO', 'El nodo indicado fue eliminado del proceso')));
    await elegir('destino', 'Gateway EXCLUSIVO #4');
    await enviar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('El nodo indicado fue eliminado del proceso');
    expect(guardados).toEqual([]);
  });
});
