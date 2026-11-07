package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student: {@code POST /students/{studentId}/deactivate}.
 *
 * <p>Seed: John (4) is a student in course section 2 of course 1, on Team1 with Eric (5) and Jerry (6). Bingyang (1) is
 * course 1's admin and is assigned to course section 2; Bill (2) is an instructor of course 1 assigned to course sections
 * 1 and 2; Tim (3) is course 3's admin, assigned only to its course section 3, so he is both an instructor of another
 * course section and the admin of another course.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    static final int JOHN = 4;
    static final int ERIC = 5;
    static final int BINGYANG = 1;
    static final int BILL = 2;
    static final int SECTION_2 = 2;
    static final int NO_SUCH_STUDENT = 99999;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String bingyangToken;
    String billToken;
    String timToken;
    String johnToken;
    String ericToken;

    @BeforeEach
    void setUp() throws Exception {
        this.bingyangToken = login("b.wei@abc.edu");
        this.billToken = login("b.gates@abc.edu");
        this.timToken = login("t.cook@abc.edu");
        this.johnToken = login("j.smith@abc.edu");
        this.ericToken = login("e.hudson@abc.edu");
    }

    String login(String username) throws Exception {
        String body = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(username, "123456")))
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + new JSONObject(body).getJSONObject("data").getString("token");
    }

    ResultActions deactivate(int studentId, String token) throws Exception {
        return this.mockMvc.perform(post(this.baseUrl + "/students/" + studentId + "/deactivate")
                .accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, token));
    }

    void unassign(int instructorId, int sectionId) {
        Section section = this.sectionRepository.findById(sectionId).orElseThrow();
        Instructor instructor = this.instructorRepository.findById(instructorId).orElseThrow();
        section.removeInstructor(instructor);
        this.entityManager.flush();
    }

    boolean johnIsEnabled() {
        // Flush the request's pending writes to the database, then read John back from it rather than from the cache
        this.entityManager.flush();
        this.entityManager.clear();
        return this.studentRepository.findById(JOHN).orElseThrow().isEnabled();
    }

    static void assertForbidden(ResultActions actions) throws Exception {
        actions.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN))
                .andExpect(jsonPath("$.message").value("No permission."));
    }

    // ---- Main success scenario (step 6) ----

    @Test
    void testInstructorAssignedToHerCourseSectionDeactivatesHer() throws Exception {
        deactivate(JOHN, this.billToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(JOHN))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Smith"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(johnIsEnabled()).isFalse();
    }

    @Test
    void testCourseAdminNotAssignedToHerCourseSectionDeactivatesHer() throws Exception {
        unassign(BINGYANG, SECTION_2); // Bingyang is still course 1's admin

        deactivate(JOHN, this.bingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(johnIsEnabled()).isFalse();
    }

    // ---- 6a: another course admin or instructor deactivated her after step 2 ----

    @Test
    void testDeactivatingAnAlreadyDeactivatedStudentLeavesHerDeactivated() throws Exception {
        deactivate(JOHN, this.billToken).andExpect(jsonPath("$.message").value("Deactivate student successfully"));

        deactivate(JOHN, this.bingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Student is already deactivated"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(johnIsEnabled()).isFalse();
    }

    // ---- POST-1 and the Associated Information: her account, team, and work are kept; she can no longer log in ----

    private long count(String jpql) {
        return this.entityManager.createQuery(jpql, Long.class).setParameter("id", JOHN).getSingleResult();
    }

    @Test
    void testDeactivationKeepsHerTeamAndSubmittedWork() throws Exception {
        long activities = count("select count(a) from Activity a where a.student.id = :id");
        long evaluationsWritten = count("select count(e) from PeerEvaluation e where e.evaluator.id = :id");
        long evaluationsReceived = count("select count(e) from PeerEvaluation e where e.evaluatee.id = :id");
        assertThat(activities).isPositive();
        assertThat(evaluationsWritten).isPositive();
        assertThat(evaluationsReceived).isPositive();

        deactivate(JOHN, this.billToken)
                .andExpect(jsonPath("$.data.teamName").value("Team1"));

        this.entityManager.flush();
        this.entityManager.clear();
        Student john = this.studentRepository.findById(JOHN).orElseThrow();
        assertThat(john.getTeam()).isNotNull();
        assertThat(john.getTeam().getTeamName()).isEqualTo("Team1");
        assertThat(count("select count(a) from Activity a where a.student.id = :id")).isEqualTo(activities);
        assertThat(count("select count(e) from PeerEvaluation e where e.evaluator.id = :id")).isEqualTo(evaluationsWritten);
        assertThat(count("select count(e) from PeerEvaluation e where e.evaluatee.id = :id")).isEqualTo(evaluationsReceived);
    }

    @Test
    void testDeactivatedStudentCannotLogIn() throws Exception {
        deactivate(JOHN, this.billToken).andExpect(status().isOk());

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("User account is abnormal."));
    }

    @Test
    void testDeactivatedStudentsOpenSessionEnds() throws Exception {
        // Before: John's token reaches a route he may call, his own student record
        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));

        deactivate(JOHN, this.billToken).andExpect(status().isOk());

        // After: the same request with the same token, obtained before his deactivation, is refused (FR-SEC-active-account)
        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    // ---- Who may not (BR-student-lifecycle, BR-section-scoped-access, BR-role-based-access) ----

    @Test
    void testStudentCannotDeactivateHerself() throws Exception {
        assertForbidden(deactivate(JOHN, this.johnToken));
        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void testStudentCannotDeactivateAnotherStudent() throws Exception {
        assertForbidden(deactivate(JOHN, this.ericToken));
        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void testInstructorAndAdminOfAnotherCourseCannotDeactivate() throws Exception {
        assertForbidden(deactivate(JOHN, this.timToken));
        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void testInstructorOfSameCourseNotAssignedToHerCourseSectionCannotDeactivate() throws Exception {
        unassign(BILL, SECTION_2); // Bill now teaches course 1 in course section 1 only

        assertForbidden(deactivate(JOHN, this.billToken));
        assertThat(johnIsEnabled()).isTrue();
    }

    @Test
    void testIdNamingNoStudentIsForbidden() throws Exception {
        assertForbidden(deactivate(NO_SUCH_STUDENT, this.bingyangToken));
    }

}
