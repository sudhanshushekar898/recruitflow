package dev.recruitflow.job.repository;

import dev.recruitflow.job.domain.Client;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    boolean existsByNameIgnoreCase(String name);
}
