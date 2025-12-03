package edu.eci.arsw.calls.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference; 
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Servicio para verificar la elegibilidad de una reserva.
 */
@Service
public class EligibilityService {
    private final RestClient schedulerClient;
    private final String baseUrl;

    // Se define el tipo de referencia para garantizar Type Safety
    private static final ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE = 
        new ParameterizedTypeReference<>() {};

    public EligibilityService(
            @Value("${reservations.base:http://localhost:8090/api/reservations}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.schedulerClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Verifica la elegibilidad de una reserva para un usuario dado.
     * * @param reservationId ID de la reserva.
     * @param userId        ID del usuario.
     * @return Resultado de la verificación de elegibilidad.
     */
    public EligibilityResult checkReservation(String reservationId, String userId) {
        return checkReservation(reservationId, userId, null);
    }

    /**
     * Verifica la elegibilidad de una reserva para un usuario dado, con token
     * opcional.
     * * @param reservationId ID de la reserva.
     * @param userId        ID del usuario.
     * @param bearerToken   Token de autenticación opcional.
     * @return Resultado de la verificación de elegibilidad.
     */
    public EligibilityResult checkReservation(String reservationId, String userId, @Nullable String bearerToken) {
        try {
            RestClient.RequestHeadersSpec<?> req = schedulerClient.get()
                    .uri("/{id}", reservationId)
                    .header("Accept", "application/json");

            if (bearerToken != null && !bearerToken.isBlank()) {
                String token = bearerToken.replaceFirst("(?i)^bearer\\s+", "");
                req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }

            // Se usa ParameterizedTypeReference para Type Safety (evita unchecked conversion)
            Map<String, Object> resp = req.retrieve().body(RESPONSE_TYPE);
            
            if (resp == null)
                return EligibilityResult.notEligible("Reservations empty response from " + baseUrl);

            String status = String.valueOf(resp.getOrDefault("status", ""));
            String studentId = String.valueOf(resp.getOrDefault("studentId", resp.getOrDefault("student_id", "")));
            String tutorId = String.valueOf(resp.getOrDefault("tutorId", resp.getOrDefault("tutor_id", "")));

            boolean isParticipant = userId.equals(studentId) || userId.equals(tutorId);
            String up = status == null ? "" : status.toUpperCase();
            boolean eligibleStatus = "ACEPTADO".equals(up) || "ACTIVE".equals(up) || "ACTIVA".equals(up);

            return (eligibleStatus && isParticipant)
                    ? EligibilityResult.ok()
                    : EligibilityResult.notEligible("Not active/participant (status=" + status + ", studentId="
                            + studentId + ", tutorId=" + tutorId + ")");

        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            
            // Si el cuerpo existe y es largo, se trunca
            String bodyMsg = "";
            if (body.length() > 200) {
                body = body.substring(0, 200) + "…";
            }
            bodyMsg = " body=" + body;

            return EligibilityResult.notEligible("Reservations error: " + ex.getStatusCode()
                    + " from " + baseUrl + " id=" + reservationId + bodyMsg);
        }
    }

    /**
     * Resultado de la verificación de elegibilidad.
     * * @param eligible Indica si es elegible.
     * @param reason   Razón en caso de no ser elegible.
     */
    public record EligibilityResult(boolean eligible, String reason) {
        public static EligibilityResult ok() {
            return new EligibilityResult(true, null);
        }

        public static EligibilityResult notEligible(String reason) {
            return new EligibilityResult(false, reason);
        }
    }
}