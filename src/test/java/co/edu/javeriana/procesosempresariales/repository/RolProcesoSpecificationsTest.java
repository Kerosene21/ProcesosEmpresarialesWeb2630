package co.edu.javeriana.procesosempresariales.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.edu.javeriana.procesosempresariales.domain.RolProceso;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadRolProceso;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RolProcesoSpecificationsTest {

    private static final Long EMPRESA = 7L;

    private final RolProcesoSpecifications rolProcesoSpecifications = new RolProcesoSpecifications();

    @Mock
    private Root<RolProceso> raiz;

    @Mock
    private CriteriaQuery<?> consulta;

    @Mock
    private CriteriaBuilder constructor;

    @Mock
    private Path<Object> caminoEmpresa;

    @Mock
    private Path<Object> caminoEmpresaId;

    @Mock
    private Path<Object> caminoActivo;

    @Mock
    private Path<String> caminoNombre;

    @Mock
    private Expression<String> enMinusculas;

    @Mock
    private Predicate predicado;

    @BeforeEach
    void inicializar() {
        when(raiz.get("empresa")).thenReturn(caminoEmpresa);
        when(caminoEmpresa.get("id")).thenReturn(caminoEmpresaId);
        when(raiz.get("activo")).thenReturn(caminoActivo);
        when(raiz.<String>get("nombre")).thenReturn(caminoNombre);
        when(constructor.lower(any())).thenReturn(enMinusculas);
        when(constructor.equal(any(), any())).thenReturn(predicado);
        when(constructor.like(any(), anyString())).thenReturn(predicado);
        when(constructor.and(any(), any())).thenReturn(predicado);
        when(constructor.conjunction()).thenReturn(predicado);
    }

    private FiltroRolesProcesoDto filtro(String q, VisibilidadRolProceso visibilidad) {
        return new FiltroRolesProcesoDto(q, visibilidad, 0);
    }

    private void construir(FiltroRolesProcesoDto filtro) {
        rolProcesoSpecifications.deLaEmpresaCon(EMPRESA, filtro).toPredicate(raiz, consulta, constructor);
    }

    @Test
    void laConsultaSiempreRestringePorLaEmpresaIndicada() {
        construir(filtro(null, VisibilidadRolProceso.ACTIVOS));

        verify(constructor).equal(caminoEmpresaId, EMPRESA);
    }

    @Test
    void laVisibilidadDeActivosBuscaRolesActivos() {
        construir(filtro(null, VisibilidadRolProceso.ACTIVOS));

        verify(constructor).equal(caminoActivo, true);
    }

    @Test
    void laVisibilidadDeInactivosBuscaRolesEliminados() {
        construir(filtro(null, VisibilidadRolProceso.INACTIVOS));

        verify(constructor).equal(caminoActivo, false);
    }

    @Test
    void laVisibilidadDeTodosNoRestringePorElIndicadorDeActivo() {
        construir(filtro(null, VisibilidadRolProceso.TODOS));

        verify(constructor, never()).equal(eq(caminoActivo), any());
        verify(constructor).equal(caminoEmpresaId, EMPRESA);
    }

    @Test
    void unaVisibilidadNulaTampocoRestringePorElIndicadorDeActivo() {
        construir(filtro(null, null));

        verify(constructor, never()).equal(eq(caminoActivo), any());
    }

    @Test
    void laBusquedaPorNombreComparaEnMinusculasYPorCoincidenciaParcial() {
        construir(filtro("AnaLiSta", VisibilidadRolProceso.ACTIVOS));

        verify(constructor).lower(caminoNombre);
        verify(constructor).like(enMinusculas, "%analista%");
    }

    @Test
    void sinTextoDeBusquedaNoSeAgregaNingunaCondicionDeNombre() {
        construir(filtro(null, VisibilidadRolProceso.ACTIVOS));

        verify(constructor, never()).like(any(), anyString());
    }

    @Test
    void laEspecificacionResultanteNoEsNula() {
        assertThat(rolProcesoSpecifications.deLaEmpresaCon(EMPRESA, filtro(null, null))).isNotNull();
    }
}
