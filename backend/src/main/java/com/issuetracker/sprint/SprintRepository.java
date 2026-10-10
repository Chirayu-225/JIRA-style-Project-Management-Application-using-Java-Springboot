package com.issuetracker.sprint;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SprintRepository extends JpaRepository<Sprint, Long> {

    List<Sprint> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    Optional<Sprint> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndStatus(Long projectId, SprintStatus status);
}
