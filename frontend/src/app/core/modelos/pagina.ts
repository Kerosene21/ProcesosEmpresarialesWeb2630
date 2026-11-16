export interface MetadatosPagina {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export interface Pagina<T> {
  content: T[];
  page: MetadatosPagina;
}
