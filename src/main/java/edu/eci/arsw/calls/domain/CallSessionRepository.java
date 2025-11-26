package edu.eci.arsw.calls.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CallSessionRepository extends MongoRepository<CallSession, String> {
    Optional<CallSession> findBySessionId(String sessionId);
    Optional<CallSession> findByReservationId(String reservationId);
}
