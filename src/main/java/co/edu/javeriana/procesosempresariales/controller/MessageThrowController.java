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

import co.edu.javeriana.procesosempresariales.dto.MessageThrowDto;
import co.edu.javeriana.procesosempresariales.service.MessageThrowService;

@Controller
@RequestMapping("/procesos/{procesoId}/mensajes-throw")
public class MessageThrowController {

    private static final String VISTA_FORMULARIO = "procesos/mensajes-throw/formulario";
    private static final String VISTA_LISTA = "procesos/mensajes-throw/lista";
    private static final String REDIRECT_LISTA = "redirect:/procesos/{procesoId}/mensajes-throw";
    private static final String PROCESO_ID = "procesoId";

    private final MessageThrowService messageThrowService;

    public MessageThrowController(MessageThrowService messageThrowService) {
        this.messageThrowService = messageThrowService;
    }

    // Consulta solo los mensajes del proceso solicitado. La autorizacion y el mapeo a DTO
    // permanecen en el servicio para que el controlador se limite a preparar la vista.
    @GetMapping
    public String listar(@PathVariable(PROCESO_ID) Long procesoId, Principal principal, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("mensajes", messageThrowService.listar(procesoId, principal.getName()));
        return VISTA_LISTA;
    }

    // Entrega un DTO vacio a Thymeleaf; la vista nunca recibe directamente una entidad JPA.
    @GetMapping("/nuevo")
    public String nuevo(@PathVariable(PROCESO_ID) Long procesoId, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("mensajeThrow", new MessageThrowDto());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String guardar(@PathVariable(PROCESO_ID) Long procesoId,
            @Valid @ModelAttribute("mensajeThrow") MessageThrowDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            // Se vuelve al mismo formulario para mostrar los errores de validacion junto a cada campo.
            model.addAttribute(PROCESO_ID, procesoId);
            return VISTA_FORMULARIO;
        }

        // El formulario usa el mismo POST para ambas operaciones: sin id se crea y con id se actualiza.
        MessageThrowDto guardado;
        String accion;
        if (dto.getId() == null) {
            guardado = messageThrowService.crear(procesoId, dto, principal.getName());
            accion = "creado";
        } else {
            guardado = messageThrowService.actualizar(procesoId, dto.getId(), dto, principal.getName());
            accion = "actualizado";
        }
        redirectAttributes.addFlashAttribute("mensaje",
                "El mensaje throw " + guardado.getNombre() + " fue " + accion);

        // PRG evita duplicar el guardado al refrescar y muestra el resultado en la lista.
        return REDIRECT_LISTA;
    }

    // Carga el DTO existente en el formulario compartido de alta y edicion.
    @GetMapping("/{mensajeId}/editar")
    public String editar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable Long mensajeId,
            Principal principal, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("mensajeThrow", messageThrowService.buscar(procesoId, mensajeId, principal.getName()));
        return VISTA_FORMULARIO;
    }

    // La eliminacion se ejecuta mediante POST; el servicio aplica el borrado logico y registra el historial.
    @PostMapping("/{mensajeId}/eliminar")
    public String eliminar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable Long mensajeId,
            Principal principal, RedirectAttributes redirectAttributes) {
        MessageThrowDto eliminado = messageThrowService.eliminar(procesoId, mensajeId, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "El mensaje throw " + eliminado.getNombre() + " fue eliminado");
        return REDIRECT_LISTA;
    }
}
