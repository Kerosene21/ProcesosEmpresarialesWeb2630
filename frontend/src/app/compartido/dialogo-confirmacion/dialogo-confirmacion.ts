import { ChangeDetectionStrategy, Component, ElementRef, effect, input, output, viewChild } from '@angular/core';

let siguienteId = 0;

@Component({
  selector: 'app-dialogo-confirmacion',
  templateUrl: './dialogo-confirmacion.html',
  styleUrl: './dialogo-confirmacion.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoConfirmacion {
  readonly abierto = input.required<boolean>();
  readonly titulo = input.required<string>();
  readonly mensaje = input<string | null>(null);
  readonly textoConfirmar = input('Confirmar');
  readonly procesando = input(false);
  readonly error = input<string | null>(null);
  readonly confirmado = output<void>();
  readonly cancelado = output<void>();

  protected readonly id = `dialogo-${++siguienteId}`;
  private readonly botonCancelar = viewChild<ElementRef<HTMLButtonElement>>('botonCancelar');

  constructor() {
    effect(() => this.botonCancelar()?.nativeElement.focus());
  }

  protected cancelar(): void {
    if (!this.procesando()) {
      this.cancelado.emit();
    }
  }
}
