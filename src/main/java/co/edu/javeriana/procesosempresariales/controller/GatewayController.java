package co.edu.javeriana.procesosempresariales.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;

@Controller
@RequestMapping("/procesos/{procesoId}/gateways")
public class GatewayController {

    private static final String VISTA_FORMULARIO = "gateways/formulario";
    private static final String VISTA_FORMULARIO_EDITAR = "gateways/formularioeditar";
    private static final String DETALLE_DEL_PROCESO = "redirect:/procesos/";
    private static final String MENSAJE = "mensaje";
    private static final String ADVERTENCIAS = "advertencias";
    private static final String PROCESO_ID = "procesoId";
    private static final String TIPOS = "tipos";

    private final GatewayService gatewayService;
    private final ArcoService arcoService;

    public GatewayController(GatewayService gatewayService, ArcoService arcoService) {
        this.gatewayService = gatewayService;
        this.arcoService = arcoService;
    }

    @GetMapping("/nuevo")
    public String nuevo(@PathVariable(PROCESO_ID) Long procesoId, Model model) {
        prepararFormulario(procesoId, model);
        model.addAttribute("gateway", new CrearGatewayDto());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String crear(@PathVariable(PROCESO_ID) Long procesoId,
            @Valid @ModelAttribute("gateway") CrearGatewayDto dto, BindingResult result, Principal principal,
            Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormulario(procesoId, model);
            return VISTA_FORMULARIO;
        }
        GatewayRespuestaDto creado = gatewayService.crear(procesoId, dto, principal.getName());
        redirectAttributes.addFlashAttribute(MENSAJE, "El " + creado.getEtiqueta() + " quedo dibujado en ("
                + creado.getPosicionX() + ", " + creado.getPosicionY() + ")");
        redirectAttributes.addFlashAttribute(ADVERTENCIAS, creado.getAdvertencias());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    @GetMapping("/{gatewayId}/editar")
    public String editar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable("gatewayId") Long gatewayId,
            Principal principal, Model model) {
        GatewayRespuestaDto gateway = gatewayService.obtener(procesoId, gatewayId, principal.getName());
        List<ArcoRespuestaDto> salientes = prepararFormularioDeEdicion(procesoId, gatewayId, principal, model);
        model.addAttribute(ADVERTENCIAS, gateway.getAdvertencias());
        model.addAttribute("gateway", new EditarGatewayDto(gateway.getTipo(), condicionesActuales(salientes)));
        return VISTA_FORMULARIO_EDITAR;
    }

    @PostMapping("/{gatewayId}")
    public String actualizar(@PathVariable(PROCESO_ID) Long procesoId, @PathVariable("gatewayId") Long gatewayId,
            @Valid @ModelAttribute("gateway") EditarGatewayDto dto, BindingResult result, Principal principal,
            Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            prepararFormularioDeEdicion(procesoId, gatewayId, principal, model);
            return VISTA_FORMULARIO_EDITAR;
        }
        GatewayRespuestaDto editado = gatewayService.editar(procesoId, gatewayId, dto, principal.getName());
        redirectAttributes.addFlashAttribute(MENSAJE, "El " + editado.getEtiqueta() + " quedo actualizado");
        redirectAttributes.addFlashAttribute(ADVERTENCIAS, editado.getAdvertencias());
        return DETALLE_DEL_PROCESO + procesoId;
    }

    private void prepararFormulario(Long procesoId, Model model) {
        model.addAttribute(PROCESO_ID, procesoId);
        model.addAttribute(TIPOS, TipoGateway.values());
    }

    private List<ArcoRespuestaDto> prepararFormularioDeEdicion(Long procesoId, Long gatewayId, Principal principal,
            Model model) {
        List<ArcoRespuestaDto> salientes = arcoService.salientesDe(procesoId, TipoNodoFlujo.GATEWAY, gatewayId,
                principal.getName());
        prepararFormulario(procesoId, model);
        model.addAttribute("gatewayId", gatewayId);
        model.addAttribute("salientes", salientes);
        return salientes;
    }

    private Map<Long, String> condicionesActuales(List<ArcoRespuestaDto> salientes) {
        Map<Long, String> condiciones = new LinkedHashMap<>();
        salientes.forEach(arco -> condiciones.put(arco.getId(), arco.getCondicion()));
        return condiciones;
    }
}
