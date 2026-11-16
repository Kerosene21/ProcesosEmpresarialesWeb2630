import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EtiquetaEstado } from './etiqueta-estado';

describe('EtiquetaEstado', () => {
  let fixture: ComponentFixture<EtiquetaEstado>;

  beforeEach(() => {
    fixture = TestBed.createComponent(EtiquetaEstado);
  });

  it('presenta un borrador como advertencia', async () => {
    fixture.componentRef.setInput('estado', 'BORRADOR');
    await fixture.whenStable();

    const chip: HTMLElement = fixture.nativeElement.querySelector('.chip');
    expect(chip.textContent?.trim()).toBe('Borrador');
    expect(chip.classList).toContain('chip--advertencia');
  });

  it('presenta un proceso publicado como éxito', async () => {
    fixture.componentRef.setInput('estado', 'PUBLICADO');
    await fixture.whenStable();

    const chip: HTMLElement = fixture.nativeElement.querySelector('.chip');
    expect(chip.textContent?.trim()).toBe('Publicado');
    expect(chip.classList).toContain('chip--exito');
  });
});
