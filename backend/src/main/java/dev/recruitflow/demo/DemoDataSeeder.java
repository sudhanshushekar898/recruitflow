package dev.recruitflow.demo;

import dev.recruitflow.shared.persistence.Ids;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a realistic but entirely synthetic dataset so the app can be explored and demoed without real
 * personal data: staff, client companies, jobs, candidates, applications and a backdated pipeline history.
 * Deterministic (fixed random seed) and idempotent (does nothing if data already exists).
 * Plain SQL on purpose: this is setup tooling, not a module, so it does not reach into any module's classes.
 * Candidate emails use the reserved example.com domain, so no real address can ever receive mail.
 */
@Component
public class DemoDataSeeder {

    public static final String DEMO_PASSWORD = "Demo-Passphrase-2026";

    public record Summary(int users, int clients, int jobs, int candidates, int applications, int interviews) {
    }

    private static final String[] FIRST = {"Aarav", "Priya", "Liam", "Emma", "Noah", "Olivia", "Mateo", "Sofia", "Kenji", "Yuki",
            "Amara", "Chidi", "Fatima", "Omar", "Hana", "Lukas", "Ingrid", "Mei", "Ravi", "Anika", "Diego", "Lucia", "Ethan",
            "Zara", "Tariq", "Elena", "Jonas", "Nia", "Samir", "Clara", "Felix", "Leila", "Marco", "Tess", "Ibrahim", "Alice",
            "Yusuf", "Freya", "Dev", "Isla"};
    private static final String[] LAST = {"Sharma", "Patel", "Okafor", "Nguyen", "Garcia", "Kowalski", "Tanaka", "Silva", "Haddad",
            "Johansson", "Fischer", "Rossi", "Murphy", "Khan", "Ahmed", "Costa", "Dubois", "Novak", "Andersen", "Moreau",
            "Ivanov", "Lopez", "Smith", "Brown", "Williams", "Chen", "Singh", "Mensah", "Papadopoulos", "Yilmaz", "Bauer",
            "Larsen", "Santos", "Hughes", "Walsh", "Reid", "Bose", "Nakamura", "Adeyemi", "Cohen"};
    private static final String[] LOCATIONS = {"London", "Manchester", "Leeds", "Bristol", "Edinburgh", "Birmingham", "Dublin", "Berlin", "Remote"};
    private static final String[] COMPANIES = {"Acme Software", "Bluepeak Labs", "Cobalt Systems", "Delta Works", "Evergreen IT",
            "Fjord Digital", "Granite Tech", "Harbor Data", "Ionic Cloud", "Juniper Apps", "Keystone Soft", "Lumen Labs"};
    private static final String[] SOURCES = {"LINKEDIN", "REFERRAL", "JOB_BOARD", "AGENCY", "OTHER"};
    private static final String[] NOTES = {"Spoke by phone; open to hybrid roles and a notice period of one month.",
            "Strong communicator, good portfolio. Salary expectation is in line with our bands.",
            "Wants to move into a lead role within two years.", "Prefers remote-first teams; will relocate for the right offer.",
            "Referred by a former colleague. Follow up next week.", "Currently interviewing elsewhere; needs a quick process.",
            "Impressive depth in distributed systems; light on cloud tooling."};
    private static final String[] REJECTION = {"SKILLS_MISMATCH", "EXPERIENCE", "SALARY_EXPECTATIONS", "CULTURE_FIT",
            "POSITION_FILLED", "CANDIDATE_UNRESPONSIVE", "OTHER"};
    /** Pipeline stages in order; a path through them ends wherever the application currently is. */
    private static final String[] STAGES = {"NEW", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED"};

    /** Role families: skills a typical candidate or job in that family has, and title stems by seniority. */
    private record Family(String key, List<String> skills, String[] titles) {
    }

    private static final List<Family> FAMILIES = List.of(
            new Family("backend", List.of("java", "spring boot", "hibernate", "postgresql", "rest apis", "microservices", "docker", "aws", "testing", "kafka"),
                    new String[]{"Java Developer", "Backend Engineer", "Software Engineer"}),
            new Family("frontend", List.of("react", "typescript", "javascript", "rest apis", "testing", "node.js", "angular"),
                    new String[]{"Frontend Developer", "React Developer", "UI Engineer"}),
            new Family("devops", List.of("docker", "kubernetes", "aws", "terraform", "ci/cd", "github actions", "azure"),
                    new String[]{"DevOps Engineer", "Platform Engineer", "Site Reliability Engineer"}),
            new Family("data", List.of("python", "sql", "postgresql", "mongodb", "kafka", "aws"),
                    new String[]{"Data Engineer", "Data Analyst", "Analytics Engineer"}),
            new Family("fullstack", List.of("java", "react", "typescript", "sql", "docker", "spring boot", "rest apis"),
                    new String[]{"Full Stack Developer", "Software Engineer", "Web Developer"}));

    private record StaffSpec(String first, String last, String role) {
    }

    private static final List<StaffSpec> STAFF = List.of(
            new StaffSpec("Maya", "Chen", "RECRUITER"), new StaffSpec("Tomas", "Novak", "RECRUITER"),
            new StaffSpec("Aisha", "Khan", "RECRUITER"), new StaffSpec("Leo", "Martin", "INTERVIEWER"),
            new StaffSpec("Nina", "Rossi", "INTERVIEWER"));

    private record ClientSpec(String name, String industry) {
    }

    private static final List<ClientSpec> CLIENTS = List.of(
            new ClientSpec("Northwind Fintech", "Financial services"), new ClientSpec("Brightwave Health", "Healthcare technology"),
            new ClientSpec("Atlas Logistics", "Logistics"), new ClientSpec("Helix Retail", "Retail and e-commerce"),
            new ClientSpec("Quantum Energy", "Energy"), new ClientSpec("Orbit Media", "Media"),
            new ClientSpec("Summit Insurance", "Insurance"), new ClientSpec("Verdant Mobility", "Mobility"));

    /** client index, title, family, location, type, salary min, salary max, openings, status */
    private record JobSpec(int client, String title, String family, String location, String type, int min, int max, int openings, String status) {
    }

    private static final List<JobSpec> JOBS = List.of(
            new JobSpec(0, "Senior Java Engineer", "backend", "London", "FULL_TIME", 75000, 95000, 2, "OPEN"),
            new JobSpec(0, "Data Engineer", "data", "London", "FULL_TIME", 65000, 85000, 1, "OPEN"),
            new JobSpec(1, "Full Stack Developer", "fullstack", "Manchester", "FULL_TIME", 55000, 72000, 2, "OPEN"),
            new JobSpec(1, "Platform Engineer", "devops", "Remote", "FULL_TIME", 70000, 90000, 1, "OPEN"),
            new JobSpec(2, "Backend Engineer", "backend", "Leeds", "FULL_TIME", 55000, 70000, 3, "OPEN"),
            new JobSpec(2, "Site Reliability Engineer", "devops", "Birmingham", "CONTRACT", 450, 600, 1, "ON_HOLD"),
            new JobSpec(3, "Frontend Developer", "frontend", "Bristol", "FULL_TIME", 50000, 65000, 2, "OPEN"),
            new JobSpec(3, "React Developer", "frontend", "Remote", "CONTRACT", 400, 520, 1, "OPEN"),
            new JobSpec(4, "Software Engineer", "backend", "Edinburgh", "FULL_TIME", 58000, 76000, 2, "OPEN"),
            new JobSpec(5, "Analytics Engineer", "data", "London", "FULL_TIME", 60000, 78000, 1, "CLOSED"),
            new JobSpec(6, "Java Developer", "backend", "Dublin", "FULL_TIME", 60000, 80000, 2, "CLOSED"),
            new JobSpec(6, "DevOps Engineer", "devops", "Remote", "FULL_TIME", 65000, 85000, 1, "OPEN"),
            new JobSpec(7, "Web Developer", "fullstack", "Berlin", "PART_TIME", 35000, 45000, 1, "ON_HOLD"),
            new JobSpec(7, "Lead Backend Engineer", "backend", "London", "FULL_TIME", 90000, 115000, 1, "DRAFT"));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final Clock clock;
    /** Per-run state: who can sit on panels, and the next free slot for upcoming interviews (avoids double-booking). */
    private List<UUID> interviewers = List.of();
    private int upcomingSlot;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder encoder, Clock clock) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.clock = clock;
    }

    /** Returns null when demo data is already there (any client exists) and nothing was done. */
    @Transactional
    public Summary seed() {
        Random rnd = new Random(2026);
        Instant now = clock.instant();
        List<UUID> recruiters = new ArrayList<>();
        interviewers = new ArrayList<>();
        upcomingSlot = 0;
        if (count("clients") > 0) {
            // Data from an earlier version of the seeder: add only what that version did not create yet.
            if (count("interviews") == 0 && count("applications") > 0) {
                seedStaff(recruiters);
                return new Summary(0, 0, 0, 0, 0, backfillInterviews(rnd, now));
            }
            return null;
        }
        Map<String, UUID> skillIds = skillIds();
        int users = seedStaff(recruiters);
        List<UUID> clientIds = seedClients(now);
        List<CandidateRow> candidates = seedCandidates(rnd, now, recruiters, skillIds);
        List<JobRow> jobs = seedJobs(rnd, now, clientIds, recruiters, skillIds);
        int applications = seedApplications(rnd, now, jobs, candidates, recruiters);
        return new Summary(users, clientIds.size(), jobs.size(), candidates.size(), applications, count("interviews"));
    }

    private int backfillInterviews(Random rnd, Instant now) {
        jdbc.query("""
                select a.id, a.status,
                       (select min(h.changed_at) from application_status_history h
                        where h.application_id = a.id and h.to_status = 'INTERVIEW') as reached
                from applications a
                where exists (select 1 from application_status_history h where h.application_id = a.id and h.to_status = 'INTERVIEW')
                order by a.applied_at""",
                rs -> {
                    seedInterviews(rnd, now, rs.getObject("id", UUID.class), rs.getTimestamp("reached").toInstant(), rs.getString("status"));
                });
        return count("interviews");
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return n == null ? 0 : n;
    }

    private Map<String, UUID> skillIds() {
        Map<String, UUID> ids = new HashMap<>();
        jdbc.query("select id, name from skills", rs -> {
            ids.put(rs.getString("name"), rs.getObject("id", UUID.class));
        });
        return ids;
    }

    private int seedStaff(List<UUID> recruiters) {
        String hash = encoder.encode(DEMO_PASSWORD);
        int created = 0;
        for (StaffSpec s : STAFF) {
            String email = (s.first() + "." + s.last()).toLowerCase(Locale.ROOT) + "@demo.recruitflow.local";
            List<UUID> existing = jdbc.query("select id from users where lower(email) = ?", (rs, i) -> rs.getObject("id", UUID.class), email);
            UUID id;
            if (existing.isEmpty()) {
                id = Ids.newId();
                jdbc.update("insert into users (id, email, password_hash, first_name, last_name) values (?,?,?,?,?)",
                        id, email, hash, s.first(), s.last());
                jdbc.update("insert into user_roles (user_id, role) values (?,?)", id, s.role());
                created++;
            } else {
                id = existing.get(0);
            }
            if (s.role().equals("RECRUITER")) {
                recruiters.add(id);
            } else if (s.role().equals("INTERVIEWER")) {
                interviewers.add(id);
            }
        }
        return created;
    }

    private List<UUID> seedClients(Instant now) {
        List<UUID> ids = new ArrayList<>();
        for (ClientSpec c : CLIENTS) {
            UUID id = Ids.newId();
            String slug = c.name().toLowerCase(Locale.ROOT).replace(' ', '-');
            jdbc.update("insert into clients (id, name, industry, contact_name, contact_email, created_at, updated_at) values (?,?,?,?,?,?,?)",
                    id, c.name(), c.industry(), "Hiring team", "talent@" + slug + ".example.com", ts(now.minus(120, ChronoUnit.DAYS)), ts(now.minus(120, ChronoUnit.DAYS)));
            ids.add(id);
        }
        return ids;
    }

    private record CandidateRow(UUID id, String family, int years, Instant createdAt) {
    }

    private List<CandidateRow> seedCandidates(Random rnd, Instant now, List<UUID> recruiters, Map<String, UUID> skillIds) {
        List<CandidateRow> rows = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        for (int i = 0; i < 150; i++) {
            Family family = FAMILIES.get(rnd.nextInt(FAMILIES.size()));
            String first = FIRST[rnd.nextInt(FIRST.length)];
            String last = LAST[rnd.nextInt(LAST.length)];
            String email = (first + "." + last).toLowerCase(Locale.ROOT) + (10 + rnd.nextInt(90)) + "@example.com";
            if (!emails.add(email)) {
                continue;
            }
            int years = 1 + rnd.nextInt(14);
            String seniority = years < 3 ? "Junior " : years < 6 ? "" : years < 10 ? "Senior " : "Lead ";
            String title = seniority + family.titles()[rnd.nextInt(family.titles().length)];
            Instant created = now.minus(1 + rnd.nextInt(90), ChronoUnit.DAYS).minus(rnd.nextInt(600), ChronoUnit.MINUTES);
            UUID id = Ids.newId();
            String status = rnd.nextInt(100) < 5 ? "ARCHIVED" : rnd.nextInt(100) < 3 ? "DO_NOT_CONTACT" : "ACTIVE";
            jdbc.update("""
                    insert into candidates (id, first_name, last_name, email, phone, current_title, current_company, location,
                                            years_experience, source, summary, status, owner_id, retention_until, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    id, first, last, email, "+44 7700 9" + String.format("%05d", rnd.nextInt(100000)), title,
                    COMPANIES[rnd.nextInt(COMPANIES.length)], LOCATIONS[rnd.nextInt(LOCATIONS.length)], years,
                    SOURCES[rnd.nextInt(SOURCES.length)], summaryFor(family, years), status,
                    recruiters.get(rnd.nextInt(recruiters.size())), ts(created.atZone(ZoneOffset.UTC).plusMonths(24).toInstant()),
                    ts(created), ts(created));

            List<String> pool = new ArrayList<>(family.skills());
            Collections.shuffle(pool, rnd);
            int skillCount = 4 + rnd.nextInt(4);
            String[] levels = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"};
            for (String skill : pool.subList(0, Math.min(skillCount, pool.size()))) {
                int skillYears = Math.max(1, Math.min(years, 1 + rnd.nextInt(years)));
                jdbc.update("insert into candidate_skills (candidate_id, skill_id, years, level) values (?,?,?,?)",
                        id, skillIds.get(skill), skillYears, levels[Math.min(3, skillYears / 3)]);
            }
            if (rnd.nextInt(100) < 60) {
                boolean consent = rnd.nextBoolean();
                jdbc.update("insert into candidate_consents (candidate_id, legal_basis, given_at, evidence, created_at, updated_at) values (?,?,?,?,?,?)",
                        id, consent ? "CONSENT" : "LEGITIMATE_INTEREST", ts(created), consent ? "Email confirmation on file" : "Candidate applied directly",
                        ts(created), ts(created));
            }
            if (rnd.nextInt(100) < 30) {
                jdbc.update("insert into candidate_notes (id, candidate_id, author_id, body, created_at, updated_at) values (?,?,?,?,?,?)",
                        Ids.newId(), id, recruiters.get(rnd.nextInt(recruiters.size())), NOTES[rnd.nextInt(NOTES.length)],
                        ts(created.plus(1, ChronoUnit.DAYS)), ts(created.plus(1, ChronoUnit.DAYS)));
            }
            rows.add(new CandidateRow(id, family.key(), years, created));
        }
        return rows;
    }

    private static String summaryFor(Family family, int years) {
        return years + " years of experience in " + family.key().replace("fullstack", "full stack") + " development. "
                + "Comfortable owning features end to end and working in small agile teams.";
    }

    private record JobRow(UUID id, JobSpec spec, UUID owner, Instant openedAt, Instant closedAt) {
    }

    private List<JobRow> seedJobs(Random rnd, Instant now, List<UUID> clientIds, List<UUID> recruiters, Map<String, UUID> skillIds) {
        List<JobRow> rows = new ArrayList<>();
        int index = 0;
        for (JobSpec spec : JOBS) {
            UUID id = Ids.newId();
            UUID owner = recruiters.get(index++ % recruiters.size());
            Instant created = now.minus(60 - rnd.nextInt(20), ChronoUnit.DAYS);
            boolean opened = !spec.status().equals("DRAFT");
            Instant openedAt = opened ? created.plus(1, ChronoUnit.DAYS) : null;
            Instant closedAt = spec.status().equals("CLOSED") ? now.minus(5 + rnd.nextInt(10), ChronoUnit.DAYS) : null;
            boolean contract = spec.type().equals("CONTRACT");
            jdbc.update("""
                    insert into jobs (id, client_id, title, description, location, employment_type, salary_min, salary_max, currency,
                                      openings, status, owner_id, opened_at, closed_at, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                    id, clientIds.get(spec.client()), spec.title(),
                    "We are hiring a " + spec.title() + " to join a growing team. You will design, build and run production services, "
                            + "work closely with product, and mentor colleagues. Day rate roles are outside IR35 review where noted.",
                    spec.location(), spec.type(), BigDecimal.valueOf(spec.min()), BigDecimal.valueOf(spec.max()),
                    "GBP", spec.openings(), spec.status(), owner, ts(openedAt), ts(closedAt), ts(created), ts(created));
            if (contract) {
                jdbc.update("update jobs set description = description || ' Contract rate shown per day.' where id = ?", id);
            }
            Family family = FAMILIES.stream().filter(f -> f.key().equals(spec.family())).findFirst().orElseThrow();
            List<String> pool = new ArrayList<>(family.skills());
            Collections.shuffle(pool, rnd);
            for (int s = 0; s < Math.min(6, pool.size()); s++) {
                boolean required = s < 3;
                jdbc.update("insert into job_skills (job_id, skill_id, importance, min_years, weight) values (?,?,?,?,?)",
                        id, skillIds.get(pool.get(s)), required ? "REQUIRED" : "NICE_TO_HAVE", required ? 2 + rnd.nextInt(4) : null, required ? 4 + rnd.nextInt(2) : 2);
            }
            rows.add(new JobRow(id, spec, owner, openedAt, closedAt));
        }
        return rows;
    }

    private int seedApplications(Random rnd, Instant now, List<JobRow> jobs, List<CandidateRow> candidates, List<UUID> recruiters) {
        int total = 0;
        for (JobRow job : jobs) {
            if (job.spec().status().equals("DRAFT")) {
                continue;
            }
            List<CandidateRow> matching = new ArrayList<>(candidates.stream().filter(c -> c.family().equals(job.spec().family())).toList());
            List<CandidateRow> others = new ArrayList<>(candidates.stream().filter(c -> !c.family().equals(job.spec().family())).toList());
            Collections.shuffle(matching, rnd);
            Collections.shuffle(others, rnd);
            int target = 7 + rnd.nextInt(8);
            List<CandidateRow> picked = new ArrayList<>(matching.subList(0, Math.min(target - 2, matching.size())));
            picked.addAll(others.subList(0, 2));

            int hired = 0;
            boolean closed = job.spec().status().equals("CLOSED");
            for (CandidateRow candidate : picked) {
                String finalStatus = pickFinalStatus(rnd, closed, hired, job.spec().openings());
                if (finalStatus.equals("HIRED")) {
                    hired++;
                }
                total += insertApplication(rnd, now, job, candidate, finalStatus, recruiters);
            }
            // A closed job must have filled at least one opening, or "closed" would be meaningless in the funnel.
            if (closed && hired == 0 && !picked.isEmpty()) {
                CandidateRow extra = others.get(2);
                total += insertApplication(rnd, now, job, extra, "HIRED", recruiters);
            }
        }
        return total;
    }

    private static String pickFinalStatus(Random rnd, boolean closed, int hiredSoFar, int openings) {
        int roll = rnd.nextInt(100);
        if (closed) {
            return roll < 45 && hiredSoFar < openings ? "HIRED" : roll < 85 ? "REJECTED" : "WITHDRAWN";
        }
        String status = roll < 14 ? "NEW" : roll < 28 ? "SCREENING" : roll < 42 ? "SHORTLISTED" : roll < 56 ? "INTERVIEW"
                : roll < 63 ? "OFFER" : roll < 71 ? "HIRED" : roll < 92 ? "REJECTED" : "WITHDRAWN";
        return status.equals("HIRED") && hiredSoFar >= openings ? "OFFER" : status;
    }

    private int insertApplication(Random rnd, Instant now, JobRow job, CandidateRow candidate, String finalStatus, List<UUID> recruiters) {
        Instant earliest = latest(job.openedAt() == null ? now.minus(30, ChronoUnit.DAYS) : job.openedAt(), candidate.createdAt());
        long span = Math.max(2, ChronoUnit.DAYS.between(earliest, now) - 2);
        Instant appliedAt = earliest.plus(rnd.nextInt((int) span), ChronoUnit.DAYS);
        UUID owner = rnd.nextInt(100) < 70 ? job.owner() : recruiters.get(rnd.nextInt(recruiters.size()));

        // The path through the pipeline: up to the stage reached, then a rejection or withdrawal if that is the end.
        List<String> path = new ArrayList<>();
        path.add("NEW");
        int reached;
        if (finalStatus.equals("REJECTED") || finalStatus.equals("WITHDRAWN")) {
            reached = rnd.nextInt(4); // NEW .. INTERVIEW
        } else {
            reached = java.util.Arrays.asList(STAGES).indexOf(finalStatus);
        }
        for (int s = 1; s <= reached; s++) {
            path.add(STAGES[s]);
        }
        if (finalStatus.equals("REJECTED") || finalStatus.equals("WITHDRAWN")) {
            path.add(finalStatus);
        }

        String reason = finalStatus.equals("REJECTED") ? REJECTION[rnd.nextInt(REJECTION.length)] : null;
        List<Instant> times = new ArrayList<>();
        Instant t = appliedAt;
        for (int i = 0; i < path.size(); i++) {
            times.add(t);
            t = t.plus(1 + rnd.nextInt(5), ChronoUnit.DAYS).plus(rnd.nextInt(600), ChronoUnit.MINUTES);
            if (t.isAfter(now)) {
                t = now.minus(rnd.nextInt(300), ChronoUnit.MINUTES);
            }
        }
        // Keep times non-decreasing even when clipped to "now".
        for (int i = 1; i < times.size(); i++) {
            if (times.get(i).isBefore(times.get(i - 1))) {
                times.set(i, times.get(i - 1));
            }
        }
        UUID applicationId = Ids.newId();
        Instant last = times.get(times.size() - 1);
        jdbc.update("""
                insert into applications (id, candidate_id, job_id, status, rejection_reason, source, owner_id, applied_at,
                                          status_changed_at, created_at, updated_at)
                values (?,?,?,?,?,?,?,?,?,?,?)""",
                applicationId, candidate.id(), job.id(), finalStatus, reason, SOURCES[rnd.nextInt(SOURCES.length)], owner,
                ts(appliedAt), ts(last), ts(appliedAt), ts(last));
        for (int i = 0; i < path.size(); i++) {
            jdbc.update("insert into application_status_history (id, application_id, from_status, to_status, changed_by, changed_at, comment) values (?,?,?,?,?,?,?)",
                    Ids.newId(), applicationId, i == 0 ? null : path.get(i - 1), path.get(i), owner, ts(times.get(i)),
                    path.get(i).equals("REJECTED") ? "Not progressing: " + reason.toLowerCase(Locale.ROOT).replace('_', ' ') : null);
        }
        int interviewStep = path.indexOf("INTERVIEW");
        if (interviewStep >= 0 && !interviewers.isEmpty()) {
            seedInterviews(rnd, now, applicationId, times.get(interviewStep), finalStatus);
        }
        return 1;
    }

    private static final String[] STRENGTHS = {"Clear communicator; explained trade-offs well.", "Strong system design and testing habits.",
            "Solid fundamentals and good questions about the team.", "Hands-on experience matches the role closely."};
    private static final String[] CONCERNS = {"Limited production cloud experience.", "Notice period is three months.",
            "Less depth in performance tuning than expected.", "Would need support on the front end."};

    /**
     * A completed first round with feedback for applications that got past the interview stage (and for some still
     * in it), plus an upcoming round for half of those still at INTERVIEW, in slots that never overlap.
     */
    private void seedInterviews(Random rnd, Instant now, UUID applicationId, Instant reachedInterview, String finalStatus) {
        boolean stillInterviewing = finalStatus.equals("INTERVIEW");
        int round = 0;
        if (!stillInterviewing || rnd.nextBoolean()) {
            Instant start = reachedInterview.plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
            if (start.isAfter(now.minus(3, ChronoUnit.HOURS))) {
                start = now.minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
            }
            UUID interviewId = insertInterview(applicationId, ++round, rnd.nextBoolean() ? "VIDEO" : "TECHNICAL", start, "COMPLETED", rnd);
            boolean positive = finalStatus.equals("OFFER") || finalStatus.equals("HIRED") || (stillInterviewing && rnd.nextBoolean());
            for (UUID interviewer : jdbc.queryForList("select user_id from interview_participants where interview_id = ?", UUID.class, interviewId)) {
                int rating = positive ? 4 + rnd.nextInt(2) : 1 + rnd.nextInt(3);
                String recommendation = positive ? (rating == 5 ? "STRONG_YES" : "YES") : (rating == 1 ? "STRONG_NO" : "NO");
                Instant submitted = start.plus(2, ChronoUnit.HOURS);
                jdbc.update("insert into interview_feedback (id, interview_id, interviewer_id, rating, recommendation, strengths, concerns, submitted_at, created_at, updated_at) values (?,?,?,?,?,?,?,?,?,?)",
                        Ids.newId(), interviewId, interviewer, rating, recommendation, STRENGTHS[rnd.nextInt(STRENGTHS.length)],
                        CONCERNS[rnd.nextInt(CONCERNS.length)], ts(submitted), ts(submitted), ts(submitted));
            }
        }
        if (stillInterviewing && rnd.nextBoolean()) {
            int slot = upcomingSlot++;
            Instant day = now.truncatedTo(ChronoUnit.DAYS).plus(1 + slot / 4, ChronoUnit.DAYS);
            insertInterview(applicationId, ++round, round == 1 ? "VIDEO" : "ONSITE", day.plus(9 + 2L * (slot % 4), ChronoUnit.HOURS), "SCHEDULED", rnd);
        }
    }

    private UUID insertInterview(UUID applicationId, int round, String type, Instant start, String status, Random rnd) {
        UUID id = Ids.newId();
        jdbc.update("""
                insert into interviews (id, application_id, round_no, type, scheduled_start, scheduled_end, location_or_link, status, created_at, updated_at)
                values (?,?,?,?,?,?,?,?,?,?)""",
                id, applicationId, round, type, ts(start), ts(start.plus(1, ChronoUnit.HOURS)),
                type.equals("ONSITE") ? "Client office, meeting room 2" : "https://meet.example.com/" + id.toString().substring(0, 8),
                status, ts(start.minus(3, ChronoUnit.DAYS)), ts(start.minus(3, ChronoUnit.DAYS)));
        List<UUID> panel = new ArrayList<>(interviewers);
        Collections.shuffle(panel, rnd);
        // Upcoming interviews use one interviewer each so that consecutive slots never double-book anyone.
        int size = status.equals("SCHEDULED") ? 1 : 1 + rnd.nextInt(panel.size());
        for (int i = 0; i < size; i++) {
            jdbc.update("insert into interview_participants (interview_id, user_id, role) values (?,?,?)", id, panel.get(i), i == 0 ? "LEAD" : "PANEL");
        }
        return id;
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
