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

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CambiarRolUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.UsuarioRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {

    private static final String VISTA_FORMULARIO = "usuarios/formulario";
    private static final String VISTA_FORMULARIO_EDITAR = "usuarios/formularioeditar";
    private static final String REDIRECCION_LISTADO = "redirect:/usuarios";

    private UsuarioService usuarioService;

    @Autowired
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public String lista(Principal principal, Model model) {
        model.addAttribute("usuarios", usuarioService.listarDeMiEmpresa(principal.getName()));
        model.addAttribute("correoAutenticado", principal.getName());
        return "usuarios/lista";
    }

    @GetMapping("/nuevo")
    public String formulario(Model model) {
        model.addAttribute("usuario", new CrearUsuarioDto());
        model.addAttribute("roles", RolUsuario.values());
        return VISTA_FORMULARIO;
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("usuario") CrearUsuarioDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("roles", RolUsuario.values());
        if (result.hasErrors()) {
            return VISTA_FORMULARIO;
        }
        try {
            UsuarioRespuestaDto creado = usuarioService.crear(dto, principal.getName());
            redirectAttributes.addFlashAttribute("mensaje",
                    "Usuario " + creado.getCorreo() + " creado con rol " + creado.getRol());
            return REDIRECCION_LISTADO;
        } catch (CorreoAdministradorEnUsoException exception) {
            result.rejectValue("correo", "usuario.correo.enUso", exception.getMessage());
            return VISTA_FORMULARIO;
        }
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable("id") Long usuarioId, Principal principal, Model model) {
        model.addAttribute("usuario", usuarioService.obtener(usuarioId, principal.getName()));
        return "usuarios/detalle";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable("id") Long usuarioId, Principal principal, Model model) {
        UsuarioRespuestaDto usuario = usuarioService.obtener(usuarioId, principal.getName());
        model.addAttribute("usuario", new CambiarRolUsuarioDto(usuario.getRol()));
        prepararFormularioDeEdicion(model, usuarioId, usuario.getCorreo());
        return VISTA_FORMULARIO_EDITAR;
    }

    @PostMapping("/{id}")
    public String actualizar(@PathVariable("id") Long usuarioId,
            @Valid @ModelAttribute("usuario") CambiarRolUsuarioDto dto, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirectAttributes) {
        UsuarioRespuestaDto objetivo = usuarioService.obtener(usuarioId, principal.getName());
        prepararFormularioDeEdicion(model, usuarioId, objetivo.getCorreo());
        if (result.hasErrors()) {
            return VISTA_FORMULARIO_EDITAR;
        }
        UsuarioRespuestaDto actualizado = usuarioService.cambiarRol(usuarioId, dto, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "El rol de " + actualizado.getCorreo() + " ahora es " + actualizado.getRol());
        return REDIRECCION_LISTADO;
    }

    @PostMapping("/{id}/desactivar")
    public String desactivar(@PathVariable("id") Long usuarioId, Principal principal,
            RedirectAttributes redirectAttributes) {
        UsuarioRespuestaDto desactivado = usuarioService.desactivar(usuarioId, principal.getName());
        redirectAttributes.addFlashAttribute("mensaje",
                "El usuario " + desactivado.getCorreo() + " quedo inactivo y conserva su historial");
        return REDIRECCION_LISTADO;
    }

    private void prepararFormularioDeEdicion(Model model, Long usuarioId, String correo) {
        model.addAttribute("usuarioId", usuarioId);
        model.addAttribute("correo", correo);
        model.addAttribute("roles", RolUsuario.values());
    }
}
