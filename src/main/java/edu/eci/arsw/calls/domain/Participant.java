package edu.eci.arsw.calls.domain;

public class Participant {
    private String userId;
    private String role; // STUDENT|TUTOR
    private Long joinedAt;
    private Long leftAt;

    public Participant() {}
    public Participant(String userId, String role, Long joinedAt) {
        this.userId = userId; this.role = role; this.joinedAt = joinedAt;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Long joinedAt) { this.joinedAt = joinedAt; }
    public Long getLeftAt() { return leftAt; }
    public void setLeftAt(Long leftAt) { this.leftAt = leftAt; }
}
