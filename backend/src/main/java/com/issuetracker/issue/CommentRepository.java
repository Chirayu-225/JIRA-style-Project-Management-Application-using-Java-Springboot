package com.issuetracker.issue;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    List<Comment> findByIssueIdOrderByCreatedAtAsc(Long issueId);

    Optional<Comment> findByIdAndIssueId(Long id, Long issueId);
}
