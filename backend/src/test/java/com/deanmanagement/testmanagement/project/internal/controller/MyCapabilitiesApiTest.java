package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectMember;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.internal.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manual is filtered by what the reader can do, so this endpoint decides what they are shown.
 * Getting it wrong is not a security problem — every action is still authorized server-side — but
 * it does mean showing someone instructions they cannot follow, or hiding the chapter they came for.
 */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class MyCapabilitiesApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    private UUID userWithRoles(ProjectRole... roles) {
        User user = new User();
        user.setEmail("cap-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Capability probe");
        user = userRepository.save(user);

        for (ProjectRole role : roles) {
            Project project = new Project();
            project.setName("Cap " + role);
            project.setKey("C" + Integer.toHexString(new java.util.Random().nextInt(0xFFFFF)));
            project = projectRepository.save(project);

            ProjectMember member = new ProjectMember();
            member.setUser(user);
            member.setProject(project);
            member.setRole(role);
            projectMemberRepository.save(member);
        }
        return user.getId();
    }

    @Test
    void reportsEveryRoleHeld_strongestFirst() throws Exception {
        UUID id = userWithRoles(ProjectRole.VIEWER, ProjectRole.ADMIN, ProjectRole.TESTER);

        mockMvc.perform(get("/api/me/capabilities").with(user(id.toString()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.systemAdmin").value(false))
                .andExpect(jsonPath("$.projectRoles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.projectRoles[1]").value("TESTER"))
                .andExpect(jsonPath("$.projectRoles[2]").value("VIEWER"))
                .andExpect(jsonPath("$.highestRole").value("ADMIN"))
                .andExpect(jsonPath("$.projectMemberships").value(3));
    }

    @Test
    void aTesterOnOneProjectCountsAsATester() throws Exception {
        // The manual is instance-wide: someone who can author anywhere needs the authoring chapter,
        // even if they are only a viewer everywhere else.
        UUID id = userWithRoles(ProjectRole.VIEWER, ProjectRole.TESTER);

        mockMvc.perform(get("/api/me/capabilities").with(user(id.toString()).roles("USER")))
                .andExpect(jsonPath("$.highestRole").value("TESTER"));
    }

    @Test
    void aViewerIsOnlyAViewer() throws Exception {
        UUID id = userWithRoles(ProjectRole.VIEWER);

        mockMvc.perform(get("/api/me/capabilities").with(user(id.toString()).roles("USER")))
                .andExpect(jsonPath("$.projectRoles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.projectRoles[1]").doesNotExist())
                .andExpect(jsonPath("$.highestRole").value("VIEWER"));
    }

    @Test
    void aSystemAdminIsTreatedAsAProjectAdminEverywhere() throws Exception {
        // They pass every project check by bypass, so hiding the admin chapters from them would
        // hide exactly the ones they need.
        UUID id = userWithRoles();

        mockMvc.perform(get("/api/me/capabilities")
                        .with(user(id.toString()).roles("USER", "ADMIN")))
                .andExpect(jsonPath("$.systemAdmin").value(true))
                .andExpect(jsonPath("$.highestRole").value("ADMIN"))
                .andExpect(jsonPath("$.projectMemberships").value(0));
    }

    @Test
    void someoneWithNoProjectYetHasNoRole() throws Exception {
        UUID id = userWithRoles();

        mockMvc.perform(get("/api/me/capabilities").with(user(id.toString()).roles("USER")))
                .andExpect(jsonPath("$.projectRoles").isEmpty())
                .andExpect(jsonPath("$.highestRole").doesNotExist())
                .andExpect(jsonPath("$.projectMemberships").value(0));
    }

    @Test
    void anonymousCannotAsk() throws Exception {
        mockMvc.perform(get("/api/me/capabilities"))
                .andExpect(status().isForbidden());
    }
}
