package com.vinod.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinod.jobtracker.user.AppUser;
import com.vinod.jobtracker.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ApplicationService applicationService;

    private Long vinod;
    private Long otherUser;

    @BeforeEach
    void users() {
        vinod = userService.upsertGoogleUser("g-vinod", "vinod@example.com", "Vinod", null).getId();
        otherUser = userService.upsertGoogleUser("g-other", "other@example.com", "Other", null).getId();
    }

    private MockHttpServletRequestBuilder as(Long userId, MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(j -> j.subject(String.valueOf(userId))));
    }

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/api/applications")).andExpect(status().isUnauthorized());
    }

    @Test
    void createMoveAndDelete() throws Exception {
        String body = mvc.perform(as(vinod, post("/api/applications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Infosys\",\"jobRole\":\"Systems Engineer\",\"source\":\"NAUKRI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(as(vinod, patch("/api/applications/" + id + "/status"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"));

        String dashboard = mvc.perform(as(vinod, get("/api/dashboard")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode d = json.readTree(dashboard);
        assertThat(d.get("counts").get("INTERVIEW").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(d.get("recentActivity").size()).isGreaterThan(0);

        mvc.perform(as(vinod, delete("/api/applications/" + id))).andExpect(status().isNoContent());
        mvc.perform(as(vinod, get("/api/applications/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotSeeEachOthersApplications() throws Exception {
        String body = mvc.perform(as(vinod, post("/api/applications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Wipro\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("id").asLong();

        mvc.perform(as(otherUser, get("/api/applications/" + id))).andExpect(status().isNotFound());
        mvc.perform(as(otherUser, delete("/api/applications/" + id))).andExpect(status().isNotFound());
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(as(vinod, post("/api/applications"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"jobUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.company").exists())
                .andExpect(jsonPath("$.fieldErrors.jobUrl").exists());
    }

    @Test
    void undoRemovesCardCreatedByEmailAndRestoresStatus() throws Exception {
        var created = applicationService.applyEmailUpdate(vinod, "Kusama Network", null, ApplicationStatus.APPLIED, false)
                .orElseThrow();
        assertThat(created.created()).isTrue();
        applicationService.revertEmailUpdate(vinod, created.application().id(), true, null, ApplicationStatus.APPLIED);
        mvc.perform(as(vinod, get("/api/applications/" + created.application().id()))).andExpect(status().isNotFound());

        var moved = applicationService.applyEmailUpdate(vinod, "Kusama Network", null, ApplicationStatus.APPLIED, false)
                .orElseThrow();
        var interview = applicationService.applyEmailUpdate(vinod, "Kusama Network Pvt Ltd", null,
                ApplicationStatus.INTERVIEW, false).orElseThrow();
        assertThat(interview.previousStatus()).isEqualTo(ApplicationStatus.APPLIED);
        applicationService.revertEmailUpdate(vinod, moved.application().id(), false,
                interview.previousStatus(), ApplicationStatus.INTERVIEW);
        mvc.perform(as(vinod, get("/api/applications/" + moved.application().id())))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void meReturnsDefaultSkills() throws Exception {
        AppUser user = userService.get(vinod);
        mvc.perform(as(user.getId(), get("/api/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("vinod@example.com"))
                .andExpect(jsonPath("$.skills[0]").value("Java"))
                .andExpect(jsonPath("$.gmail.connected").value(false));
    }
}
