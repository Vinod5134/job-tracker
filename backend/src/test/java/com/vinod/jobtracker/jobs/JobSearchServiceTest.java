package com.vinod.jobtracker.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.vinod.jobtracker.application.JobApplicationRepository;
import com.vinod.jobtracker.config.AppProperties;
import com.vinod.jobtracker.user.AppUser;
import com.vinod.jobtracker.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class JobSearchServiceTest {

    private MockRestServiceServer adzuna;
    private JobSearchService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        adzuna = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        AppProperties props = new AppProperties("http://localhost:5173", "x".repeat(32), 72, "key",
                new AppProperties.Gmail(600000, 30, 50), new AppProperties.Adzuna(" id ", " key ", "in"), "Java");
        UserService users = mock(UserService.class);
        AppUser user = new AppUser();
        user.setSkills("Java, Spring Boot, SQL");
        when(users.get(any())).thenReturn(user);
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        when(apps.findByUserIdOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        service = new JobSearchService(builder.build(), props, users, apps);
    }

    @Test
    void parsesAdzunaReplyWithItsUtf8Charset() {
        String reply = """
                {"count": 1, "results": [{"id": "42", "title": "Java <strong>Developer</strong> Fresher",
                 "company": {"display_name": "Infosys"}, "location": {"display_name": "Bangalore, Karnataka"},
                 "description": "Java, Spring Boot and SQL. 0-1 years.", "redirect_url": "https://www.adzuna.in/land/ad/42",
                 "created": "2026-10-15T10:00:00Z", "salary_min": 350000, "salary_max": 450000}]}
                """;
        adzuna.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.adzuna.com/v1/api/jobs/in/search/1?app_id=id&app_key=key")))
                .andRespond(withSuccess(reply, MediaType.parseMediaType("application/json; charset=utf8")));

        JobSearchService.JobSearchResponse response = service.search(1L, "java developer", "Bangalore", 1);

        assertThat(response.results()).hasSize(1);
        JobSearchService.JobResult job = response.results().get(0);
        assertThat(job.title()).isEqualTo("Java Developer Fresher");
        assertThat(job.matchedSkills()).containsExactly("Java", "Spring Boot", "SQL");
        assertThat(job.fresherFriendly()).isTrue();
    }

    @Test
    void wrongKeysGiveAClearMessage() {
        adzuna.expect(requestTo(org.hamcrest.Matchers.containsString("api.adzuna.com")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"exception\":\"AUTH_FAIL\"}"));

        assertThatThrownBy(() -> service.search(1L, "java", "", 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("rejected the API keys");
    }
}
