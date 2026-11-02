package dev.recruitflow.identity.api;

import dev.recruitflow.identity.api.UserDtos.CreateUserRequest;
import dev.recruitflow.identity.api.UserDtos.UpdateRolesRequest;
import dev.recruitflow.identity.api.UserDtos.UpdateUserRequest;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.service.UserService;
import dev.recruitflow.identity.service.UserView;
import dev.recruitflow.shared.paging.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    PageResponse<UserView> list(@RequestParam(required = false) String q,
                                @RequestParam(required = false) Role role,
                                @RequestParam(required = false) Boolean enabled,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                @RequestParam(required = false) String sort) {
        return users.list(q, role, enabled, page, size, sort);
    }

    @PostMapping
    ResponseEntity<UserView> create(@Valid @RequestBody CreateUserRequest request) {
        UserView created = users.create(request.email(), request.firstName(), request.lastName(), request.roles());
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    UserView get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PatchMapping("/{id}")
    UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.updateProfile(id, request.firstName(), request.lastName(), request.version());
    }

    @PutMapping("/{id}/roles")
    UserView replaceRoles(@PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest request) {
        return users.replaceRoles(id, request.roles(), request.version());
    }

    @PostMapping("/{id}/deactivate")
    UserView deactivate(@PathVariable UUID id) {
        return users.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    UserView activate(@PathVariable UUID id) {
        return users.activate(id);
    }
}
