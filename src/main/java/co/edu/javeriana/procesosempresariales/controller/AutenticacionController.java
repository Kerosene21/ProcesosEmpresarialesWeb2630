package co.edu.javeriana.procesosempresariales.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AutenticacionController {

    @GetMapping("/login")
    public String login() {
        return "autenticacion/login";
    }

    @GetMapping("/acceso-denegado")
    public String accesoDenegado(Model model) {
        model.addAttribute("titulo", "No tienes permiso para ver esta informacion");
        model.addAttribute("detalle", "Esta seccion solo esta disponible para el administrador de la empresa");
        return "error/problema";
    }
}
