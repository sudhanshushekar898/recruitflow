package dev.recruitflow.job.api;

import dev.recruitflow.job.api.JobDtos.CreateClientRequest;
import dev.recruitflow.job.api.JobDtos.UpdateClientRequest;
import dev.recruitflow.job.service.ClientService;
import dev.recruitflow.job.service.JobViews.ClientView;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clients")
class ClientController {

    private final ClientService clients;

    ClientController(ClientService clients) {
        this.clients = clients;
    }

    @GetMapping
    PageResponse<ClientView> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) Boolean active,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false) String sort) {
        return clients.list(q, active, page, size, sort);
    }

    @PostMapping
    ResponseEntity<ClientView> create(@Valid @RequestBody CreateClientRequest r) {
        ClientView created = clients.create(r.name(), r.industry(), r.contactName(), r.contactEmail());
        return ResponseEntity.created(URI.create("/api/v1/clients/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    ClientView get(@PathVariable UUID id) {
        return clients.get(id);
    }

    @PatchMapping("/{id}")
    ClientView update(@PathVariable UUID id, @Valid @RequestBody UpdateClientRequest r) {
        return clients.update(id, r.name(), r.industry(), r.contactName(), r.contactEmail(), r.active(), r.version());
    }
}
