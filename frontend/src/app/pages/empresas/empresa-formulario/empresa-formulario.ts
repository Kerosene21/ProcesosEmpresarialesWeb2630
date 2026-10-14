
import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-empresa-formulario',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  templateUrl: './empresa-formulario.html',
  styleUrl: './empresa-formulario.css'
})
export class EmpresaFormulario {
  private readonly fb = inject(FormBuilder);

  enviado = false;
  mensaje = '';
  tipoMensaje: 'exito' | 'error' | '' = '';

  empresaForm = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    nit: ['', [Validators.required, Validators.maxLength(20)]],
    correoContacto: [
      '',
      [
        Validators.required,
        Validators.email,
        Validators.maxLength(180)
      ]
    ],
    passwordAdministrador: [
      '',
      [Validators.required, Validators.maxLength(100)]
    ]
  });

  get campos() {
    return this.empresaForm.controls;
  }

  registrarEmpresa(): void {
    this.enviado = true;
    this.mensaje = '';
    this.tipoMensaje = '';

    if (this.empresaForm.invalid) {
      this.empresaForm.markAllAsTouched();
      this.mensaje = 'Revisa los campos del formulario antes de continuar.';
      this.tipoMensaje = 'error';
      return;
    }

    // Registro temporal: todavía no existe conexión con Spring Boot.
    // No se guarda la información ni se crea un administrador real.
    console.log('Formulario válido. Registro todavía no conectado.');

    this.mensaje =
      'Formulario validado correctamente. El registro real estará disponible cuando conectemos el backend.';
    this.tipoMensaje = 'exito';
  }
}
