package dev.recruitflow.shared.paging;

import dev.recruitflow.shared.error.DomainException;
import dev.recruitflow.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds a safe Pageable: size is capped and sort fields must be whitelisted per endpoint. */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {}

    public static Pageable of(int page, int size, String sort, Set<String> allowedSortFields, String defaultSort) {
        if (page < 0) {
            throw invalid("page", "must be 0 or greater");
        }
        if (size < 1) {
            throw invalid("size", "must be 1 or greater");
        }
        String spec = sort == null || sort.isBlank() ? defaultSort : sort;
        String[] parts = spec.split(",", 2);
        String field = parts[0].trim();
        if (!allowedSortFields.contains(field)) {
            throw invalid("sort", "unsupported sort field; allowed: " + String.join(", ", allowedSortFields.stream().sorted().toList()));
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = switch (parts[1].trim().toLowerCase()) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw invalid("sort", "direction must be asc or desc");
            };
        }
        return PageRequest.of(page, Math.min(size, MAX_SIZE), Sort.by(direction, field).and(Sort.by("id")));
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "Invalid pagination parameter.",
                Map.of("errors", List.of(Map.of("field", field, "message", message))));
    }
}
