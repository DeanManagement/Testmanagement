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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of PRD-036: estimates and durations travel on the existing endpoints, bounded. */
@SpringBootTest
@ActiveProfiles("dev")
@AutoConfigureMockMvc
@Transactional
class TimeTrackingApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;

    private String cases;
    private String runs;
    private UUID tester;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Time API");
        project.setKey("TTA");
        project = projectRepository.save(project);
        User user = new User();
        user.setEmail("u-" + UUID.randomUUID() + "@test.local");
        user.setDisplayName("u");
        user.setPasswordHash("x");
        user = userRepository.save(user);
        ProjectMember member = new ProjectMember();
        member.setUser(user);
        member.setProject(project);
        member.setRole(ProjectRole.TESTER);
        memberRepository.save(member);
        tester = user.getId();
        cases = "/api/projects/" + project.getId() + "/test-cases";
        runs = "/api/projects/" + project.getId() + "/test-runs";
    }

    private RequestPostProcessor asTester() {
        return authentication(new UsernamePasswordAuthenticationToken(tester.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private String createCase(String estimateJson) throws Exception {
        return mockMvc.perform(post(cases).with(asTester()).with(csrf()).contentType("application/json")
                        .content("{\"title\":\"Pay\",\"priority\":\"HIGH\",\"status\":\"DRAFT\"" + estimateJson + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aTestCaseCarriesItsEstimate() throws Exception {
        String body = createCase(",\"estimateMinutes\":25");

        assertThat((Integer) JsonPath.read(body, "$.estimateMinutes")).isEqualTo(25);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -5, 1441})
    void anEstimateOutsideOneMinuteToOneDayIsRefusedOnCreate(int minutes) throws Exception {
        mockMvc.perform(post(cases).with(asTester()).with(csrf()).contentType("application/json")
                        .content("{\"title\":\"Pay\",\"priority\":\"HIGH\",\"status\":\"DRAFT\",\"estimateMinutes\":"
                                + minutes + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aResultCarriesItsDurationAndANegativeOneIsRefused() throws Exception {
        String caseId = JsonPath.read(createCase(""), "$.id");
        String run = mockMvc.perform(post(runs).with(asTester()).with(csrf()).contentType("application/json")
                        .content("{\"name\":\"Run\",\"testCaseIds\":[\"" + caseId + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String result = runs + "/" + JsonPath.read(run, "$.id") + "/results/" + JsonPath.read(run, "$.results[0].id");

        mockMvc.perform(put(result).with(asTester()).with(csrf()).contentType("application/json")
                        .content("{\"status\":\"PASSED\",\"durationMs\":90000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMs").value(90000))
                .andExpect(jsonPath("$.executedAt").exists());
        mockMvc.perform(put(result).with(asTester()).with(csrf()).contentType("application/json")
                        .content("{\"status\":\"PASSED\",\"durationMs\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
