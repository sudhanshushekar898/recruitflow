package dev.recruitflow.job.service;

import dev.recruitflow.audit.AuditRecorder;
import dev.recruitflow.identity.CurrentUser;
import dev.recruitflow.job.domain.Client;
import dev.recruitflow.job.repository.ClientRepository;
import dev.recruitflow.job.service.JobViews.ClientView;
import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import dev.recruitflow.shared.paging.Likes;
import dev.recruitflow.shared.paging.PageRequests;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasAnyRole('ADMIN','RECRUITER')")
public class ClientService {

    static final Set<String> SORT_FIELDS = Set.of("name", "industry", "createdAt");

    private final ClientRepository clients;
    private final CurrentUser currentUser;
    private final AuditRecorder audit;

    ClientService(ClientRepository clients, CurrentUser currentUser, AuditRecorder audit) {
        this.clients = clients;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional
    public ClientView create(String name, String industry, String contactName, String contactEmail) {
        if (clients.existsByNameIgnoreCase(name.trim())) {
            throw duplicate();
        }
        Client client = new Client(name, industry, contactName, contactEmail);
        try {
            clients.saveAndFlush(client);
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }
        audit.record("CLIENT_CREATED", currentUser.id(), "CLIENT", client.getId());
        return view(client);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClientView> list(String q, Boolean active, int page, int size, String sort) {
        Pageable pageable = PageRequests.of(page, size, sort, SORT_FIELDS, "name,asc");
        Specification<Client> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String pattern = Likes.contains(q.trim());
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, Likes.ESCAPE),
                        cb.like(cb.lower(root.get("industry")), pattern, Likes.ESCAPE)));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(clients.findAll(spec, pageable), ClientService::view);
    }

    @Transactional(readOnly = true)
    public ClientView get(UUID id) {
        return view(find(id));
    }

    @Transactional
    public ClientView update(UUID id, String name, String industry, String contactName, String contactEmail,
                             Boolean active, Long version) {
        Client client = find(id);
        client.checkVersion(version);
        if (name != null && !name.trim().equalsIgnoreCase(client.getName()) && clients.existsByNameIgnoreCase(name.trim())) {
            throw duplicate();
        }
        client.update(name, industry, contactName, contactEmail, active);
        clients.saveAndFlush(client);
        audit.record("CLIENT_UPDATED", currentUser.id(), "CLIENT", id);
        return view(client);
    }

    private Client find(UUID id) {
        return clients.findById(id).orElseThrow(() -> new DomainException(ErrorCode.RESOURCE_NOT_FOUND, "Client not found."));
    }

    private static DomainException duplicate() {
        return new DomainException(ErrorCode.DUPLICATE_CLIENT, "A client with this name already exists.", Map.of());
    }

    static ClientView view(Client c) {
        return new ClientView(c.getId(), c.getName(), c.getIndustry(), c.getContactName(), c.getContactEmail(),
                c.isActive(), c.getCreatedAt(), c.getVersion());
    }
}
