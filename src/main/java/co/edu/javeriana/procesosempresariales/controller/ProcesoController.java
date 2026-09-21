package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
import co.edu.javeriana.procesosempresariales.service.ActividadService;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@Controller
@RequestMapping("/procesos")
public class ProcesoController {

    private ProcesoService procesoService;
    private ActividadService actividadService;
    private ArcoService arcoService;
    private GatewayService gatewayService;

    @Autowired
    public ProcesoController(ProcesoService procesoService,
                             ActividadService actividadService,
                             ArcoService arcoService,
                             GatewayService gatewayService) {
        this.procesoService = procesoService;
        this.actividadService = actividadService;
        this.arcoService = arcoService;
        this.gatewayService = gatewayService;
    }

    @GetMapping
    public String lista(@ModelAttribute("filtro") FiltroProcesosDto filtro, Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("procesos", procesoService.consultarProcesos(filtro, principal.getName()));
        model.addAttribute("categorias", procesoService.categoriasDisponibles(principal.getName()));
        model.addAttribute("estados", EstadoProceso.values());
        model.addAttribute("visibilidades", VisibilidadProceso.values());
        return "procesos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("proceso", new CrearProcesoDto());
        return "procesos/formularioprocesos";
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable("id") Long procesoId, Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("proceso", procesoService.obtener(procesoId, principal.getName()));
        model.addAttribute("puedeEditar", procesoService.puedeEditar(principal.getName()));
        model.addAttribute("puedeEliminar", procesoService.puedeEliminar(principal.getName()));
        model.addAttribute("lanes", actividadService.lanesDelProceso(procesoId, principal.getName()));
        model.addAttribute("actividades", actividadService.consultarActivas(procesoId, principal.getName()));
        model.addAttribute("gateways", gatewayService.consultarActivos(procesoId, principal.getName()));
        model.addAttribute("arcos", arcoService.consultarActivos(procesoId, principal.getName()));
        model.addAttribute("consistencia", gatewayService.advertenciasDelProceso(procesoId, principal.getName()));
        return "procesos/proceso";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long procesoId, Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        ProcesoRespuestaDto proceso = procesoService.obtener(procesoId, principal.getName());
        if (!procesoService.puedeEditar(principal.getName())) {
            return "redirect:/procesos/" + procesoId;
        }
        model.addAttribute("proceso", new EditarProcesoDto(proceso.getNombre(), proceso.getDescripcion(),
                proceso.getCategoria(), proceso.getEstado()));
        model.addAttribute("procesoId", procesoId);
        model.addAttribute("estados", EstadoProceso.values());
        return "procesos/formularioprocesoseditar";
    }

    @PostMapping("/{id}")
    public String actualizar(@PathVariable("id") Long procesoId,
            @Valid @ModelAttribute("proceso") EditarProcesoDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("procesoId", procesoId);
        if (result.hasErrors()) {
            model.addAttribute("estados", EstadoProceso.values());
            return "procesos/formularioprocesoseditar";
        }
        if (principal == null) {
            return "redirect:/login";
        }
        procesoService.editar(procesoId, dto, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje", "Proceso actualizado correctamente");
        return "redirect:/procesos/" + procesoId;
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("proceso") CrearProcesoDto dto, BindingResult result,
            Principal principal, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "procesos/formularioprocesos";
        }
        if (principal == null) {
            return "redirect:/login";
        }
        procesoService.crear(dto, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje", "Proceso creado en estado borrador");
        return "redirect:/procesos/nuevo";
    }
}


