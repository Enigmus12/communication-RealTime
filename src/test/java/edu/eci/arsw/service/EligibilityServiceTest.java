package edu.eci.arsw.service;

import edu.eci.arsw.calls.service.EligibilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


class EligibilityServiceTest {

    private RestClient restClient;

    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec uriSpec;

    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersSpec headersSpec;

    private RestClient.ResponseSpec responseSpec;

    private EligibilityService service;

    @BeforeEach
    void setUp() {
        service = new EligibilityService("http://scheduler.test");

        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(uriSpec);


        when(uriSpec.uri("/{id}", "RES-1")).thenReturn(headersSpec);

        when(headersSpec.header("Accept", "application/json")).thenReturn(headersSpec);

        when(headersSpec.header(eq(HttpHeaders.AUTHORIZATION), anyString()))
                .thenReturn(headersSpec);

        when(headersSpec.retrieve()).thenReturn(responseSpec);

        ReflectionTestUtils.setField(service, "schedulerClient", restClient);
    }

    @SuppressWarnings("unchecked")
    private void mockBody(Map<String, Object> body) {
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(body);
    }

    // ---------------------------------------------------------------------
    // checkReservation()
    // ---------------------------------------------------------------------

    @Test
    void checkReservation_deberiaSerElegible_casoFeliz1() {
        mockBody(Map.of(
                "status", "ACEPTADO",
                "studentId", "U1",
                "tutorId", "T1"
        ));

        var res = service.checkReservation("RES-1", "U1", "Bearer TOK");

        assertTrue(res.eligible());
        assertNull(res.reason());
        verify(headersSpec).header(HttpHeaders.AUTHORIZATION, "Bearer TOK");
    }

    @Test
    void checkReservation_deberiaSerNoElegible_porEstado_casoFeliz2() {
        mockBody(Map.of(
                "status", "CANCELADO",
                "studentId", "U1",
                "tutorId", "T1"
        ));

        var res = service.checkReservation("RES-1", "U1", null);

        assertFalse(res.eligible());
        assertTrue(res.reason().contains("Not active/participant"));
    }

    @Test
    void checkReservation_noDeberiaPasar_respuestaNula() {
        mockBody(null);

        var res = service.checkReservation("RES-1", "U1", null);

        assertFalse(res.eligible());
        assertTrue(res.reason().contains("empty response"));
    }

    @Test
    void checkReservation_noDeberiaPasar_cuandoRestClientDevuelveError() {
        byte[] body = "error body".getBytes(StandardCharsets.UTF_8);
        RestClientResponseException ex = new RestClientResponseException(
                "boom",
                500,
                "Internal",
                null,
                body,
                StandardCharsets.UTF_8
        );

        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenThrow(ex);

        var res = service.checkReservation("RES-1", "U1", null);

        assertFalse(res.eligible());
        assertTrue(res.reason().contains("Reservations error"));
        assertTrue(res.reason().contains("500"));
    }
}
