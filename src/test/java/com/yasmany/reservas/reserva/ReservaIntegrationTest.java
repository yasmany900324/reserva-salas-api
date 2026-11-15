package com.yasmany.reservas.reserva;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de punta a punta: seguridad JWT real, validación, migraciones Flyway y
 * la restricción de exclusión de PostgreSQL, contra un PostgreSQL 16 en Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private String token;
    private long salaId;
    private long responsableId;

    @BeforeEach
    void preparar() throws Exception {
        token = login();
        // Código único por test: el contenedor se comparte entre los tests de la clase
        salaId = crear("/salas", """
                {"codigo": "IT-%d", "nombre": "Sala integración"}""".formatted(System.nanoTime() % 1_000_000));
        responsableId = crear("/responsables", """
                {"nombre": "Ana Pérez"}""");
    }

    @Test
    void creaUnaReservaYRechazaOtraSolapadaEnLaMismaSala() throws Exception {
        crearReserva("2031-03-10T10:00:00Z", "2031-03-10T11:00:00Z")
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("/reservas/")))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.sala.id").value(salaId));

        crearReserva("2031-03-10T10:30:00Z", "2031-03-10T11:30:00Z")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("choca con la reserva")));

        // Contigua: empieza justo cuando termina la anterior, no choca
        crearReserva("2031-03-10T11:00:00Z", "2031-03-10T12:00:00Z")
                .andExpect(status().isCreated());
    }

    @Test
    void exigeTokenParaCrearYCancelarPeroNoParaConsultar() throws Exception {
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoReserva("2031-03-11T10:00:00Z", "2031-03-11T11:00:00Z")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));

        mvc.perform(patch("/reservas/1/cancelar"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/reservas"))
                .andExpect(status().isOk());
    }

    @Test
    void validaLosDatosConErroresPorCampo() throws Exception {
        crearReserva("2031-03-12T11:00:00Z", "2031-03-12T10:00:00Z")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].campo").value("fin"));

        crearReserva("2031-03-12T10:00:00", "2031-03-12T11:00:00")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].mensaje", containsString("ISO-8601")));
    }

    @Test
    void cancelarLiberaElHorarioYNoSePuedeCancelarDosVeces() throws Exception {
        long id = idDe(crearReserva("2031-03-13T10:00:00Z", "2031-03-13T11:00:00Z")
                .andExpect(status().isCreated()));

        mvc.perform(patch("/reservas/{id}/cancelar", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        mvc.perform(patch("/reservas/{id}/cancelar", id).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        // El hueco que deja la cancelada se puede volver a reservar
        crearReserva("2031-03-13T10:00:00Z", "2031-03-13T11:00:00Z")
                .andExpect(status().isCreated());
    }

    @Test
    void filtraPorSalaYFechaConPaginacion() throws Exception {
        crearReserva("2031-03-14T09:00:00Z", "2031-03-14T10:00:00Z").andExpect(status().isCreated());
        crearReserva("2031-03-14T23:30:00Z", "2031-03-15T00:30:00Z").andExpect(status().isCreated());
        crearReserva("2031-03-16T09:00:00Z", "2031-03-16T10:00:00Z").andExpect(status().isCreated());

        // La que cruza la medianoche también ocupa el día 15
        mvc.perform(get("/reservas").param("salaId", String.valueOf(salaId)).param("fecha", "2031-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].inicio").value("2031-03-14T23:30:00Z"));

        mvc.perform(get("/reservas").param("salaId", String.valueOf(salaId))
                        .param("desde", "2031-03-14").param("hasta", "2031-03-16")
                        .param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    private String login() throws Exception {
        String respuesta = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usuario": "admin", "password": "admin123"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta).get("accessToken").asText();
    }

    private long crear(String ruta, String cuerpo) throws Exception {
        return idDe(mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()));
    }

    private ResultActions crearReserva(String inicio, String fin) throws Exception {
        return mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoReserva(inicio, fin))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String cuerpoReserva(String inicio, String fin) {
        return """
                {"salaId": %d, "responsableId": %d, "inicio": "%s", "fin": "%s"}"""
                .formatted(salaId, responsableId, inicio, fin);
    }

    private long idDe(ResultActions resultado) throws Exception {
        JsonNode nodo = json.readTree(resultado.andReturn().getResponse().getContentAsString());
        return nodo.get("id").asLong();
    }
}
