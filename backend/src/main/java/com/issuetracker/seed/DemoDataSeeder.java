package com.issuetracker.seed;

import com.issuetracker.issue.Comment;
import com.issuetracker.issue.CommentRepository;
import com.issuetracker.issue.Issue;
import com.issuetracker.issue.IssuePriority;
import com.issuetracker.issue.IssueRepository;
import com.issuetracker.issue.IssueStatus;
import com.issuetracker.issue.IssueType;
import com.issuetracker.project.Project;
import com.issuetracker.project.ProjectMember;
import com.issuetracker.project.ProjectMemberRepository;
import com.issuetracker.project.ProjectRepository;
import com.issuetracker.project.ProjectRole;
import com.issuetracker.sprint.Sprint;
import com.issuetracker.sprint.SprintRepository;
import com.issuetracker.user.User;
import com.issuetracker.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * When SEED_DEMO=true, creates a demo account with a realistic project on first startup,
 * so recruiters can click "Try the demo" without signing up. Safe to run on every boot.
 */
@Component
@ConditionalOnProperty(name = "app.seed-demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@example.com";
    public static final String DEMO_PASSWORD = "demo12345";
    public static final String DEMO_PROJECT_KEY = "MOB";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final SprintRepository sprints;
    private final IssueRepository issues;
    private final CommentRepository comments;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(UserRepository users, ProjectRepository projects, ProjectMemberRepository members,
                          SprintRepository sprints, IssueRepository issues, CommentRepository comments,
                          PasswordEncoder encoder) {
        this.users = users;
        this.projects = projects;
        this.members = members;
        this.sprints = sprints;
        this.issues = issues;
        this.comments = comments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Skip if already seeded (or if the demo project key is taken in an older database)
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL) || projects.existsByKeyIgnoreCase(DEMO_PROJECT_KEY)) {
            return;
        }
        log.info("Seeding demo data ({} / {})", DEMO_EMAIL, DEMO_PASSWORD);

        User demo = users.save(new User(DEMO_EMAIL, encoder.encode(DEMO_PASSWORD), "Demo User"));
        User priya = users.save(new User("priya@example.com", encoder.encode(DEMO_PASSWORD), "Priya Sharma"));
        User arjun = users.save(new User("arjun@example.com", encoder.encode(DEMO_PASSWORD), "Arjun Rao"));

        Project project = projects.save(new Project(DEMO_PROJECT_KEY, "Mobile App",
                "Sample project: a team shipping the first release of a mobile app.", demo));
        members.save(new ProjectMember(project, demo, ProjectRole.OWNER));
        members.save(new ProjectMember(project, priya, ProjectRole.ADMIN));
        members.save(new ProjectMember(project, arjun, ProjectRole.MEMBER));

        Sprint sprint1 = sprints.save(new Sprint(project, "Sprint 1", "Ship login and the core board"));
        sprint1.start(LocalDate.now().minusDays(4), LocalDate.now().plusDays(10));
        Sprint sprint2 = sprints.save(new Sprint(project, "Sprint 2", "Notifications and polish"));

        Map<IssueStatus, Double> pos = new EnumMap<>(IssueStatus.class);
        Object[][] rows = {
                // title, type, status, priority, points, assignee, sprint
                {"Set up CI pipeline with GitHub Actions", IssueType.TASK, IssueStatus.DONE, IssuePriority.HIGH, 3, arjun, sprint1},
                {"Email + password login screen", IssueType.STORY, IssueStatus.DONE, IssuePriority.HIGHEST, 5, priya, sprint1},
                {"Login button unresponsive on Android 12", IssueType.BUG, IssueStatus.IN_REVIEW, IssuePriority.HIGHEST, 2, arjun, sprint1},
                {"Kanban board drag-and-drop", IssueType.STORY, IssueStatus.IN_PROGRESS, IssuePriority.HIGH, 8, demo, sprint1},
                {"Show assignee avatars on cards", IssueType.TASK, IssueStatus.IN_PROGRESS, IssuePriority.MEDIUM, 2, priya, sprint1},
                {"Issue detail page with comments", IssueType.STORY, IssueStatus.TODO, IssuePriority.HIGH, 5, null, sprint1},
                {"Board crashes when a column is empty", IssueType.BUG, IssueStatus.TODO, IssuePriority.HIGH, 1, arjun, sprint1},
                {"Push notifications for mentions", IssueType.STORY, IssueStatus.TODO, IssuePriority.MEDIUM, 8, null, sprint2},
                {"Dark mode", IssueType.STORY, IssueStatus.TODO, IssuePriority.LOW, 3, null, sprint2},
                {"Export sprint report as CSV", IssueType.TASK, IssueStatus.TODO, IssuePriority.LOW, 2, null, null},
                {"Investigate slow search on large projects", IssueType.TASK, IssueStatus.TODO, IssuePriority.MEDIUM, 3, null, null},
        };
        for (Object[] r : rows) {
            IssueStatus status = (IssueStatus) r[2];
            double p = pos.merge(status, 1000.0, Double::sum);
            Issue issue = issues.save(new Issue(project, project.nextIssueNumber(), (String) r[0],
                    "Sample issue. Open it, edit it, or drag it across the board.",
                    (IssueType) r[1], status, (IssuePriority) r[3], (Integer) r[4],
                    (User) r[5], demo, (Sprint) r[6], p));
            if (status == IssueStatus.IN_REVIEW) {
                comments.save(new Comment(issue, priya, "Reproduced on a Pixel 6. The click handler is attached twice."));
                comments.save(new Comment(issue, arjun, "Fixed in the latest PR, please review."));
            }
        }
    }
}
