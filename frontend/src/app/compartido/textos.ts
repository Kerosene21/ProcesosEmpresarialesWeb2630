import {
  ComportamientoFallo,
  ComportamientoSinCaso,
  TipoActividad,
  TipoDestinoExterno,
  TipoGateway,
  TipoPool,
  VarianteMessageCatch,
} from '../core/modelos/diagrama';
import { FalloHttp, TipoFallo } from '../core/modelos/fallo-http';
import { AlcanceProceso, EstadoProceso, VisibilidadProceso } from '../core/modelos/proceso';
import { AccionRolProceso, VisibilidadRolProceso } from '../core/modelos/rol-proceso';
import { TipoElemento } from '../core/modelos/seleccion';
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

export const TEXTO_TIPO_GATEWAY: Record<TipoGateway, string> = {
  EXCLUSIVO: 'Exclusivo',
  PARALELO: 'Paralelo',
  INCLUSIVO: 'Inclusivo',
};

export const TEXTO_TIPO_ELEMENTO: Record<TipoElemento, string> = {
  ACTIVIDAD: 'Actividad',
  GATEWAY: 'Gateway',
  ARCO: 'Arco',
  MESSAGE_THROW: 'Message Throw',
  MESSAGE_CATCH: 'Message Catch',
  ENVIO_EXTERNO: 'Envío externo',
};

export const TEXTO_VARIANTE_CATCH: Record<VarianteMessageCatch, string> = {
  INICIO: 'De inicio',
  INTERMEDIO: 'Intermedio',
};

export const TEXTO_DESTINO_EXTERNO: Record<TipoDestinoExterno, string> = {
  CORREO: 'Correo',
  SERVICIO_WEB: 'Servicio web',
  COLA: 'Cola de mensajes',
};

export const TEXTO_SIN_CASO: Record<ComportamientoSinCaso, string> = {
  DESCARTAR: 'Descartar el mensaje',
  INICIAR_NUEVO_CASO: 'Iniciar un caso nuevo',
};

export const TEXTO_FALLO_ENVIO: Record<ComportamientoFallo, string> = {
  CONTINUAR: 'Continuar',
  RUTA_ERROR: 'Seguir la ruta de error',
  FINALIZAR: 'Finalizar',
};

export const TEXTO_VISIBILIDAD_ROL: Record<VisibilidadRolProceso, string> = {
  ACTIVOS: 'Activos',
  INACTIVOS: 'Eliminados',
  TODOS: 'Activos y eliminados',
};

export const TEXTO_ACCION_ROL: Record<AccionRolProceso, string> = {
  CREACION: 'Creación',
  EDICION: 'Edición',
  ELIMINACION: 'Eliminación',
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
