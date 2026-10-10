package com.issuetracker.issue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JpaSpecificationExecutor lets IssueService build dynamic WHERE clauses
 * (status? assignee? sprint? text?) without writing one query per combination.
 */
public interface IssueRepository extends JpaRepository<Issue, Long>, JpaSpecificationExecutor<Issue> {

    // Load assignee, reporter and sprint in the same SQL query (avoids N+1 queries on the board).
    @Override
    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint"})
    Page<Issue> findAll(Specification<Issue> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint"})
    List<Issue> findByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"assignee", "reporter", "sprint"})
    Optional<Issue> findByIdAndProjectId(Long id, Long projectId);

    Optional<Issue> findByProjectIdAndNumber(Long projectId, int number);

    List<Issue> findByProjectIdAndStatusOrderByPositionAsc(Long projectId, IssueStatus status);

    @Query("select coalesce(max(i.position), 0.0) from Issue i where i.project.id = :projectId and i.status = :status")
    double maxPosition(@Param("projectId") Long projectId, @Param("status") IssueStatus status);

    /**
     * PostgreSQL full-text search using the generated search_vector column + GIN index.
     * websearch_to_tsquery understands Google-like input: "login bug" -crash "exact phrase".
     * Also falls back to a plain substring match on the title for partial words.
     */
    @Query(value = """
            SELECT i.id FROM issues i
            WHERE i.project_id = :projectId
              AND (i.search_vector @@ websearch_to_tsquery('english', :q)
                   OR i.title ILIKE '%' || :q || '%')
            ORDER BY ts_rank(i.search_vector, websearch_to_tsquery('english', :q)) DESC, i.updated_at DESC
            LIMIT 20
            """, nativeQuery = true)
    List<Long> searchIds(@Param("projectId") Long projectId, @Param("q") String q);

    /** Bulk UPDATE in one statement. flushAutomatically writes pending changes first. */
    @Modifying(flushAutomatically = true)
    @Query("update Issue i set i.sprint = null where i.sprint.id = :sprintId and i.status <> :done")
    int moveUnfinishedToBacklog(@Param("sprintId") Long sprintId, @Param("done") IssueStatus done);

    @Modifying(flushAutomatically = true)
    @Query("update Issue i set i.assignee = null where i.project.id = :projectId and i.assignee.id = :userId")
    int unassignUserInProject(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
