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

import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchDto;
import co.edu.javeriana.procesosempresariales.service.MessageCatchService;

@Controller
@RequestMapping("/procesos/{procesoId}/mensajes-catch")
public class MessageCatchController {

    private static final String VISTA_FORMULARIO = "procesos/mensajes-catch/formulario";
    private static final String VISTA_LISTA = "procesos/mensajes-catch/lista";
    private static final String REDIRECT_LISTA = "redirect:/procesos/{procesoId}/mensajes-catch";
    private static final String PROCESO_ID = "procesoId";

    private final MessageCatchService messageCatchService;

    // hacer explicita la unica dependencia del controlador.
    public MessageCatchController(MessageCatchService messageCatchService) {
        this.messageCatchService = messageCatchService;
    }

    @GetMapping
    public String listar(@PathVariable(PROCESO_ID) Long procesoId, Principal principal, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("mensajesCatch", messageCatchService.listar(procesoId, principal.getName()));
        return VISTA_LISTA;
    }

    @GetMapping("/nuevo")
    public String nuevo(@PathVariable(PROCESO_ID) Long procesoId, Model model) {
        prepararFormulario(procesoId, model);
        model.addAttribute("messageCatch", new MessageCatchDto());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String guardar(@PathVariable(PROCESO_ID) Long procesoId,
            @Valid @ModelAttribute("messageCatch") MessageCatchDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormulario(procesoId, model);
            return VISTA_FORMULARIO;
        }

        // El id oculto permite usar el mismo formulario para crear y actualizar.
        MessageCatchDto guardado;
        String accion;
        if (dto.getId() == null) {
            guardado = messageCatchService.crear(procesoId, dto, principal.getName());
            accion = "creado";
        } else {
            guardado = messageCatchService.actualizar(procesoId, dto.getId(), dto, principal.getName());
            accion = "actualizado";
        }
        redirectAttributes.addFlashAttribute("mensaje",
                "El Message Catch " + guardado.getNombreMensaje() + " fue " + accion);
        redirectAttributes.addFlashAttribute("advertencias", guardado.getAdvertencias());

        // PRG evita repetir el POST al refrescar la lista.
        return REDIRECT_LISTA;
    }

    @GetMapping("/{catchId}/editar")
    public String editar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable Long catchId,
            Principal principal, Model model) {
        prepararFormulario(procesoId, model);
        model.addAttribute("messageCatch", messageCatchService.buscar(procesoId, catchId, principal.getName()));
        return VISTA_FORMULARIO;
    }

    @PostMapping("/{catchId}/eliminar")
    public String eliminar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable Long catchId,
            Principal principal, RedirectAttributes redirectAttributes) {
        MessageCatchDto eliminado = messageCatchService.eliminar(procesoId, catchId, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "El Message Catch " + eliminado.getNombreMensaje() + " fue eliminado");
        return REDIRECT_LISTA;
    }

    private void prepararFormulario(Long procesoId, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("variantes", VarianteMessageCatch.values());
    }
}
