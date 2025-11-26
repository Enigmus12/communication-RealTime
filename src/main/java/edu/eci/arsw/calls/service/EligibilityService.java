package edu.eci.arsw.calls.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class EligibilityService {
    private final RestClient schedulerClient;
    private final String baseUrl;

    public EligibilityService(@Value("${RESERVATIONS_BASE:http://localhost:8090/api/reservations}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.schedulerClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public EligibilityResult checkReservation(String reservationId, String userId) {
        return checkReservation(reservationId, userId, null);
    }

    public EligibilityResult checkReservation(String reservationId, String userId, @Nullable String bearerToken) {
        try {
            RestClient.RequestHeadersSpec<?> req = schedulerClient.get()
                    .uri("/{id}", reservationId)
                    .header("Accept", "application/json");

            if (bearerToken != null && !bearerToken.isBlank()) {
                String token = bearerToken.replaceFirst("(?i)^bearer\\s+", "");
                req = req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }

            Map<String, Object> resp = req.retrieve().body(Map.class);
            if (resp == null) return EligibilityResult.notEligible("Reservations empty response from " + baseUrl);

            String status = String.valueOf(resp.getOrDefault("status", ""));
            String studentId = String.valueOf(resp.getOrDefault("studentId", resp.getOrDefault("student_id", "")));
            String tutorId = String.valueOf(resp.getOrDefault("tutorId", resp.getOrDefault("tutor_id", "")));

            boolean isParticipant = userId.equals(studentId) || userId.equals(tutorId);
            String up = status == null ? "" : status.toUpperCase();
            boolean eligibleStatus = "ACEPTADO".equals(up) || "ACTIVE".equals(up) || "ACTIVA".equals(up);

            return (eligibleStatus && isParticipant)
                    ? EligibilityResult.ok()
                    : EligibilityResult.notEligible("Not active/participant (status=" + status + ", studentId=" + studentId + ", tutorId=" + tutorId + ")");

        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            if (body != null && body.length() > 200) body = body.substring(0, 200) + "…";
            return EligibilityResult.notEligible("Reservations error: " + ex.getStatusCode()
                    + " from " + baseUrl + " id=" + reservationId + (body != null ? " body=" + body : ""));
        }
    }

    public record EligibilityResult(boolean eligible, String reason) {
        public static EligibilityResult ok() { return new EligibilityResult(true, null); }
        public static EligibilityResult notEligible(String reason) { return new EligibilityResult(false, reason); }
    }
}
