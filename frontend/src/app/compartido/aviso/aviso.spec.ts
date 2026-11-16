import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Aviso } from './aviso';

describe('Aviso', () => {
  let fixture: ComponentFixture<Aviso>;
  let elemento: HTMLElement;

  beforeEach(() => {
    fixture = TestBed.createComponent(Aviso);
    elemento = fixture.nativeElement;
  });

  it('anuncia los errores como alerta y emite la acción solicitada', async () => {
    let solicitudes = 0;
    fixture.componentInstance.accionSolicitada.subscribe(() => solicitudes++);
    fixture.componentRef.setInput('tipo', 'error');
    fixture.componentRef.setInput('titulo', 'No hay conexión con el servidor');
    fixture.componentRef.setInput('accion', 'Reintentar');
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No hay conexión con el servidor');
    elemento.querySelector<HTMLButtonElement>('button')!.click();
    expect(solicitudes).toBe(1);
  });

  it('usa un rol de estado y no muestra acción cuando no se indica', async () => {
    fixture.componentRef.setInput('titulo', 'Cargando procesos…');
    fixture.componentRef.setInput('tipo', 'carga');
    await fixture.whenStable();

    expect(elemento.querySelector('[role="status"]')).not.toBeNull();
    expect(elemento.querySelector('.aviso__indicador')).not.toBeNull();
    expect(elemento.querySelector('button')).toBeNull();
  });
});
