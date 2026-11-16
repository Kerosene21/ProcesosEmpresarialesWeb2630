package co.edu.javeriana.procesosempresariales;

import jakarta.servlet.http.Cookie;

import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

record AccesoJwt(String correo, String token, Cookie refresco) implements RequestPostProcessor {

    String bearer() {
        return "Bearer " + token;
    }

    @Override
    public MockHttpServletRequest postProcessRequest(MockHttpServletRequest peticion) {
        peticion.addHeader(HttpHeaders.AUTHORIZATION, bearer());
        return peticion;
    }

    @Override
    public String toString() {
        return "AccesoJwt[" + correo + "]";
    }
}
