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

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;

@Controller
@RequestMapping("/procesos/{procesoId}/arcos")
public class ArcoController {

    private static final String VISTA_FORMULARIO = "arcos/formulario";
    private static final String VISTA_FORMULARIO_EDITAR = "arcos/formularioeditar";
    private static final String VISTA_CONFIRMAR_ELIMINACION = "arcos/confirmareliminacion";
    private static final String DETALLE_DEL_PROCESO = "redirect:/procesos/";
    private static final String MENSAJE = "mensaje";
    private static final String ADVERTENCIAS = "advertencias";

    private ArcoService arcoService;

    @Autowired
    public ArcoController(ArcoService arcoService) {
        this.arcoService = arcoService;
    }

    @GetMapping("/nuevo")
    public String nuevo(@PathVariable("procesoId") Long procesoId, Principal principal, Model model) {
        prepararFormulario(procesoId, principal, model);
        model.addAttribute("arco", new CrearArcoDto());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String crear(@PathVariable("procesoId") Long procesoId,
            @Valid @ModelAttribute("arco") CrearArcoDto dto, BindingResult result, Principal principal,
            Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormulario(procesoId, principal, model);
            return VISTA_FORMULARIO;
        }
        ArcoRespuestaDto creado = arcoService.crear(procesoId, dto, principal.getName());
        redirectAttributes.addFlashAttribute(MENSAJE,
                "El arco " + creado.getOrigenNombre() + " hacia " + creado.getDestinoNombre() + " quedo dibujado");
        redirectAttributes.addFlashAttribute(ADVERTENCIAS, creado.getAdvertencias());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    @GetMapping("/{arcoId}/editar")
    public String editar(@PathVariable("procesoId") Long procesoId, @PathVariable("arcoId") Long arcoId,
            Principal principal, Model model) {
        ArcoRespuestaDto arco = arcoService.obtener(procesoId, arcoId, principal.getName());
        prepararFormulario(procesoId, principal, model);
        model.addAttribute("arcoId", arcoId);
        model.addAttribute("arco", new EditarArcoDto(arco.getOrigenTipo(), arco.getOrigenId(), arco.getDestinoTipo(),
                arco.getDestinoId(), arco.getEtiqueta(), arco.getCondicion()));
        return VISTA_FORMULARIO_EDITAR;
    }

    @PostMapping("/{arcoId}")
    public String actualizar(@PathVariable("procesoId") Long procesoId, @PathVariable("arcoId") Long arcoId,
            @Valid @ModelAttribute("arco") EditarArcoDto dto, BindingResult result, Principal principal,
            Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormulario(procesoId, principal, model);
            model.addAttribute("arcoId", arcoId);
            return VISTA_FORMULARIO_EDITAR;
        }
        ArcoRespuestaDto editado = arcoService.editar(procesoId, arcoId, dto, principal.getName());
        redirectAttributes.addFlashAttribute(MENSAJE,
                "El arco " + editado.getOrigenNombre() + " hacia " + editado.getDestinoNombre() + " quedo actualizado");
        redirectAttributes.addFlashAttribute(ADVERTENCIAS, editado.getAdvertencias());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    @GetMapping("/{arcoId}/eliminar")
    public String confirmarEliminacion(@PathVariable("procesoId") Long procesoId,
            @PathVariable("arcoId") Long arcoId, Principal principal, Model model) {
        model.addAttribute("arco", arcoService.obtenerParaEliminar(procesoId, arcoId, principal.getName()));
        model.addAttribute("procesoId", procesoId);
        return VISTA_CONFIRMAR_ELIMINACION;
    }

    @PostMapping("/{arcoId}/eliminar")
    public String eliminar(@PathVariable("procesoId") Long procesoId, @PathVariable("arcoId") Long arcoId,
            Principal principal, RedirectAttributes redirectAttributes) {
        ArcoRespuestaDto eliminado = arcoService.eliminar(procesoId, arcoId, principal.getName());
        redirectAttributes.addFlashAttribute(MENSAJE, "El arco " + eliminado.getOrigenNombre() + " hacia "
                + eliminado.getDestinoNombre() + " quedo eliminado y conserva su historial");
        redirectAttributes.addFlashAttribute(ADVERTENCIAS, eliminado.getAdvertencias());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    private void prepararFormulario(Long procesoId, Principal principal, Model model) {
        model.addAttribute("procesoId", procesoId);
        model.addAttribute("nodos", arcoService.nodosDelProceso(procesoId, principal.getName()));
        model.addAttribute("tiposDeNodo", new TipoNodoFlujo[] { TipoNodoFlujo.ACTIVIDAD, TipoNodoFlujo.GATEWAY });
    }
}
