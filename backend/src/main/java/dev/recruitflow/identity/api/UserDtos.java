package dev.recruitflow.identity.api;

import dev.recruitflow.identity.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Request bodies of the /users endpoints. */
final class UserDtos {

    private UserDtos() {}

    record CreateUserRequest(@NotBlank @Email @Size(max = 254) String email,
                             @NotBlank @Size(max = 100) String firstName,
                             @NotBlank @Size(max = 100) String lastName,
                             @NotEmpty Set<Role> roles) {
    }

    record UpdateUserRequest(@Size(min = 1, max = 100) String firstName,
                             @Size(min = 1, max = 100) String lastName,
                             @NotNull Long version) {
    }

    record UpdateRolesRequest(@NotEmpty Set<Role> roles, @NotNull Long version) {
    }
}
