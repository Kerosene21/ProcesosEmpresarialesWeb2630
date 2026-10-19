import { TipoActividad, TipoPool } from '../core/modelos/diagrama';
import { FalloHttp, TipoFallo } from '../core/modelos/fallo-http';
import { AlcanceProceso, EstadoProceso, VisibilidadProceso } from '../core/modelos/proceso';
import { RolUsuario } from '../core/modelos/sesion';

export const TEXTO_ROL: Record<RolUsuario, string> = {
  ADMINISTRADOR: 'Administrador',
  EDITOR: 'Editor',
  SOLO_LECTURA: 'Solo lectura',
};

export const TEXTO_ESTADO: Record<EstadoProceso, string> = {
  BORRADOR: 'Borrador',
  PUBLICADO: 'Publicado',
};

export const TEXTO_VISIBILIDAD: Record<VisibilidadProceso, string> = {
  ACTIVOS: 'Activos',
  INACTIVOS: 'Eliminados',
  TODOS: 'Activos y eliminados',
};

export const TEXTO_ALCANCE: Record<AlcanceProceso, string> = {
  PROPIOS: 'De mi empresa',
  COMPARTIDOS: 'Compartidos conmigo',
  TODOS: 'Propios y compartidos',
};

export const TEXTO_TIPO_POOL: Record<TipoPool, string> = {
  PROPIETARIO: 'Propietario',
  PARTICIPANTE: 'Participante',
  EXTERNO: 'Externo',
};

export const TEXTO_TIPO_ACTIVIDAD: Record<TipoActividad, string> = {
  TAREA_USUARIO: 'Tarea de usuario',
  TAREA_SISTEMA: 'Tarea de sistema',
  TAREA_MANUAL: 'Tarea manual',
  TAREA_SERVICIO: 'Tarea de servicio',
};

const EXPLICACION_FALLO: Record<TipoFallo, string> = {
  conexion: 'Comprueba que el backend esté en ejecución y que el proxy de desarrollo apunte a él.',
  'no-autenticado': 'La sesión no está iniciada o expiró. Inicia sesión para continuar.',
  'sin-permiso': 'El recurso pertenece a otra empresa o tu rol no permite consultarlo.',
  'no-encontrado': 'Puede que no exista o que haya sido eliminado.',
  'datos-no-validos': 'Revisa los valores enviados.',
  conflicto: 'Actualiza la información e inténtalo de nuevo.',
  servidor: 'Comprueba que el backend esté en ejecución en el puerto configurado.',
  desconocido: 'Inténtalo de nuevo más tarde.',
};

export function explicarFallo(fallo: FalloHttp): string {
  const codigo = fallo.codigo ? ` Código: ${fallo.codigo}.` : '';
  return `${EXPLICACION_FALLO[fallo.tipo]}${codigo}`;
}
