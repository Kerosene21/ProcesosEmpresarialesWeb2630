package co.edu.javeriana.procesosempresariales.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.ProcesoCompartidoEmpresa;
import co.edu.javeriana.procesosempresariales.dto.AlcanceProceso;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;

@Component
public class ProcesoSpecifications {

    public Specification<Proceso> deLaEmpresaCon(Long empresaId, FiltroProcesosDto filtro) {
        List<Specification<Proceso>> especificaciones = new ArrayList<>();
        especificaciones.add(visiblesPara(empresaId, filtro.getAlcance()));
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

    private Specification<Proceso> visiblesPara(Long empresaId, AlcanceProceso alcance) {
        if (alcance == null || alcance == AlcanceProceso.PROPIOS) {
            return deLaEmpresa(empresaId);
        }
        if (alcance == AlcanceProceso.COMPARTIDOS) {
            return compartidosCon(empresaId);
        }
        return deLaEmpresa(empresaId).or(compartidosCon(empresaId));
    }

    private Specification<Proceso> deLaEmpresa(Long empresaId) {
        return (raiz, consulta, constructor) -> constructor.equal(raiz.get("empresa").get("id"), empresaId);
    }

    private Specification<Proceso> compartidosCon(Long empresaId) {
        return (raiz, consulta, constructor) -> {
            Subquery<Long> compartido = consulta.subquery(Long.class);
            Root<ProcesoCompartidoEmpresa> comparticion = compartido.from(ProcesoCompartidoEmpresa.class);
            compartido.select(comparticion.get("id")).where(
                    constructor.equal(comparticion.get("proceso"), raiz),
                    constructor.equal(comparticion.get("empresaInvitada").get("id"), empresaId),
                    constructor.isTrue(comparticion.get("activo")));
            return constructor.and(constructor.exists(compartido), constructor.isFalse(raiz.get("eliminado")));
        };
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
