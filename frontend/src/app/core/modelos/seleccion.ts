export type TipoElemento = 'ACTIVIDAD' | 'GATEWAY' | 'ARCO' | 'MESSAGE_THROW' | 'MESSAGE_CATCH' | 'ENVIO_EXTERNO';

export interface Seleccion {
  tipo: TipoElemento;
  id: number;
}
