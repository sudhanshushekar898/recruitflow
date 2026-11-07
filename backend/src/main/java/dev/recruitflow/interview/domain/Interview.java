package dev.recruitflow.interview.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "interviews")
public class Interview extends BaseEntity {

    @Column(nullable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private int roundNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewType type;

    @Column(nullable = false)
    private Instant scheduledStart;

    @Column(nullable = false)
    private Instant scheduledEnd;

    @Column(length = 300)
    private String locationOrLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InterviewStatus status = InterviewStatus.SCHEDULED;

    @Column(length = 2000)
    private String notes;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "interview_participants", joinColumns = @JoinColumn(name = "interview_id"))
    @BatchSize(size = 50)
    private Set<InterviewParticipant> participants = new HashSet<>();

    protected Interview() {}

    public Interview(UUID applicationId, int roundNo, InterviewType type, Instant start, Instant end, String locationOrLink,
                     String notes, Set<InterviewParticipant> participants) {
        this.applicationId = applicationId;
        this.roundNo = roundNo;
        this.type = type;
        this.scheduledStart = start;
        this.scheduledEnd = end;
        this.locationOrLink = clean(locationOrLink);
        this.notes = clean(notes);
        this.participants.addAll(participants);
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Null arguments leave a field unchanged; an empty string clears the optional ones. */
    public void reschedule(Instant start, Instant end, InterviewType type, String locationOrLink, String notes) {
        if (start != null) {
            this.scheduledStart = start;
        }
        if (end != null) {
            this.scheduledEnd = end;
        }
        if (type != null) {
            this.type = type;
        }
        if (locationOrLink != null) {
            this.locationOrLink = clean(locationOrLink);
        }
        if (notes != null) {
            this.notes = clean(notes);
        }
    }

    public void replaceParticipants(Set<InterviewParticipant> newParticipants) {
        participants.clear();
        participants.addAll(newParticipants);
    }

    public void setStatus(InterviewStatus status) {
        this.status = status;
    }

    public boolean isParticipant(UUID userId) {
        return participants.stream().anyMatch(p -> p.userId().equals(userId));
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public InterviewType getType() {
        return type;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public String getLocationOrLink() {
        return locationOrLink;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Set<InterviewParticipant> getParticipants() {
        return Set.copyOf(participants);
    }
}
