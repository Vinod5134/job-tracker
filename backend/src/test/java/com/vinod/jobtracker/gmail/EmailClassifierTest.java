package com.vinod.jobtracker.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import com.vinod.jobtracker.application.ApplicationStatus;
import com.vinod.jobtracker.gmail.EmailClassifier.EmailInsight;
import org.junit.jupiter.api.Test;

class EmailClassifierTest {

    private final EmailClassifier classifier = new EmailClassifier();

    @Test
    void naukriApplicationSent() {
        EmailInsight insight = classifier.classify("Naukri <info@naukri.com>",
                "Your application was sent to Infosys", "You applied for Systems Engineer");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(insight.company()).isEqualTo("Infosys");
        assertThat(insight.highConfidence()).isTrue();
    }

    @Test
    void interviewInvitationWithRole() {
        EmailInsight insight = classifier.classify("TCS Careers <careers@tcs.com>",
                "Interview invitation for Java Developer at TCS", "Your interview is scheduled on 20 Oct at 10 AM");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(insight.company()).isEqualTo("TCS");
        assertThat(insight.role()).isEqualTo("Java Developer");
        assertThat(insight.highConfidence()).isTrue();
    }

    @Test
    void rejectionBeatsOtherKeywords() {
        EmailInsight insight = classifier.classify("Wipro Talent Acquisition <noreply@wipro.com>",
                "Update on your application",
                "Thank you for taking the time to interview with us. Unfortunately we will not be moving forward.");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(insight.company()).isEqualTo("Wipro");
        assertThat(insight.highConfidence()).isTrue();
    }

    @Test
    void offerLetter() {
        EmailInsight insight = classifier.classify("HR <hr@capgemini.com>",
                "Offer letter - Capgemini", "We are pleased to offer you the role of Analyst");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.OFFER);
        assertThat(insight.company()).isEqualTo("Capgemini");
    }

    @Test
    void conditionalInterviewIsJustApplied() {
        EmailInsight insight = classifier.classify("Accenture Recruiting <jobs@accenture.com>",
                "Thank you for applying to Accenture",
                "If you are shortlisted, our team will contact you for an interview.");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(insight.company()).isEqualTo("Accenture");
    }

    @Test
    void personNameSenderNeedsReview() {
        EmailInsight insight = classifier.classify("Priya Sharma <priya.sharma@gmail.com>",
                "Interview schedule", "Please join the technical round tomorrow");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(insight.highConfidence()).isFalse();
    }

    @Test
    void jobAlertsAreIgnored() {
        assertThat(classifier.classify("Naukri <jobalerts@naukri.com>",
                "25 new jobs matching your profile", "Apply now to Java Developer roles")).isNull();
    }

    @Test
    void unrelatedEmailIsIgnored() {
        assertThat(classifier.classify("Amazon <orders@amazon.in>", "Your order has shipped", "Arriving Friday")).isNull();
    }

    @Test
    void domainLabelSkipsCountrySuffix() {
        assertThat(EmailClassifier.domainLabel("hr@infosys.co.in")).isEqualTo("infosys");
        assertThat(EmailClassifier.domainLabel("careers@mail.tcs.com")).isEqualTo("tcs");
    }
}
