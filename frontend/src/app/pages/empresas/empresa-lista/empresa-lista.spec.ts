import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmpresaLista } from './empresa-lista';

describe('EmpresaLista', () => {
  let component: EmpresaLista;
  let fixture: ComponentFixture<EmpresaLista>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmpresaLista],
    }).compileComponents();

    fixture = TestBed.createComponent(EmpresaLista);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
