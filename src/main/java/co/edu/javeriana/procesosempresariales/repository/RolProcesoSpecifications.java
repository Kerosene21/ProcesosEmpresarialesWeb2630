package co.edu.javeriana.procesosempresariales.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadRolProceso;

@Component
public class RolProcesoSpecifications {

    public Specification<RolProceso> deLaEmpresaCon(Long empresaId, FiltroRolesProcesoDto filtro) {
        List<Specification<RolProceso>> especificaciones = new ArrayList<>();
        especificaciones.add(deLaEmpresa(empresaId));
        agregarSiAplica(especificaciones, conVisibilidad(filtro.getVisibilidad()));
        agregarSiAplica(especificaciones, conNombreParecidoA(filtro.getQ()));
        return Specification.allOf(especificaciones);
    }

    private void agregarSiAplica(List<Specification<RolProceso>> destino,
            Specification<RolProceso> especificacion) {
        if (especificacion != null) {
            destino.add(especificacion);
        }
    }

    private Specification<RolProceso> deLaEmpresa(Long empresaId) {
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("empresa").get("id"), empresaId);
    }

    private Specification<RolProceso> conVisibilidad(VisibilidadRolProceso visibilidad) {
        if (visibilidad == null || visibilidad == VisibilidadRolProceso.TODOS) {
            return null;
        }
        boolean activo = visibilidad == VisibilidadRolProceso.ACTIVOS;
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("activo"), activo);
    }

    private Specification<RolProceso> conNombreParecidoA(String nombre) {
        if (nombre == null) {
            return null;
        }
        String patron = "%" + nombre.toLowerCase(Locale.ROOT) + "%";
        return (raiz, consulta, constructor) -> constructor.like(constructor.lower(raiz.get("nombre")), patron);
    }
}
