package dev.recruitflow.job.domain;

import dev.recruitflow.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An employer the consultancy recruits for. */
@Entity
@Table(name = "clients")
public class Client extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String industry;

    @Column(length = 150)
    private String contactName;

    @Column(length = 254)
    private String contactEmail;

    private boolean active = true;

    protected Client() {}

    public Client(String name, String industry, String contactName, String contactEmail) {
        this.name = name.trim();
        this.industry = clean(industry);
        this.contactName = clean(contactName);
        this.contactEmail = contactEmail == null || contactEmail.isBlank() ? null : contactEmail.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String clean(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    /** Null leaves a field unchanged; an empty string clears an optional one. */
    public void update(String name, String industry, String contactName, String contactEmail, Boolean active) {
        if (name != null) {
            this.name = name.trim();
        }
        if (industry != null) {
            this.industry = clean(industry);
        }
        if (contactName != null) {
            this.contactName = clean(contactName);
        }
        if (contactEmail != null) {
            this.contactEmail = clean(contactEmail) == null ? null : contactEmail.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (active != null) {
            this.active = active;
        }
    }

    public String getName() {
        return name;
    }

    public String getIndustry() {
        return industry;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public boolean isActive() {
        return active;
    }
}
