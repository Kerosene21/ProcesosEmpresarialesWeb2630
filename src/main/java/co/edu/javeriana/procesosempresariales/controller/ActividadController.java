package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearActividadDto;
import co.edu.javeriana.procesosempresariales.dto.EditarActividadDto;
import co.edu.javeriana.procesosempresariales.service.ActividadService;

@Controller
@RequestMapping("/procesos/{procesoId}/actividades")
public class ActividadController {

    private static final String VISTA_FORMULARIO = "actividades/formulario";
    private static final String VISTA_FORMULARIO_EDITAR = "actividades/formularioeditar";
    private static final String VISTA_CONFIRMAR_ELIMINACION = "actividades/confirmareliminacion";
    private static final String DETALLE_DEL_PROCESO = "redirect:/procesos/";

    private final ActividadService actividadService;

    public ActividadController(ActividadService actividadService) {
        this.actividadService = actividadService;
    }

    @GetMapping("/nueva")
    public String nueva(@PathVariable("procesoId") Long procesoId, Principal principal, Model model) {
        prepararFormulario(procesoId, principal, model);
        model.addAttribute("actividad", new CrearActividadDto());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String crear(@PathVariable("procesoId") Long procesoId,
            @Valid @ModelAttribute("actividad") CrearActividadDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormulario(procesoId, principal, model);
            return VISTA_FORMULARIO;
        }
        ActividadRespuestaDto creada = actividadService.crear(procesoId, dto, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "La actividad " + creada.getNombre() + " quedo en la lane " + creada.getLaneNombre());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    @GetMapping("/{actividadId}/editar")
    public String editar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, Principal principal, Model model) {
        ActividadRespuestaDto actividad = actividadService.obtener(procesoId, actividadId, principal.getName());
        prepararFormulario(procesoId, principal, model);
        model.addAttribute("actividadId", actividadId);
        model.addAttribute("actividad",
                new EditarActividadDto(actividad.getNombre(), actividad.getTipo(), actividad.getLaneId()));
        return VISTA_FORMULARIO_EDITAR;
    }

    @PostMapping("/{actividadId}")
    public String actualizar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId,
            @Valid @ModelAttribute("actividad") EditarActividadDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormulario(procesoId, principal, model);
            model.addAttribute("actividadId", actividadId);
            return VISTA_FORMULARIO_EDITAR;
        }
        ActividadRespuestaDto editada = actividadService.editar(procesoId, actividadId, dto, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "La actividad " + editada.getNombre() + " quedo en la lane " + editada.getLaneNombre());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    @GetMapping("/{actividadId}/eliminar")
    public String confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, Principal principal, Model model) {
        model.addAttribute("actividad",
                actividadService.obtenerParaEliminar(procesoId, actividadId, principal.getName()));
        model.addAttribute("procesoId", procesoId);
        return VISTA_CONFIRMAR_ELIMINACION;
    }

    @PostMapping("/{actividadId}/eliminar")
    public String eliminar(@PathVariable("procesoId") Long procesoId,
            @PathVariable("actividadId") Long actividadId, Principal principal,
            RedirectAttributes redirectAttributes) {
        ActividadRespuestaDto eliminada = actividadService.eliminar(procesoId, actividadId, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "La actividad " + eliminada.getNombre() + " quedo eliminada y conserva su historial");
        redirectAttributes.addFlashAttribute("advertencias", eliminada.getAdvertencias());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    private void prepararFormulario(Long procesoId, Principal principal, Model model) {
        model.addAttribute("procesoId", procesoId);
        model.addAttribute("lanes", actividadService.lanesDelProceso(procesoId, principal.getName()));
        model.addAttribute("tipos", TipoActividad.values());
    }
}
