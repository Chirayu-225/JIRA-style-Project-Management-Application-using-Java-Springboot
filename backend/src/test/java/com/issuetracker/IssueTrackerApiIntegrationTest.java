package com.issuetracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end API tests against a REAL PostgreSQL started in Docker by Testcontainers.
 * (Same database engine as production, so Flyway migrations, full-text search and
 * constraints are all tested for real.)
 */
@SpringBootTest(properties = "app.seed-demo=false")
@AutoConfigureMockMvc
@Testcontainers
class IssueTrackerApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    // ------------------------------------------------------------------ tests

    @Test
    void fullIssueLifecycle() throws Exception {
        String alice = register("Alice");
        long projectId = createProject(alice, uniqueKey());

        long first = createIssue(alice, projectId, "First issue").get("id").asLong();
        JsonNode second = createIssue(alice, projectId, "Login crashes on Android");
        assertThat(second.get("number").asInt()).isEqualTo(2);
        assertThat(second.get("key").asText()).endsWith("-2");

        // drag "second" above "first" in a new column
        call(alice, post("/api/projects/{p}/issues/{i}/move", projectId, second.get("id").asLong()),
                "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        JsonNode moved = body(call(alice, post("/api/projects/{p}/issues/{i}/move", projectId, first),
                "{\"status\":\"IN_PROGRESS\",\"beforeIssueId\":" + second.get("id").asLong() + "}")
                .andExpect(status().isOk()));
        // "second" landed at 1000 in the empty column, so a card dropped above it must sort before it
        assertThat(moved.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(moved.get("position").asDouble()).isLessThan(1000.0);

        // full-text search with stemming: "crashing" finds "crashes"
        call(alice, get("/api/projects/{p}/issues/search?q=crashing", projectId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Login crashes on Android"));

        // comments + activity
        call(alice, post("/api/projects/{p}/issues/{i}/comments", projectId, first), "{\"body\":\"Looking into it\"}")
                .andExpect(status().isCreated());
        call(alice, get("/api/projects/{p}/activity", projectId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(3)));
    }

    @Test
    void staleVersionIsRejectedWith409() throws Exception {
        String alice = register("Alice");
        long projectId = createProject(alice, uniqueKey());
        JsonNode issue = createIssue(alice, projectId, "Edit me");
        long id = issue.get("id").asLong();
        long v0 = issue.get("version").asLong();

        String update = "{\"title\":\"%s\",\"type\":\"TASK\",\"priority\":\"HIGH\",\"status\":\"TODO\",\"version\":%d}";
        call(alice, put("/api/projects/{p}/issues/{i}", projectId, id), update.formatted("Edit 1", v0))
                .andExpect(status().isOk());
        // second editor still holds the old version -> conflict, not a silent overwrite
        call(alice, put("/api/projects/{p}/issues/{i}", projectId, id), update.formatted("Edit 2", v0))
                .andExpect(status().isConflict());
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        String owner = register("Owner");
        String outsider = register("Outsider");
        String viewerEmail = "viewer-" + UUID.randomUUID() + "@test.dev";
        String viewer = register("Viewer", viewerEmail);
        long projectId = createProject(owner, uniqueKey());

        // outsiders can't even see that the project exists
        call(outsider, get("/api/projects/{p}", projectId), null).andExpect(status().isNotFound());
        // no token at all
        mvc.perform(get("/api/projects/{p}", projectId)).andExpect(status().isUnauthorized());

        call(owner, post("/api/projects/{p}/members", projectId),
                "{\"email\":\"" + viewerEmail + "\",\"role\":\"VIEWER\"}").andExpect(status().isCreated());
        call(viewer, get("/api/projects/{p}/issues", projectId), null).andExpect(status().isOk());
        call(viewer, post("/api/projects/{p}/issues", projectId), "{\"title\":\"Nope\",\"type\":\"BUG\"}")
                .andExpect(status().isForbidden());
        call(viewer, delete("/api/projects/{p}", projectId), null).andExpect(status().isForbidden());
    }

    @Test
    void onlyOneActiveSprintAndCompletionReturnsUnfinishedWorkToBacklog() throws Exception {
        String alice = register("Alice");
        long projectId = createProject(alice, uniqueKey());
        long s1 = body(call(alice, post("/api/projects/{p}/sprints", projectId), "{\"name\":\"S1\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        long s2 = body(call(alice, post("/api/projects/{p}/sprints", projectId), "{\"name\":\"S2\"}"))
                .get("id").asLong();

        call(alice, post("/api/projects/{p}/sprints/{s}/start", projectId, s1), null).andExpect(status().isOk());
        call(alice, post("/api/projects/{p}/sprints/{s}/start", projectId, s2), null).andExpect(status().isConflict());

        call(alice, post("/api/projects/{p}/issues", projectId),
                "{\"title\":\"Unfinished\",\"type\":\"TASK\",\"sprintId\":" + s1 + "}").andExpect(status().isCreated());
        call(alice, post("/api/projects/{p}/sprints/{s}/complete", projectId, s1), null).andExpect(status().isOk());
        call(alice, get("/api/projects/{p}/issues?backlog=true", projectId), null)
                .andExpect(jsonPath("$.content[0].title").value("Unfinished"));
    }

    @Test
    void validationErrorsListTheFields() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ------------------------------------------------------------------ helpers

    private String register(String name) throws Exception {
        return register(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@test.dev");
    }

    private String register(String name, String email) throws Exception {
        String req = "{\"email\":\"%s\",\"password\":\"password123\",\"fullName\":\"%s\"}".formatted(email, name);
        JsonNode res = body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated()));
        return res.get("token").asText();
    }

    private long createProject(String token, String key) throws Exception {
        return body(call(token, post("/api/projects"), "{\"key\":\"" + key + "\",\"name\":\"Test project\"}")
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode createIssue(String token, long projectId, String title) throws Exception {
        return body(call(token, post("/api/projects/{p}/issues", projectId),
                "{\"title\":\"" + title + "\",\"type\":\"BUG\",\"priority\":\"HIGH\"}")
                .andExpect(status().isCreated()));
    }

    private ResultActions call(String token,
                               org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String jsonBody) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (jsonBody != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(jsonBody);
        }
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String uniqueKey() {
        return "P" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
