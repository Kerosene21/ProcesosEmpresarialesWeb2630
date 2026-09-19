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
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String RUTA_LOGIN = "/login";
    private static final String RUTA_API = "/api/**";
    private static final String RUTA_REGISTRO_EMPRESA = "/empresas/nueva";
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
                .exceptionHandling(errores -> errores.authenticationEntryPoint(puntoDeEntrada()))
                .build();
    }

    private AuthenticationEntryPoint puntoDeEntrada() {
        return DelegatingAuthenticationEntryPoint.builder()
                .addEntryPointFor(new ApiNoAutorizadoEntryPoint(),
                        PathPatternRequestMatcher.withDefaults().matcher(RUTA_API))
                .defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(RUTA_LOGIN))
                .build();
    }
}
