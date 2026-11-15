package co.edu.javeriana.procesosempresariales.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.util.function.SingletonSupplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String RUTA_LOGIN = "/login";
    private static final String RUTA_API = "/api/**";
    private static final String[] RUTAS_API_AUTH_CON_COOKIES = { "/api/auth/csrf", "/api/auth/login",
            "/api/auth/refresh", "/api/auth/logout" };
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
    private static final String[] RUTAS_API_POSICION_EVENTO = { "/api/procesos/*/message-throws/*/posicion",
            "/api/procesos/*/message-catches/*/posicion", "/api/procesos/*/envios-externos/*/posicion" };
    private static final String[] RUTAS_API_ELIMINACION_EVENTO = { "/api/procesos/*/message-throws/*/eliminacion",
            "/api/procesos/*/message-catches/*/eliminacion", "/api/procesos/*/envios-externos/*/eliminacion" };
    private static final String RUTA_API_EMPRESAS = "/api/empresas";
    private static final String DESAFIO_BEARER = "Bearer";
    private static final String DESAFIO_TOKEN_NO_VALIDO = "Bearer error=\"invalid_token\"";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(0)
    SecurityFilterChain autenticacionJwtFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(RUTAS_API_AUTH_CON_COOKIES)
                .csrf(csrf -> csrf.spa())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(contexto -> contexto
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .authorizeHttpRequests(peticiones -> peticiones.anyRequest().permitAll())
                .logout(logout -> logout.disable())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(new ApiNoAutorizadoEntryPoint())
                        .accessDeniedHandler(new ApiAccesoDenegadoHandler()))
                .build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> decodificadores,
            ObjectProvider<AutenticadorJwt> autenticadores) throws Exception {
        return http
                .securityMatcher(RUTA_API)
                .addFilter(filtroBearer(decodificadores, autenticadores))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(contexto -> contexto
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(HttpMethod.POST, RUTA_API_EMPRESAS).permitAll()
                        .requestMatchers(RUTA_API_USUARIOS).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.GET, RUTAS_API_ELIMINACION_EVENTO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.DELETE, RUTAS_API_EVENTO).hasRole(ROL_ADMINISTRADOR)
                        .requestMatchers(HttpMethod.POST, RUTAS_API_EVENTOS).hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.PUT, RUTAS_API_EVENTO).hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .requestMatchers(HttpMethod.PUT, RUTAS_API_POSICION_EVENTO)
                        .hasAnyRole(ROL_ADMINISTRADOR, ROL_EDITOR)
                        .anyRequest().authenticated())
                .logout(logout -> logout.disable())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(entradaBearer())
                        .accessDeniedHandler(new ApiAccesoDenegadoHandler()))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService)
            throws Exception {
        AccessDeniedHandlerImpl accesoDenegado = new AccessDeniedHandlerImpl();
        accesoDenegado.setErrorPage(RUTA_ACCESO_DENEGADO);
        return http
                .addFilterBefore(new UsuarioActivoFilter(userDetailsService), AnonymousAuthenticationFilter.class)
                .csrf(csrf -> csrf.spa())
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(RECURSOS_PUBLICOS).permitAll()
                        .requestMatchers(HttpMethod.GET, RUTAS_DOCUMENTACION_API).permitAll()
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
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(RUTA_LOGIN))
                        .accessDeniedHandler(accesoDenegado))
                .build();
    }

    private BearerTokenAuthenticationFilter filtroBearer(ObjectProvider<JwtDecoder> decodificadores,
            ObjectProvider<AutenticadorJwt> autenticadores) {
        SingletonSupplier<JwtAuthenticationProvider> proveedor = SingletonSupplier.of(() -> {
            JwtAuthenticationProvider jwt = new JwtAuthenticationProvider(decodificadores.getObject());
            AutenticadorJwt autenticador = autenticadores.getObject();
            jwt.setJwtAuthenticationConverter(autenticador::autenticar);
            return jwt;
        });
        AuthenticationManager gestor = autenticacion -> proveedor.obtain().authenticate(autenticacion);
        BearerTokenAuthenticationFilter filtro = new BearerTokenAuthenticationFilter(gestor);
        AuthenticationEntryPoint entrada = entradaBearer();
        filtro.setAuthenticationEntryPoint(entrada);
        filtro.setAuthenticationFailureHandler(new AuthenticationEntryPointFailureHandler(entrada));
        return filtro;
    }

    private AuthenticationEntryPoint entradaBearer() {
        ApiNoAutorizadoEntryPoint cuerpo = new ApiNoAutorizadoEntryPoint();
        return (peticion, respuesta, error) -> {
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    error instanceof OAuth2AuthenticationException ? DESAFIO_TOKEN_NO_VALIDO : DESAFIO_BEARER);
            cuerpo.commence(peticion, respuesta, error);
        };
    }
}
