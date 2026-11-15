import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { TEXTO_ROL } from './compartido/textos';
import { ROLES_ESCRITURA } from './core/modelos/sesion';
import { RUTA_INICIO_SESION } from './core/seguridad/sesion-guard';
import { SesionService } from './core/servicios/sesion-service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly sesiones = inject(SesionService);
  private readonly router = inject(Router);

  protected readonly sesion = this.sesiones.sesion;
  protected readonly autenticado = this.sesiones.autenticado;
  protected readonly puedeCrear = computed(() => this.sesiones.tieneRol(ROLES_ESCRITURA));
  protected readonly esAdministrador = computed(() => this.sesiones.tieneRol(['ADMINISTRADOR']));
  protected readonly textoRol = TEXTO_ROL;
  protected readonly cerrando = signal(false);

  constructor() {
    this.sesiones.cierreEnOtraPestana
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.irAInicioDeSesion('otra-pestana'));
  }

  protected cerrarSesion(): void {
    this.cerrando.set(true);
    this.sesiones.cerrar().subscribe({
      next: () => this.terminarCierre('cerrada'),
      error: () => this.terminarCierre('cierre-sin-confirmar'),
    });
  }

  private terminarCierre(motivo: string): void {
    this.cerrando.set(false);
    this.irAInicioDeSesion(motivo);
  }

  private irAInicioDeSesion(motivo: string): void {
    void this.router.navigate([RUTA_INICIO_SESION], { queryParams: { motivo } });
  }
}
