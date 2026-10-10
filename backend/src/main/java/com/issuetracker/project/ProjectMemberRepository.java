package com.issuetracker.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    boolean existsByProjectIdAndUserId(Long projectId, Long userId);

    // "join fetch" loads the related rows in the SAME query, avoiding the N+1 query problem
    @Query("select m from ProjectMember m join fetch m.project p join fetch p.owner where m.user.id = :userId order by p.name")
    List<ProjectMember> findAllForUser(@Param("userId") Long userId);

    /** One GROUP BY query for all member counts instead of one COUNT per project. */
    @Query("select m.project.id, count(m) from ProjectMember m where m.project.id in :projectIds group by m.project.id")
    List<Object[]> countByProjectIds(@Param("projectIds") List<Long> projectIds);

    @Query("select m from ProjectMember m join fetch m.user where m.project.id = :projectId order by m.joinedAt")
    List<ProjectMember> findAllForProject(@Param("projectId") Long projectId);

    long countByProjectId(Long projectId);
}
