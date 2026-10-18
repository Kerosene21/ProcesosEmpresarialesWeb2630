import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('muestra la marca, el enlace para saltar al contenido y la navegación principal', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const elemento = fixture.nativeElement as HTMLElement;

    expect(elemento.querySelector('.marca')?.textContent).toContain('Procesos Empresariales');
    expect(elemento.querySelector('.saltar')?.getAttribute('href')).toBe('#contenido');
    const enlaces = Array.from(elemento.querySelectorAll('nav[aria-label="Navegación principal"] a'));
    expect(enlaces.map((enlace) => enlace.getAttribute('href'))).toEqual(['/procesos', '/procesos/nuevo']);
    expect(elemento.querySelector('main#contenido router-outlet')).not.toBeNull();
  });
});
