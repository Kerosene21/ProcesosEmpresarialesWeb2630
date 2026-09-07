package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@Controller
@RequestMapping("/procesos")
public class ProcesoController {
    private final ProcesoService procesoService;

    public ProcesoController(ProcesoService procesoService) {
        this.procesoService = procesoService;
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        // La vista trabaja con un DTO, no con una entidad de la base de datos.
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
        // Si falta algo, volvemos al formulario y mostramos los errores junto a cada campo.
        if (result.hasErrors()) {
            return "procesos/formularioprocesos";
        }
        if (principal == null) {
            return "redirect:/login";
        }
        procesoService.crear(dto, principal.getName());
        // Después de guardar redirigimos para que recargar la página no repita el POST.
        redirectAttributes.addFlashAttribute("mensaje", "Proceso creado en estado borrador");
        return "redirect:/procesos/nuevo";
    }
}