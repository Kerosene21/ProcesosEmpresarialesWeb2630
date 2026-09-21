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
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
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

    @GetMapping
    public String lista(@ModelAttribute("filtro") FiltroProcesosDto filtro, Principal principal, Model model) {
        model.addAttribute("procesos", procesoService.consultarProcesos(filtro, principal.getName()));
        model.addAttribute("categorias", procesoService.categoriasDisponibles(principal.getName()));
        model.addAttribute("estados", EstadoProceso.values());
        model.addAttribute("visibilidades", VisibilidadProceso.values());
        return "procesos/lista";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        // La vista trabaja con un DTO, no con una entidad de la base de datos.
        model.addAttribute("proceso", new CrearProcesoDto());
        return "procesos/formularioprocesos";
    }

    @GetMapping("/{id}")
    public String ver(@PathVariable("id") Long procesoId, Principal principal, Model model) {
        model.addAttribute("proceso", procesoService.obtener(procesoId, principal.getName()));
        model.addAttribute("puedeEditar", procesoService.puedeEditar(principal.getName()));
        model.addAttribute("puedeEliminar", procesoService.puedeEliminar(principal.getName()));
        return "procesos/proceso";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long procesoId, Principal principal, Model model) {
        ProcesoRespuestaDto proceso = procesoService.obtener(procesoId, principal.getName());
        model.addAttribute("proceso", new EditarProcesoDto(proceso.getNombre(), proceso.getDescripcion(),
                proceso.getCategoria(), proceso.getEstado()));
        model.addAttribute("procesoId", procesoId);
        model.addAttribute("estados", EstadoProceso.values());
        return "procesos/formularioprocesoseditar";
    }

    @GetMapping("/{id}/historial")
    public String historial(@PathVariable("id") Long procesoId, Principal principal, Model model) {
        model.addAttribute("proceso", procesoService.obtener(procesoId, principal.getName()));
        model.addAttribute("historial", procesoService.consultarHistorial(procesoId, principal.getName()));
        return "procesos/historial";
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
        procesoService.editar(procesoId, dto, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje", "Proceso actualizado correctamente");
        return "redirect:/procesos/" + procesoId;
    }

    @GetMapping("/{id}/eliminar")
    public String confirmarEliminacion(@PathVariable("id") Long procesoId, Principal principal, Model model) {
        model.addAttribute("proceso", procesoService.obtenerParaEliminar(procesoId, principal.getName()));
        return "procesos/confirmareliminacion";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable("id") Long procesoId, Principal principal,
            RedirectAttributes redirectAttributes) {
        ProcesoRespuestaDto eliminado = procesoService.eliminar(procesoId, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "El proceso " + eliminado.getNombre() + " quedo eliminado y conserva su historial");
        return "redirect:/procesos/" + procesoId;
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("proceso") CrearProcesoDto dto, BindingResult result,
            Principal principal, RedirectAttributes redirectAttributes) {
        // Si falta algo, volvemos al formulario y mostramos los errores junto a cada campo.
        if (result.hasErrors()) {
            return "procesos/formularioprocesos";
        }
        ProcesoRespuestaDto creado = procesoService.crear(dto, principal.getName());
        // Después de guardar redirigimos para que recargar la página no repita el POST.
        redirectAttributes.addFlashAttribute("mensaje", "Proceso creado en estado borrador");
        return "redirect:/procesos/" + creado.getId();
    }
}
