package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Locale;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

final class ClientePruebaJwt {

    static final String RUTA_CSRF = "/api/auth/csrf";
    static final String RUTA_LOGIN = "/api/auth/login";
    static final String RUTA_REFRESH = "/api/auth/refresh";
    static final String RUTA_LOGOUT = "/api/auth/logout";
    static final String RUTA_ME = "/api/auth/me";
    static final String COOKIE_CSRF = "XSRF-TOKEN";
    static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    static final String COOKIE_REFRESCO = "PE_REFRESH";

    private final MockMvc mockMvc;

    ClientePruebaJwt(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    Cookie tokenCsrf() throws Exception {
        Cookie token = mockMvc.perform(get(RUTA_CSRF)).andReturn().getResponse().getCookie(COOKIE_CSRF);
        assertThat(token).as("cookie %s emitida por %s", COOKIE_CSRF, RUTA_CSRF).isNotNull();
        return token;
    }

    MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion, Cookie... cookies)
            throws Exception {
        Cookie token = tokenCsrf();
        peticion.cookie(token).header(CABECERA_CSRF, token.getValue());
        return cookies.length == 0 ? peticion : peticion.cookie(cookies);
    }

    MvcResult intentarLogin(String correo, String password) throws Exception {
        return mockMvc.perform(conCsrf(post(RUTA_LOGIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(correo, password)))
                .andReturn();
    }

    AccesoJwt iniciarSesion(String correo, String password) throws Exception {
        AccesoJwt acceso = acceso(intentarLogin(correo, password));
        assertThat(acceso.correo()).isEqualTo(correo.strip().toLowerCase(Locale.ROOT));
        assertThat(acceso.refresco()).as("cookie %s del login", COOKIE_REFRESCO).isNotNull();
        return acceso;
    }

    MockHttpSession sesionDeNavegador(String correo, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(password))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
        assertThat(sesion).as("sesión del formulario /login").isNotNull();
        return sesion;
    }

    MvcResult intentarRefrescar(Cookie refresco) throws Exception {
        return mockMvc.perform(conCsrf(post(RUTA_REFRESH), refresco)).andReturn();
    }

    AccesoJwt acceso(MvcResult resultado) throws Exception {
        MockHttpServletResponse respuesta = resultado.getResponse();
        String cuerpo = respuesta.getContentAsString();
        assertThat(respuesta.getStatus()).as("respuesta de %s: %s", resultado.getRequest().getRequestURI(), cuerpo)
                .isEqualTo(200);
        assertThat(JsonPath.<String>read(cuerpo, "$.tipoToken")).isEqualTo("Bearer");
        return new AccesoJwt(JsonPath.read(cuerpo, "$.usuario.correo"), JsonPath.read(cuerpo, "$.tokenAcceso"),
                respuesta.getCookie(COOKIE_REFRESCO));
    }

    static String credenciales(String correo, String password) {
        return "{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}";
    }
}
