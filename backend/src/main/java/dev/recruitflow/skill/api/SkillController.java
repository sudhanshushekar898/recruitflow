package dev.recruitflow.skill.api;

import dev.recruitflow.skill.service.SkillService;
import dev.recruitflow.skill.service.SkillService.SkillView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
class SkillController {

    private final SkillService skills;

    SkillController(SkillService skills) {
        this.skills = skills;
    }

    record CreateSkillRequest(@NotBlank @Size(max = 60) String name, @Size(max = 40) String category) {
    }

    @GetMapping
    List<SkillView> search(@RequestParam(required = false) String q) {
        return skills.search(q);
    }

    @PostMapping
    ResponseEntity<SkillView> create(@Valid @RequestBody CreateSkillRequest request) {
        SkillView created = skills.create(request.name(), request.category());
        return ResponseEntity.created(URI.create("/api/v1/skills/" + created.id())).body(created);
    }
}
