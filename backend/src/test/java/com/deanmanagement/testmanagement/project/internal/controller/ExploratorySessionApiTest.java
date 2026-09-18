package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP wiring of PRD-034: role guards, the image upload and the PRD-017 headers on download. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class ExploratorySessionApiTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;

    private Project project;
    private Project otherProject;
    private UUID tester;
    private UUID viewer;
    private UUID outsider;

    @BeforeEach
    void setUp() {
        project = project("SAPI");
        otherProject = project("SOTH");
        tester = member(project, ProjectRole.TESTER);
        viewer = member(project, ProjectRole.VIEWER);
        outsider = member(otherProject, ProjectRole.ADMIN);
    }

    private Project project(String key) {
        Project p = new Project();
        p.setName(key);
        p.setKey(key);
        return projectRepository.save(p);
    }

    private UUID member(Project target, ProjectRole role) {
        User u = new User();
        u.setEmail("u-" + UUID.randomUUID() + "@test.local");
        u.setDisplayName("u");
        u.setPasswordHash("x");
        u = userRepository.save(u);
        ProjectMember m = new ProjectMember();
        m.setUser(u);
        m.setProject(target);
        m.setRole(role);
        memberRepository.save(m);
        return u.getId();
    }

    /** A String principal, as the JWT filter sets it, so auditing records the note's author. */
    private static RequestPostProcessor as(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private String base(Project p) {
        return "/api/projects/" + p.getId() + "/exploratory-sessions";
    }

    private String createRunningSession() throws Exception {
        String body = mockMvc.perform(post(base(project)).with(as(tester)).with(csrf())
                        .contentType("application/json").content("{\"charter\":\"Explore checkout\",\"timeboxMinutes\":45}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("SAPI-Session-1"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.id");
        mockMvc.perform(post(base(project) + "/" + id + "/start").with(as(tester)).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private String addNote(String sessionId) throws Exception {
        String body = mockMvc.perform(post(base(project) + "/" + sessionId + "/notes").with(as(tester)).with(csrf())
                        .contentType("application/json").content("{\"type\":\"BUG\",\"body\":\"Total off by one\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void aViewerCanReadButNotCreate() throws Exception {
        mockMvc.perform(get(base(project)).with(as(viewer))).andExpect(status().isOk());
        mockMvc.perform(post(base(project)).with(as(viewer)).with(csrf())
                        .contentType("application/json").content("{\"charter\":\"x\",\"timeboxMinutes\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aNonMemberCannotSeeTheProjectsSessions() throws Exception {
        mockMvc.perform(get(base(project)).with(as(outsider))).andExpect(status().isForbidden());
    }

    @Test
    void theTimeboxIsBounded() throws Exception {
        mockMvc.perform(post(base(project)).with(as(tester)).with(csrf())
                        .contentType("application/json").content("{\"charter\":\"x\",\"timeboxMinutes\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noteImagesAreServedWithTheScreenshotHeaders() throws Exception {
        String sessionId = createRunningSession();
        String noteId = addNote(sessionId);
        String imageUrl = base(project) + "/" + sessionId + "/notes/" + noteId + "/image";

        mockMvc.perform(multipart(imageUrl).file(new MockMultipartFile("file", "shot.png", "image/png", PNG))
                        .with(as(tester)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(imageUrl).with(as(viewer)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(header().exists("ETag"));
        mockMvc.perform(get(base(project) + "/" + sessionId).with(as(viewer)))
                .andExpect(jsonPath("$.notes[0].hasImage").value(true));
    }

    @Test
    void anImageThroughAnotherProjectsPathIsNotFound() throws Exception {
        String sessionId = createRunningSession();
        String noteId = addNote(sessionId);

        mockMvc.perform(get(base(otherProject) + "/" + sessionId + "/notes/" + noteId + "/image").with(as(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNonImageUploadIsRefused() throws Exception {
        String sessionId = createRunningSession();
        String noteId = addNote(sessionId);

        mockMvc.perform(multipart(base(project) + "/" + sessionId + "/notes/" + noteId + "/image")
                        .file(new MockMultipartFile("file", "x.html", "text/html", "<script>".getBytes()))
                        .with(as(tester)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mySessionsListsWhatIAmAssigned() throws Exception {
        mockMvc.perform(post(base(project)).with(as(tester)).with(csrf())
                        .contentType("application/json")
                        .content("{\"charter\":\"Mine\",\"timeboxMinutes\":30,\"testerId\":\"" + tester + "\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/exploratory-sessions/assigned-to-me").with(as(tester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].charter").value("Mine"));
    }
}
