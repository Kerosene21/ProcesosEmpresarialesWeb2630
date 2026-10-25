package co.edu.javeriana.procesosempresariales.config;

import java.util.LinkedHashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String RUTA_LOGIN = "/login";
    private static final String RUTA_API = "/api/**";
    private static final String RUTA_REGISTRO_EMPRESA = "/empresas/nueva";
    private static final String RUTA_USUARIOS = "/usuarios/**";
    private static final String RUTA_API_USUARIOS = "/api/usuarios/**";
    private static final String RUTA_ACCESO_DENEGADO = "/acceso-denegado";
    private static final String RUTA_ELIMINAR_PROCESO = "/procesos/*/eliminar";
    private static final String RUTA_ELIMINAR_ACTIVIDAD = "/procesos/*/actividades/*/eliminar";
    private static final String RUTA_ELIMINAR_ARCO = "/procesos/*/arcos/*/eliminar";
    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ROL_EDITOR = "EDITOR";
    private static final String[] RECURSOS_PUBLICOS = { "/css/**", "/js/**", "/favicon.ico", "/error" };
    private static final String[] RUTAS_DOCUMENTACION_API = { "/v3/api-docs/**", "/v3/api-docs.yaml/**",
            "/swagger-ui.html", "/swagger-ui/**" };
    private static final String[] RUTAS_API_EVENTOS = { "/api/procesos/*/message-throws",
            "/api/procesos/*/message-catches", "/api/procesos/*/envios-externos" };
    private static final String[] RUTAS_API_EVENTO = { "/api/procesos/*/message-throws/*",
            "/api/procesos/*/message-catches/*", "/api/procesos/*/envios-externos/*" };
    private static final String[] RUTAS_API_ELIMINACION_EVENTO = { "/api/procesos/*/message-throws/*/eliminacion",
            "/api/procesos/*/message-catches/*/eliminacion", "/api/procesos/*/envios-externos/*/eliminacion" };
    private static final String RUTA_API_SESION = "/api/sesion";
    private static final String RUTA_API_EMPRESAS = "/api/empresas";
    private static final String PARAMETRO_CORREO = "correo";
    private static final String PARAMETRO_PASSWORD = "password";
    private static final String COOKIE_SESION = "JSESSIONID";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(1)
    SecurityFilterChain sesionApiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(RUTA_API_SESION)
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(peticiones -> peticiones.anyRequest().permitAll())
                .formLogin(login -> login
                        .loginPage(RUTA_API_SESION)
                        .loginProcessingUrl(RUTA_API_SESION)
                        .usernameParameter(PARAMETRO_CORREO)
                        .passwordParameter(PARAMETRO_PASSWORD)
                        .successHandler((peticion, respuesta, autenticacion) ->
                                respuesta.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler(new ApiCredencialesInvalidasHandler()))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.DELETE, RUTA_API_SESION))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(COOKIE_SESION))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new ApiNoAutorizadoEntryPoint())
                        .accessDeniedHandler(new ApiAccesoDenegadoHandler()))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(RECURSOS_PUBLICOS).permitAll()
                        .requestMatchers(HttpMethod.GET, RUTAS_DOCUMENTACION_API).permitAll()
                        .requestMatchers(RUTA_REGISTRO_EMPRESA).permitAll()
                        .requestMatchers(HttpMethod.POST, "/empresas").permitAll()
                        .requestMatchers(HttpMethod.POST, RUTA_API_EMPRESAS).permitAll()
                        .requestMatchers(RUTA_USUARIOS).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(RUTA_API_USUARIOS).hasRole(ROL_ADMINISTRADOR)
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
                        .requestMatchers(HttpMethod.GET, RUTAS_API_ELIMINACION_EVENTO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.DELETE, RUTAS_API_EVENTO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, RUTAS_API_EVENTOS).hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.PUT, RUTAS_API_EVENTO).hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
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
                        .accessDeniedHandler(accesoDenegado()))
                .build();
    }

    private AccessDeniedHandler accesoDenegado() {
        AccessDeniedHandlerImpl pagina = new AccessDeniedHandlerImpl();
        pagina.setErrorPage(RUTA_ACCESO_DENEGADO);
        LinkedHashMap<RequestMatcher, AccessDeniedHandler> manejadores = new LinkedHashMap<>();
        manejadores.put(PathPatternRequestMatcher.withDefaults().matcher(RUTA_API), new ApiAccesoDenegadoHandler());
        return new RequestMatcherDelegatingAccessDeniedHandler(manejadores, pagina);
    }

    private AuthenticationEntryPoint puntoDeEntrada() {
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(new ApiNoAutorizadoEntryPoint(),
                        PathPatternRequestMatcher.withDefaults().matcher(RUTA_API))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(RUTA_LOGIN))
                .build();
    }
}
