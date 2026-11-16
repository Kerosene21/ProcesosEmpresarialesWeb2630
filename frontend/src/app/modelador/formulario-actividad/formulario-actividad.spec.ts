import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { actividad, diagramaCompleto } from '../../../testing/diagramas';
import { CrearActividad, EditarActividad } from '../../core/modelos/actividad';
import { ActividadRespuesta, DiagramaProceso } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { ActividadService } from '../../core/servicios/actividad-service';
import { calcularGeometria } from '../geometria';
import { FormularioActividad, PropuestaActividad } from './formulario-actividad';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('FormularioActividad', () => {
  let fixture: ComponentFixture<FormularioActividad>;
  let elemento: HTMLElement;
  let guardadas: ActividadRespuesta[];
  let cancelaciones: number;
  const crearApi = vi.fn<(procesoId: number, datos: CrearActividad) => Observable<ActividadRespuesta>>();
  const editarApi = vi.fn<(procesoId: number, id: number, datos: EditarActividad) => Observable<ActividadRespuesta>>();

  async function crear(
    actual: ActividadRespuesta | null,
    propuesta: PropuestaActividad | null,
    diagrama: DiagramaProceso = diagramaCompleto(),
  ): Promise<void> {
    crearApi.mockReset();
    editarApi.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: ActividadService, useValue: { crear: crearApi, editar: editarApi } }],
    });
    fixture = TestBed.createComponent(FormularioActividad);
    elemento = fixture.nativeElement;
    guardadas = [];
    cancelaciones = 0;
    fixture.componentInstance.guardado.subscribe((guardada) => guardadas.push(guardada));
    fixture.componentInstance.cancelado.subscribe(() => cancelaciones++);
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('actividad', actual);
    fixture.componentRef.setInput('propuesta', propuesta);
    await fixture.whenStable();
  }

  function control<T extends HTMLElement>(nombre: string): T {
    return elemento.querySelector<T>(`[formControlName="${nombre}"]`)!;
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

  it('crea la actividad en la lane y la posición propuestas por el lienzo', async () => {
    const creada = actividad(9, 'Archivar solicitud', 12, 235, 187);
    await crear(null, { laneId: 12, posicion: { x: 235, y: 187 } });
    crearApi.mockReturnValue(of(creada));

    expect(control<HTMLSelectElement>('laneId').selectedOptions[0].textContent?.trim()).toBe('Supervisor');
    expect(control<HTMLInputElement>('posicionX').value).toBe('235');
    await escribir('nombre', '  Archivar solicitud ');
    await elegir('tipo', 'Tarea manual');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, {
      nombre: 'Archivar solicitud',
      tipo: 'TAREA_MANUAL',
      laneId: 12,
      posicionX: 235,
      posicionY: 187,
    });
    expect(guardadas).toEqual([creada]);
  });

  it('agrupa las lanes por pool y ajusta la posición al cambiar de lane', async () => {
    await crear(null, { laneId: 12, posicion: { x: 235, y: 187 } });

    expect(Array.from(elemento.querySelectorAll('optgroup')).map((grupo) => grupo.label)).toEqual(['Empresa de prueba']);
    await elegir('laneId', 'General');

    expect(control<HTMLInputElement>('posicionX').value).toBe('235');
    expect(control<HTMLInputElement>('posicionY').value).toBe('74');
  });

  it('no envía la solicitud si faltan datos obligatorios y muestra los mensajes del DTO', async () => {
    await crear(null, { laneId: 11, posicion: { x: 20, y: 47 } });
    await escribir('nombre', '   ');
    await escribir('posicionX', '');
    await enviar();

    expect(crearApi).not.toHaveBeenCalled();
    expect(elemento.textContent).toContain('El nombre es obligatorio');
    expect(elemento.textContent).toContain('El tipo de actividad es obligatorio');
    expect(elemento.textContent).toContain('La posición X es obligatoria');
  });

  it('muestra el nombre duplicado junto al campo y los rechazos de permisos como aviso', async () => {
    await crear(null, { laneId: 11, posicion: { x: 20, y: 47 } });
    await escribir('nombre', 'Registrar solicitud');
    await elegir('tipo', 'Tarea de usuario');
    crearApi.mockReturnValue(
      throwError(() => fallo('conflicto', 409, 'ACTIVIDAD_NOMBRE_DUPLICADO', 'Ya existe una actividad con ese nombre en el proceso')),
    );
    await enviar();

    expect(control('nombre').getAttribute('aria-invalid')).toBe('true');
    expect(elemento.textContent).toContain('Ya existe una actividad con ese nombre en el proceso');
    expect(guardadas).toEqual([]);

    crearApi.mockReturnValue(
      throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'Solo un administrador o editor puede crear o modificar actividades')),
    );
    await escribir('nombre', 'Otra actividad');
    await enviar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Solo un administrador o editor');
  });

  it('al cambiar de lane envía en la misma petición la posición dentro de la nueva lane', async () => {
    const original = diagramaCompleto().actividades[0];
    await crear(original, null);
    editarApi.mockReturnValue(of({ ...original, laneId: 12, laneNombre: 'Supervisor', posicionY: 200 }));

    expect(control<HTMLInputElement>('nombre').value).toBe('Registrar solicitud');
    expect(control('posicionX')).toBeNull();
    expect(elemento.textContent).toContain('Posición: x 160, y 60');
    expect(elemento.textContent).toContain('arrastra la actividad en el lienzo');
    await elegir('laneId', 'Supervisor');

    expect(elemento.textContent).toContain('Posición: x 160, y 200');
    expect(elemento.textContent).toContain('se recolocará dentro de la nueva lane');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 1, {
      nombre: 'Registrar solicitud',
      tipo: 'TAREA_USUARIO',
      laneId: 12,
      posicionX: 160,
      posicionY: 200,
    });
    expect(guardadas[0].laneId).toBe(12);
  });

  it('al volver a la lane original o editar solo el nombre conserva la posición sin enviarla', async () => {
    const original = diagramaCompleto().actividades[2];
    await crear(original, null);
    editarApi.mockReturnValue(of(original));

    await elegir('laneId', 'General');
    expect(elemento.textContent).toContain('Posición: x 620, y 60');
    await elegir('laneId', 'Supervisor');
    expect(elemento.textContent).toContain('Posición: x 620, y 200');
    await escribir('nombre', 'Aprobar solicitud urgente');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 3, { nombre: 'Aprobar solicitud urgente', tipo: 'TAREA_USUARIO', laneId: 12 });
  });

  it('muestra junto a la lane el rechazo de pasar a otro pool con arcos conectados', async () => {
    const original = diagramaCompleto().actividades[0];
    await crear(original, null);
    editarApi.mockReturnValue(
      throwError(() =>
        fallo('datos-no-validos', 400, 'SECUENCIA_ENTRE_POOLS', "'Registrar solicitud' tiene 2 arcos conectados"),
      ),
    );

    await elegir('laneId', 'Supervisor');
    await enviar();

    expect(elemento.querySelector('[id$="-error-lane"]')?.textContent).toContain('tiene 2 arcos conectados');
  });

  it('cancela sin llamar a la API', async () => {
    await crear(null, { laneId: 11, posicion: { x: 20, y: 47 } });

    Array.from(elemento.querySelectorAll('button')).find((boton) => boton.textContent?.trim() === 'Cancelar')!.click();

    expect(cancelaciones).toBe(1);
    expect(crearApi).not.toHaveBeenCalled();
  });
});
