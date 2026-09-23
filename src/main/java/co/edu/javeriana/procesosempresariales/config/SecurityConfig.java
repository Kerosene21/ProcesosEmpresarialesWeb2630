package co.edu.javeriana.procesosempresariales.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher; 
import java.util.LinkedHashMap;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String RUTA_LOGIN = "/login";
    private static final String RUTA_API = "/api/**";
    private static final String RUTA_REGISTRO_EMPRESA = "/empresas/nueva";
    private static final String RUTA_USUARIOS = "/usuarios/**";
    private static final String RUTA_ACCESO_DENEGADO = "/acceso-denegado";
    private static final String RUTA_ELIMINAR_PROCESO = "/procesos/*/eliminar";
    private static final String RUTA_ELIMINAR_ACTIVIDAD = "/procesos/*/actividades/*/eliminar";
    private static final String RUTA_ELIMINAR_ARCO = "/procesos/*/arcos/*/eliminar";
    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ROL_EDITOR = "EDITOR";
    private static final String[] RECURSOS_PUBLICOS = { "/css/**", "/js/**", "/favicon.ico", "/error" };

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(RECURSOS_PUBLICOS).permitAll()
                        .requestMatchers(RUTA_REGISTRO_EMPRESA).permitAll()
                        .requestMatchers(HttpMethod.POST, "/empresas").permitAll()
                        .requestMatchers(RUTA_USUARIOS).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, RUTA_ELIMINAR_PROCESO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, RUTA_ELIMINAR_PROCESO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/procesos/nuevo", "/procesos/*/editar")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.POST, "/procesos", "/procesos/*")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.GET, RUTA_ELIMINAR_ACTIVIDAD).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, RUTA_ELIMINAR_ACTIVIDAD).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/procesos/*/actividades/nueva",
                                "/procesos/*/actividades/*/editar")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.POST, "/procesos/*/actividades",
                                "/procesos/*/actividades/*")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.GET, RUTA_ELIMINAR_ARCO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, RUTA_ELIMINAR_ARCO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, "/procesos/*/arcos/nuevo", "/procesos/*/arcos/*/editar")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.POST, "/procesos/*/arcos", "/procesos/*/arcos/*")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.GET, "/procesos/*/gateways/nuevo",
                                "/procesos/*/gateways/*/editar")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.POST, "/procesos/*/gateways", "/procesos/*/gateways/*")
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .anyRequest().authenticated())
                .formLogin(login -> login
                        .loginPage(RUTA_LOGIN)
                        .defaultSuccessUrl("/empresas")
                        .failureUrl(RUTA_LOGIN + "?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl(RUTA_LOGIN + "?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoDeEntrada())
                        .accessDeniedPage(RUTA_ACCESO_DENEGADO))
                .build();
    }

    private AuthenticationEntryPoint puntoDeEntrada() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> puntosDeEntrada = new LinkedHashMap<>();
        puntosDeEntrada.put(new AntPathRequestMatcher(RUTA_API), new ApiNoAutorizadoEntryPoint());

        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(puntosDeEntrada);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint(RUTA_LOGIN));
        return entryPoint;
    }
}
