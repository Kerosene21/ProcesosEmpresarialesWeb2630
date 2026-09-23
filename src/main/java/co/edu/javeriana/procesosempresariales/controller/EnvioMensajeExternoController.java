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

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import co.edu.javeriana.procesosempresariales.dto.EnvioMensajeExternoDto;
import co.edu.javeriana.procesosempresariales.service.EnvioMensajeExternoService;


 // atiende las vistas web para documentar envios hacia sistemas externos
 
@Controller
@RequestMapping("/procesos/{procesoId}/envios-externos")
public class EnvioMensajeExternoController {

    private static final String VISTA_FORMULARIO = "procesos/envios-externos/formulario";
    private static final String VISTA_LISTA = "procesos/envios-externos/lista";
    private static final String REDIRECT_LISTA = "redirect:/procesos/{procesoId}/envios-externos";
    private static final String PROCESO_ID = "procesoId";

    private final EnvioMensajeExternoService envioService;

        public EnvioMensajeExternoController(EnvioMensajeExternoService envioService) {
        this.envioService = envioService;
    }

    @GetMapping
    public String listar(@PathVariable(PROCESO_ID) Long procesoId, Principal principal, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("envios", envioService.listar(procesoId, principal.getName()));
        return VISTA_LISTA;
    }

    @GetMapping("/nuevo")
    public String nuevo(@PathVariable(PROCESO_ID) Long procesoId, Model model) {
        prepararFormulario(procesoId, model);
        model.addAttribute("envio", new EnvioMensajeExternoDto());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String guardar(@PathVariable(PROCESO_ID) Long procesoId,
            @Valid @ModelAttribute("envio") EnvioMensajeExternoDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            // Se conserva la vista para que Thymeleaf muestre el error junto al campo correspondiente
            prepararFormulario(procesoId, model);
            return VISTA_FORMULARIO;
        }

        // El id oculto permite que el formulario compartido cree o actualice el registro
        EnvioMensajeExternoDto guardado;
        String accion;
        if (dto.getId() == null) {
            guardado = envioService.crear(procesoId, dto, principal.getName());
            accion = "creado";
        } else {
            guardado = envioService.actualizar(procesoId, dto.getId(), dto, principal.getName());
            accion = "actualizado";
        }
        redirectAttributes.addFlashAttribute("mensaje",
                "El envio al sistema " + guardado.getNombreSistemaExterno() + " fue " + accion);

        // PRG evita repetir el POST al refrescar la pagina y muestra el resultado en la lista
        return REDIRECT_LISTA;
    }

    @GetMapping("/{envioId}/editar")
    public String editar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable Long envioId,
            Principal principal, Model model) {
        prepararFormulario(procesoId, model);
        model.addAttribute("envio", envioService.buscar(procesoId, envioId, principal.getName()));
        return VISTA_FORMULARIO;
    }

    @PostMapping("/{envioId}/eliminar")
    public String eliminar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable Long envioId,
            Principal principal, RedirectAttributes redirectAttributes) {
        EnvioMensajeExternoDto eliminado = envioService.eliminar(procesoId, envioId, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "El envio al sistema " + eliminado.getNombreSistemaExterno() + " fue eliminado");
        return REDIRECT_LISTA;
    }

    private void prepararFormulario(Long procesoId, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute("tiposDestino", TipoDestinoExterno.values());
        model.addAttribute("comportamientosFallo", ComportamientoFallo.values());
    }
}
