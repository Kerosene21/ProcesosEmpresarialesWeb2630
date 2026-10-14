import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmpresaFormulario } from './empresa-formulario';

describe('EmpresaFormulario', () => {
  let component: EmpresaFormulario;
  let fixture: ComponentFixture<EmpresaFormulario>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmpresaFormulario],
    }).compileComponents();

    fixture = TestBed.createComponent(EmpresaFormulario);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
