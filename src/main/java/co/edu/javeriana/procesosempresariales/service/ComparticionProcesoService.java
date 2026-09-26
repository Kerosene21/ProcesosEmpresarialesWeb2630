package co.edu.javeriana.procesosempresariales.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.Empresa;
import co.edu.javeriana.procesosempresariales.domain.Proceso;
import co.edu.javeriana.procesosempresariales.domain.ProcesoCompartidoEmpresa;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaInvitadaDto;
import co.edu.javeriana.procesosempresariales.exception.ComparticionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ProcesoYaCompartidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.repository.ProcesoCompartidoEmpresaRepository;

@Service
public class ComparticionProcesoService {

    static final String SIN_PERMISO =
            "Solo un administrador de la empresa propietaria puede cambiar con quién se comparte el proceso";
    static final String CON_SU_PROPIETARIA = "Un proceso no se comparte con su propia empresa";
    static final String YA_COMPARTIDO = "El proceso ya está compartido con esa empresa";
    static final String NO_COMPARTIDO = "El proceso no está compartido con esa empresa";

    private ProcesoCompartidoEmpresaRepository procesoCompartidoEmpresaRepository;
    private AccesoProcesoService accesoProcesoService;
    private EmpresaService empresaService;
    private HistorialProcesoService historialProcesoService;

    @Autowired
    public ComparticionProcesoService(ProcesoCompartidoEmpresaRepository procesoCompartidoEmpresaRepository,
            AccesoProcesoService accesoProcesoService, EmpresaService empresaService,
            HistorialProcesoService historialProcesoService) {
        this.procesoCompartidoEmpresaRepository = procesoCompartidoEmpresaRepository;
        this.accesoProcesoService = accesoProcesoService;
        this.empresaService = empresaService;
        this.historialProcesoService = historialProcesoService;
    }

    @Transactional(readOnly = true)
    public List<EmpresaInvitadaDto> listar(Long procesoId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        Proceso proceso = accesoProcesoService.procesoDeLaEmpresa(procesoId, usuario);
        return procesoCompartidoEmpresaRepository
                .findByProcesoIdAndActivoTrueOrderByEmpresaInvitadaNombreAsc(proceso.getId()).stream()
                .map(comparticion -> toDto(comparticion.getEmpresaInvitada()))
                .toList();
    }

    @Transactional
    public EmpresaInvitadaDto compartir(Long procesoId, Long empresaId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);
        if (proceso.getEmpresa().getId().equals(empresaId)) {
            throw new ComparticionNoValidaException(CON_SU_PROPIETARIA);
        }
        Empresa invitada = empresaService.buscarPorId(empresaId);

        ProcesoCompartidoEmpresa comparticion = procesoCompartidoEmpresaRepository
                .findByProcesoIdAndEmpresaInvitadaId(proceso.getId(), invitada.getId())
                .orElseGet(() -> new ProcesoCompartidoEmpresa(null, proceso, invitada, false));
        if (comparticion.isActivo()) {
            throw new ProcesoYaCompartidoException(YA_COMPARTIDO);
        }
        comparticion.setActivo(true);
        procesoCompartidoEmpresaRepository.save(comparticion);
        historialProcesoService.registrar(proceso, usuario,
                "proceso compartido en solo lectura con '" + invitada.getNombre() + "'");
        return toDto(invitada);
    }

    @Transactional
    public void dejarDeCompartir(Long procesoId, Long empresaId, String username) {
        Usuario usuario = accesoProcesoService.usuarioAutenticado(username);
        accesoProcesoService.validarRolAdministrador(usuario, SIN_PERMISO);
        Proceso proceso = accesoProcesoService.procesoActivoDeLaEmpresa(procesoId, usuario);

        ProcesoCompartidoEmpresa comparticion = procesoCompartidoEmpresaRepository
                .findByProcesoIdAndEmpresaInvitadaId(proceso.getId(), empresaId)
                .filter(ProcesoCompartidoEmpresa::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException(NO_COMPARTIDO));
        comparticion.setActivo(false);
        procesoCompartidoEmpresaRepository.save(comparticion);
        historialProcesoService.registrar(proceso, usuario,
                "se retiró el acceso de '" + comparticion.getEmpresaInvitada().getNombre() + "' al proceso");
    }

    private EmpresaInvitadaDto toDto(Empresa empresa) {
        return new EmpresaInvitadaDto(empresa.getId(), empresa.getNombre());
    }
}
