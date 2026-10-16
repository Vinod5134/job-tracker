package com.vinod.jobtracker.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.vinod.jobtracker.jobs.JobAlertParser.ParsedJob;
import com.vinod.jobtracker.jobs.JobAlertParser.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobAlertParserTest {

    private final JobAlertParser parser = new JobAlertParser();

    @Test
    void linkedInAlertEmail() {
        String html = """
                <table><tr><td>
                  <table><tr>
                    <td><a href="https://www.linkedin.com/comm/jobs/view/4012345678/?trackingId=abc&refId=x"><img alt="Infosys logo"></a></td>
                    <td>
                      <a href="https://www.linkedin.com/comm/jobs/view/4012345678/?trackingId=abc">Java Developer</a>
                      <p>Infosys · Bengaluru, Karnataka, India (On-site)</p>
                      <p>Actively recruiting</p>
                    </td>
                  </tr></table>
                  <table><tr>
                    <td>
                      <a href="https://www.linkedin.com/comm/jobs/view/4098765432/?trackingId=def">Associate Software Engineer</a>
                      <p>Accenture</p>
                      <p>Hyderabad, Telangana, India</p>
                      <p>Easy Apply</p>
                    </td>
                  </tr></table>
                  <a href="https://www.linkedin.com/comm/jobs/search?keywords=java">See all jobs</a>
                  <a href="https://www.linkedin.com/comm/jobs/alerts">Manage alerts</a>
                </td></tr></table>
                """;

        List<ParsedJob> jobs = parser.parse(html);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0)).isEqualTo(new ParsedJob(Source.LINKEDIN, "Java Developer", "Infosys",
                "Bengaluru, Karnataka, India (On-site)", "https://www.linkedin.com/jobs/view/4012345678/"));
        assertThat(jobs.get(1).title()).isEqualTo("Associate Software Engineer");
        assertThat(jobs.get(1).company()).isEqualTo("Accenture");
        assertThat(jobs.get(1).location()).isEqualTo("Hyderabad, Telangana, India");
    }

    @Test
    void naukriAlertEmail() {
        String html = """
                <div>
                  <div class="job">
                    <a href="https://www.naukri.com/job-listings-java-developer-tcs-hyderabad-0-to-2-years-150925500123?src=jobalert&amp;sid=1">Java Developer</a>
                    <div>Tata Consultancy Services</div>
                    <div>4.1 ★</div>
                    <div>0-2 Yrs</div>
                    <div>Not disclosed</div>
                    <div>Hyderabad</div>
                    <a href="https://www.naukri.com/job-listings-java-developer-tcs-hyderabad-0-to-2-years-150925500123?src=jobalert">Apply</a>
                  </div>
                  <div class="job">
                    <a href="https://www.naukri.com/job-listings-spring-boot-developer-wipro-bengaluru-0-to-1-years-160925700456">Spring Boot Developer</a>
                    <div>Wipro</div>
                    <div>0-1 Yrs</div>
                    <div>Bengaluru</div>
                  </div>
                  <a href="https://www.naukri.com/mnjuser/recommendedjobs">View all jobs</a>
                </div>
                """;

        List<ParsedJob> jobs = parser.parse(html);

        assertThat(jobs).hasSize(2);
        assertThat(jobs.get(0)).isEqualTo(new ParsedJob(Source.NAUKRI, "Java Developer", "Tata Consultancy Services",
                "Hyderabad", "https://www.naukri.com/job-listings-java-developer-tcs-hyderabad-0-to-2-years-150925500123"));
        assertThat(jobs.get(1).company()).isEqualTo("Wipro");
        assertThat(jobs.get(1).location()).isEqualTo("Bengaluru");
    }

    @Test
    void trackingRedirectsAreUnwrapped() {
        String html = "<a href=\"https://click.naukri.com/r?u=https%3A%2F%2Fwww.naukri.com%2Fjob-listings-java-trainee-infosys-pune-0-to-1-years-170925800789%3Fsrc%3Dmail\">Java Trainee</a>";

        List<ParsedJob> jobs = parser.parse(html);

        assertThat(jobs).extracting(ParsedJob::url)
                .containsExactly("https://www.naukri.com/job-listings-java-trainee-infosys-pune-0-to-1-years-170925800789");
    }

    @Test
    void buttonOnlyLinksFallBackToTitleFromUrl() {
        String html = "<a href=\"https://www.naukri.com/job-listings-java-developer-infosys-bengaluru-0-to-2-years-150925500999\">Apply now</a>";

        assertThat(parser.parse(html)).extracting(ParsedJob::title).containsExactly("Java Developer Infosys Bengaluru");
    }

    @Test
    void emailsWithoutJobLinksGiveNothing() {
        assertThat(parser.parse("<p>Your application was sent to Infosys</p><a href=\"https://www.naukri.com/mnjuser/profile\">Profile</a>"))
                .isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }
}
