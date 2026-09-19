package co.edu.javeriana.procesosempresariales.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AutenticacionController {

    @GetMapping("/login")
    public String login() {
        return "autenticacion/login";
    }
}
