import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

interface Empresa {
  id: number;
  nombre: string;
  nit: string;
  correoContacto: string;
}

  @Component({
    selector: 'app-empresa-lista',
    standalone: true,
    imports: [CommonModule, RouterLink],
    templateUrl: './empresa-lista.html',
    styleUrl: './empresa-lista.css'
  })
  export class EmpresaLista {
    mensaje = '';

    empresas: Empresa[] = [
      {
        id: 1,
        nombre: 'Empresa de ejemplo S.A.S.',
        nit: '900123456',
        correoContacto: 'contacto@ejemplo.com'
      },
      {
        id: 2,
        nombre: 'Servicios empresariales Ltda.',
        nit: '800654321',
        correoContacto: 'servicios@ejemplo.com'
      }
    ];
  cerrarSesion(event: Event): void {
    event.preventDefault();

    
    console.log('Debemos conectar el cierre de sesión con Spring Boot.');
  }
  }
