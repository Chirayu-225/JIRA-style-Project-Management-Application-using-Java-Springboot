package com.issuetracker.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityRepository extends JpaRepository<ActivityLog, Long> {

    // @EntityGraph = "also load the actor in the same query"
    @EntityGraph(attributePaths = "actor")
    Page<ActivityLog> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = "actor")
    List<ActivityLog> findByProjectIdAndIssueIdOrderByCreatedAtDesc(Long projectId, Long issueId);
}
