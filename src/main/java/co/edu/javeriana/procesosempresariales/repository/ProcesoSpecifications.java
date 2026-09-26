package co.edu.javeriana.procesosempresariales.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;

@Component
public class ProcesoSpecifications {

    public Specification<Proceso> deLaEmpresaCon(Long empresaId, FiltroProcesosDto filtro) {
        List<Specification<Proceso>> especificaciones = new ArrayList<>();
        especificaciones.add(deLaEmpresa(empresaId));
        agregarSiAplica(especificaciones, conVisibilidad(filtro.getVisibilidad()));
        agregarSiAplica(especificaciones, conNombreParecidoA(filtro.getQ()));
        agregarSiAplica(especificaciones, conEstado(filtro.getEstado()));
        agregarSiAplica(especificaciones, conCategoria(filtro.getCategoria()));
        return Specification.allOf(especificaciones);
    }

    private void agregarSiAplica(List<Specification<Proceso>> destino, Specification<Proceso> especificacion) {
        if (especificacion != null) {
            destino.add(especificacion);
        }
    }

    private Specification<Proceso> deLaEmpresa(Long empresaId) {
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("empresa").get("id"), empresaId);
    }

    private Specification<Proceso> conVisibilidad(VisibilidadProceso visibilidad) {
        if (visibilidad == null || visibilidad == VisibilidadProceso.TODOS) {
            return null;
        }
        boolean eliminado = visibilidad == VisibilidadProceso.INACTIVOS;
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("eliminado"), eliminado);
    }

    private Specification<Proceso> conNombreParecidoA(String nombre) {
        if (nombre == null) {
            return null;
        }
        String patron = "%" + nombre.toLowerCase(Locale.ROOT) + "%";
        return (raiz, consulta, constructor) -> constructor.like(constructor.lower(raiz.get("nombre")), patron);
    }

    private Specification<Proceso> conEstado(EstadoProceso estado) {
        if (estado == null) {
            return null;
        }
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("estado"), estado);
    }

    private Specification<Proceso> conCategoria(String categoria) {
        if (categoria == null) {
            return null;
        }
        String valor = categoria.toLowerCase(Locale.ROOT);
        return (raiz, consulta, constructor) -> constructor.equal(constructor.lower(raiz.get("categoria")), valor);
    }
}
