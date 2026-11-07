package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.instructor.InstructorRepository;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StudentRepository#findByIdInCallerScope}: who counts as in scope for acting on a student
 * (BR-section-scoped-access). Seed: John (4) is in course section 2 of course 1; Bingyang (1) is course 1's admin and is
 * assigned to course sections 1 and 2; Bill (2) is an instructor of course 1 assigned to course sections 1 and 2; Tim (3)
 * is course 3's admin, assigned only to its course section 3.
 */
@DisplayName("Integration tests for the caller-scoped student lookup")
class StudentRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;
    private static final int BINGYANG = 1;
    private static final int BILL = 2;
    private static final int TIM = 3;
    private static final int SECTION_2 = 2;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    SectionRepository sectionRepository;

    @Autowired
    InstructorRepository instructorRepository;

    @Autowired
    EntityManager entityManager;

    private void unassign(int instructorId, int sectionId) {
        Section section = this.sectionRepository.findById(sectionId).orElseThrow();
        Instructor instructor = this.instructorRepository.findById(instructorId).orElseThrow();
        section.removeInstructor(instructor);
        this.entityManager.flush();
    }

    @Test
    void testInstructorAssignedToHerCourseSectionFindsHer() {
        assertThat(this.studentRepository.findByIdInCallerScope(JOHN, BILL)).isPresent();
    }

    @Test
    void testCourseAdminNotAssignedToHerCourseSectionFindsHer() {
        unassign(BINGYANG, SECTION_2);

        assertThat(this.studentRepository.findByIdInCallerScope(JOHN, BINGYANG)).isPresent();
    }

    @Test
    void testInstructorOfSameCourseNotAssignedToHerCourseSectionDoesNotFindHer() {
        unassign(BILL, SECTION_2);

        assertThat(this.studentRepository.findByIdInCallerScope(JOHN, BILL)).isEmpty();
    }

    @Test
    void testAdminAndInstructorOfAnotherCourseDoesNotFindHer() {
        assertThat(this.studentRepository.findByIdInCallerScope(JOHN, TIM)).isEmpty();
    }

    @Test
    void testStudentDoesNotFindAnotherStudentOrHerself() {
        assertThat(this.studentRepository.findByIdInCallerScope(5, JOHN)).isEmpty();
        assertThat(this.studentRepository.findByIdInCallerScope(JOHN, JOHN)).isEmpty();
    }

}
