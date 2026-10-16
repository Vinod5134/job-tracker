package com.vinod.jobtracker.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vinod.jobtracker.jobs.SkillMatcher;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationRulesTest {

    @Test
    void emailsOnlyMoveCardsForward() {
        assertThat(ApplicationStatus.APPLIED.canAutoMoveTo(ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.canAutoMoveTo(ApplicationStatus.APPLIED)).isFalse();
        assertThat(ApplicationStatus.INTERVIEW.canAutoMoveTo(ApplicationStatus.REJECTED)).isTrue();
        assertThat(ApplicationStatus.OFFER.canAutoMoveTo(ApplicationStatus.REJECTED)).isFalse();
        assertThat(ApplicationStatus.REJECTED.canAutoMoveTo(ApplicationStatus.INTERVIEW)).isFalse();
    }

    @Test
    void companyNamesMatchAcrossSuffixes() {
        assertThat(CompanyNames.sameCompany("Infosys Ltd", "Infosys Limited")).isTrue();
        assertThat(CompanyNames.sameCompany("Tech Mahindra", "Tech Mahindra Pvt. Ltd.")).isTrue();
        assertThat(CompanyNames.sameCompany("TCS", "Tata Consultancy Services")).isFalse();
        assertThat(CompanyNames.sameCompany("Wipro", "Infosys")).isFalse();
    }

    @Test
    void javaDoesNotMatchJavaScript() {
        SkillMatcher.Match match = SkillMatcher.match(List.of("Java", "Spring Boot", "SQL"),
                "Frontend role: JavaScript, React and SpringBoot basics, MySQL");
        assertThat(match.matched()).containsExactly("Spring Boot");
    }

    @Test
    void fresherDetection() {
        assertThat(SkillMatcher.fresherFriendly("Hiring freshers, 0-1 years")).isTrue();
        assertThat(SkillMatcher.fresherFriendly("Senior engineer, 8+ years")).isFalse();
    }
}
