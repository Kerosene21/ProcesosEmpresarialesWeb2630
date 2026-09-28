package co.edu.javeriana.procesosempresariales.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
import jakarta.persistence.criteria.Subquery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.ProcesoCompartidoEmpresa;
import co.edu.javeriana.procesosempresariales.dto.AlcanceProceso;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcesoSpecificationsTest {

    private static final Long EMPRESA = 7L;

    private final ProcesoSpecifications procesoSpecifications = new ProcesoSpecifications();

    @Mock
    private Root<Proceso> raiz;

    @Mock
    private CriteriaQuery<?> consulta;

    @Mock
    private CriteriaBuilder constructor;

    @Mock
    private Path<Object> caminoEmpresa;

    @Mock
    private Path<Object> caminoEmpresaId;

    @Mock
    private Path<Object> caminoEliminado;

    @Mock
    private Path<Object> caminoEstado;

    @Mock
    private Path<String> caminoNombre;

    @Mock
    private Path<String> caminoCategoria;

    @Mock
    private Expression<String> enMinusculas;

    @Mock
    private Predicate predicado;

    @Mock
    private Subquery<Long> subconsulta;

    @Mock
    private Root<ProcesoCompartidoEmpresa> comparticion;

    @Mock
    private Path<Long> caminoComparticionId;

    @Mock
    private Path<Object> caminoProceso;

    @Mock
    private Path<Object> caminoEmpresaInvitada;

    @Mock
    private Path<Object> caminoEmpresaInvitadaId;

    @Mock
    private Path<Boolean> caminoActivo;

    @BeforeEach
    void inicializar() {
        when(raiz.get("empresa")).thenReturn(caminoEmpresa);
        when(caminoEmpresa.get("id")).thenReturn(caminoEmpresaId);
        when(raiz.get("eliminado")).thenReturn(caminoEliminado);
        when(raiz.get("estado")).thenReturn(caminoEstado);
        when(raiz.<String>get("nombre")).thenReturn(caminoNombre);
        when(raiz.<String>get("categoria")).thenReturn(caminoCategoria);
        when(constructor.lower(any())).thenReturn(enMinusculas);
        when(constructor.equal(any(), any())).thenReturn(predicado);
        when(constructor.like(any(), anyString())).thenReturn(predicado);
        when(constructor.and(any(), any())).thenReturn(predicado);
        when(constructor.conjunction()).thenReturn(predicado);
    }

    private FiltroProcesosDto filtro() {
        FiltroProcesosDto filtro = new FiltroProcesosDto();
        filtro.setVisibilidad(VisibilidadProceso.ACTIVOS);
        return filtro;
    }

    private void construir(FiltroProcesosDto filtro) {
        procesoSpecifications.deLaEmpresaCon(EMPRESA, filtro).toPredicate(raiz, consulta, constructor);
    }

    @Test
    void laConsultaSiempreRestringePorLaEmpresaIndicada() {
        construir(filtro());

        verify(constructor).equal(caminoEmpresaId, EMPRESA);
    }

    @Test
    void laVisibilidadDeActivosBuscaProcesosNoEliminados() {
        construir(filtro());

        verify(constructor).equal(caminoEliminado, false);
    }

    @Test
    void laVisibilidadDeInactivosBuscaProcesosEliminados() {
        FiltroProcesosDto filtro = filtro();
        filtro.setVisibilidad(VisibilidadProceso.INACTIVOS);

        construir(filtro);

        verify(constructor).equal(caminoEliminado, true);
    }

    @Test
    void laVisibilidadDeTodosNoRestringePorElIndicadorDeEliminado() {
        FiltroProcesosDto filtro = filtro();
        filtro.setVisibilidad(VisibilidadProceso.TODOS);

        construir(filtro);

        verify(constructor, never()).equal(eq(caminoEliminado), any());
    }

    @Test
    void unaVisibilidadNulaTampocoRestringePorElIndicadorDeEliminado() {
        FiltroProcesosDto filtro = new FiltroProcesosDto();

        construir(filtro);

        verify(constructor, never()).equal(eq(caminoEliminado), any());
        verify(constructor).equal(caminoEmpresaId, EMPRESA);
    }

    @Test
    void laBusquedaPorNombreComparaEnMinusculasYPorCoincidenciaParcial() {
        FiltroProcesosDto filtro = filtro();
        filtro.setQ("VenTa");

        construir(filtro);

        verify(constructor).lower(caminoNombre);
        verify(constructor).like(enMinusculas, "%venta%");
    }

    @Test
    void sinTextoDeBusquedaNoSeAgregaNingunaCondicionDeNombre() {
        construir(filtro());

        verify(constructor, never()).like(any(), anyString());
    }

    @Test
    void elFiltroDeEstadoComparaElValorDelEnumerado() {
        FiltroProcesosDto filtro = filtro();
        filtro.setEstado(EstadoProceso.PUBLICADO);

        construir(filtro);

        verify(constructor).equal(caminoEstado, EstadoProceso.PUBLICADO);
    }

    @Test
    void sinFiltroDeEstadoNoSeRestringeElEstado() {
        construir(filtro());

        verify(constructor, never()).equal(eq(caminoEstado), any());
    }

    @Test
    void elFiltroDeCategoriaComparaEnMinusculas() {
        FiltroProcesosDto filtro = filtro();
        filtro.setCategoria("Comercial");

        construir(filtro);

        verify(constructor).lower(caminoCategoria);
        verify(constructor).equal(enMinusculas, "comercial");
    }

    @Test
    void sinFiltroDeCategoriaNoSeRestringeLaCategoria() {
        construir(filtro());

        verify(constructor, never()).equal(eq(enMinusculas), anyString());
    }

    @Test
    void todosLosFiltrosSeCombinanEnUnaSolaConsulta() {
        FiltroProcesosDto filtro = filtro();
        filtro.setQ("venta");
        filtro.setEstado(EstadoProceso.PUBLICADO);
        filtro.setCategoria("Comercial");

        construir(filtro);

        verify(constructor).equal(caminoEmpresaId, EMPRESA);
        verify(constructor).equal(caminoEliminado, false);
        verify(constructor).like(enMinusculas, "%venta%");
        verify(constructor).equal(caminoEstado, EstadoProceso.PUBLICADO);
        verify(constructor).equal(enMinusculas, "comercial");
    }

    @Test
    void laEspecificacionResultanteNoEsNula() {
        assertThat(procesoSpecifications.deLaEmpresaCon(EMPRESA, filtro())).isNotNull();
    }
    private void prepararSubconsultaDeComparticion() {
        when(consulta.subquery(Long.class)).thenReturn(subconsulta);
        when(subconsulta.from(ProcesoCompartidoEmpresa.class)).thenReturn(comparticion);
        when(subconsulta.select(any())).thenReturn(subconsulta);
        when(comparticion.<Long>get("id")).thenReturn(caminoComparticionId);
        when(comparticion.get("proceso")).thenReturn(caminoProceso);
        when(comparticion.get("empresaInvitada")).thenReturn(caminoEmpresaInvitada);
        when(caminoEmpresaInvitada.get("id")).thenReturn(caminoEmpresaInvitadaId);
        when(comparticion.<Boolean>get("activo")).thenReturn(caminoActivo);
        when(constructor.isTrue(any())).thenReturn(predicado);
        when(constructor.isFalse(any())).thenReturn(predicado);
        when(constructor.exists(any())).thenReturn(predicado);
        when(constructor.or(any(Predicate.class), any(Predicate.class))).thenReturn(predicado);
    }

    @Test
    void sinAlcanceSoloSeConsultanLosProcesosPropios() {
        construir(filtro());

        verify(constructor).equal(caminoEmpresaId, EMPRESA);
        verify(consulta, never()).subquery(Long.class);
    }

    @Test
    void elAlcanceDePropiosNoConsultaComparticiones() {
        FiltroProcesosDto filtro = filtro();
        filtro.setAlcance(AlcanceProceso.PROPIOS);

        construir(filtro);

        verify(constructor).equal(caminoEmpresaId, EMPRESA);
        verify(consulta, never()).subquery(Long.class);
    }

    @Test
    void elAlcanceDeCompartidosBuscaComparticionesActivasConLaEmpresaYProcesosNoEliminados() {
        prepararSubconsultaDeComparticion();
        FiltroProcesosDto filtro = filtro();
        filtro.setAlcance(AlcanceProceso.COMPARTIDOS);

        construir(filtro);

        verify(constructor).equal(caminoProceso, raiz);
        verify(constructor).equal(caminoEmpresaInvitadaId, EMPRESA);
        verify(constructor).isTrue(caminoActivo);
        verify(constructor).exists(subconsulta);
        verify(constructor).isFalse(argThat(expresion -> caminoEliminado.equals(expresion)));
        verify(subconsulta).select(caminoComparticionId);
        verify(constructor, never()).equal(caminoEmpresaId, EMPRESA);
    }

    @Test
    void elAlcanceDeTodosUneLosProcesosPropiosYLosCompartidos() {
        prepararSubconsultaDeComparticion();
        when(constructor.equal(caminoEmpresaId, EMPRESA)).thenReturn(predicado);
        FiltroProcesosDto filtro = filtro();
        filtro.setAlcance(AlcanceProceso.TODOS);

        construir(filtro);

        verify(constructor).equal(caminoEmpresaId, EMPRESA);
        verify(constructor).equal(caminoEmpresaInvitadaId, EMPRESA);
        verify(constructor).exists(subconsulta);
        verify(constructor).or(any(Predicate.class), any(Predicate.class));
    }
}
