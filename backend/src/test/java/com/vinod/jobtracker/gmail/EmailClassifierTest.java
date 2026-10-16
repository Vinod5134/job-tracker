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

    // ---- Real noisy emails from Vinod's inbox: none of these may touch the board ----

    @Test
    void reviewSiteMailIsIgnored() {
        assertThat(classifier.classify("AmbitionBox <reviews@ambitionbox.com>",
                "You applied to Tech Mahindra - see what employees have to say!", "")).isNull();
        assertThat(classifier.classify("AmbitionBox <reviews@ambitionbox.com>",
                "You applied to Transdyne IT Services - see what employees have to say!", "")).isNull();
    }

    @Test
    void jobUpdateSpamSitesAreIgnored() {
        assertThat(classifier.classify("Skilluo Job Updates <notify@skilluo.com>",
                "Application Status Update: AI Data Annotation Specialist at Kusama Network", "")).isNull();
        assertThat(classifier.classify("Reshma <reshma@techslash.com>",
                "Hi Vinodpedakota - Register your Club Mahindra Application", "")).isNull();
    }

    @Test
    void naukriWalkInAdsAreIgnored() {
        assertThat(classifier.classify("reminder <reminder@naukri.com>",
                "Reminder: Don't Forget to attend these Walk-in's today", "")).isNull();
        assertThat(classifier.classify("Foundever <kondala.muralikrishnaZm91bmRldmVyLmNvbQ==@naukri.com>",
                "Walk-in interview | International Voice Process | Foundever in Hyderabad", "")).isNull();
    }

    @Test
    void unknownCompanySendersAreIgnored() {
        // A newsletter from some company domain, not a hiring team
        assertThat(classifier.classify("Swiggy <offers@swiggy.in>", "Your offer is waiting", "")).isNull();
    }

    @Test
    void boardInterviewClaimsNeedReview() {
        EmailInsight insight = classifier.classify("Naukri <info@naukri.com>",
                "Interview invitation from Infosys", "");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(insight.highConfidence()).isFalse();
    }

    @Test
    void linkedInConfirmationIsTrusted() {
        EmailInsight insight = classifier.classify("LinkedIn <jobs-noreply@linkedin.com>",
                "Vinod, your application was sent to Capgemini", "");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(insight.company()).isEqualTo("Capgemini");
        assertThat(insight.highConfidence()).isTrue();
    }

    @Test
    void hiringToolUsesEmployerName() {
        EmailInsight insight = classifier.classify("Deloitte Careers <deloitte@myworkday.com>",
                "Thank you for applying", "We have received your application for Analyst");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(insight.company()).isEqualTo("Deloitte");
        assertThat(insight.highConfidence()).isTrue();
    }

    @Test
    void employerTalkingAboutAnotherCompanyIsIgnored() {
        assertThat(classifier.classify("Careers <careers@randomconsultancy.com>",
                "Interview for Java Developer at Infosys", "")).isNull();
    }

    @Test
    void employerReviewMailIsNotFilteredAsMarketing() {
        EmailInsight insight = classifier.classify("TCS Careers <careers@tcs.com>",
                "Register for your online assessment", "Your TCS NQT assessment is scheduled");
        assertThat(insight.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(insight.company()).isEqualTo("TCS");
    }

    @Test
    void noiseCheckForOldReviewItems() {
        assertThat(classifier.isNoise("reminder <reminder@naukri.com>",
                "Reminder: Don't Forget to attend these Walk-in's today")).isTrue();
        assertThat(classifier.isNoise("TCS Careers <careers@tcs.com>", "Interview schedule")).isFalse();
    }

    @Test
    void domainLabelSkipsCountrySuffix() {
        assertThat(EmailClassifier.domainLabel("hr@infosys.co.in")).isEqualTo("infosys");
        assertThat(EmailClassifier.domainLabel("careers@mail.tcs.com")).isEqualTo("tcs");
    }
}
