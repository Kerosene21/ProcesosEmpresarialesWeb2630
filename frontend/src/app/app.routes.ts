
import { Routes } from '@angular/router';

import { EmpresaLista } from './pages/empresas/empresa-lista/empresa-lista';
import { EmpresaFormulario } from './pages/empresas/empresa-formulario/empresa-formulario';

export const routes: Routes = [
  {
    path: 'empresas',
    component: EmpresaLista
  },
  {
    path: 'empresas/nueva',
    component: EmpresaFormulario
  },
  {
    path: '',
    redirectTo: 'empresas',
    pathMatch: 'full'
  }
];
