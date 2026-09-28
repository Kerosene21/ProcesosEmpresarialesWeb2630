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

import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.exception.CorreoAdministradorEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.NitEmpresaDuplicadoException;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;

@Controller
@RequestMapping("/empresas")
public class EmpresaController {

    private EmpresaService empresaService;

    @Autowired
    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @GetMapping
    public String lista(Principal principal, Model model) {
        model.addAttribute("empresas", empresaService.listarVisiblesPara(principal.getName()));
        return "empresas/lista";
    }

    @GetMapping("/nueva")
    public String formulario(Model model) {
        model.addAttribute("empresa", new RegistroEmpresaDto());
        return "empresas/formulario";
    }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("empresa") RegistroEmpresaDto dto, BindingResult result,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "empresas/formulario";
        }
        try {
            EmpresaRespuestaDto registrada = empresaService.registrar(dto);
            redirectAttributes.addFlashAttribute("mensaje",
                    "Empresa registrada correctamente junto a su usuario administrador inicial");
            return "redirect:/empresas/" + registrada.getId();
        } catch (NitEmpresaDuplicadoException exception) {
            result.rejectValue("nit", "empresa.nit.duplicado", exception.getMessage());
            return "empresas/formulario";
        } catch (CorreoAdministradorEnUsoException exception) {
            result.rejectValue("correoContacto", "empresa.correoContacto.enUso", exception.getMessage());
            return "empresas/formulario";
        }
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable("id") Long empresaId, Principal principal, Model model) {
        model.addAttribute("empresa", empresaService.obtenerParaUsuario(empresaId, principal.getName()));
        return "empresas/detalle";
    }
}
