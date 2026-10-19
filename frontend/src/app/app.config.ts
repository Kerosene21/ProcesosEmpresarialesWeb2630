import { provideHttpClient, withFetch, withInterceptors, withXsrfConfiguration } from '@angular/common/http';
import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { catchError, of } from 'rxjs';

import { routes } from './app.routes';
import { sesionExpiradaInterceptor } from './core/http/sesion-expirada-interceptor';
import { SesionService } from './core/servicios/sesion-service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      withFetch(),
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
      withInterceptors([sesionExpiradaInterceptor]),
    ),
    provideAppInitializer(() =>
      inject(SesionService)
        .cargar()
        .pipe(catchError(() => of(null))),
    ),
  ],
};
