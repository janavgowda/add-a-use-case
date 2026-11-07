package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    @Mock
    StudentRepository studentRepository;

    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Student john;

    @BeforeEach
    void setUp() {
        Instructor courseAdmin = new Instructor("b.wei@abc.edu", "Bingyang", "Wei", "b.wei@abc.edu", "x", true, "admin instructor");
        courseAdmin.setId(1);
        Course course = new Course();
        course.setCourseAdmin(courseAdmin);
        Section section = new Section();
        section.setCourse(course); // Bingyang is not among this course section's instructors

        this.john = new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "x", true, "student");
        this.john.setId(4);
        this.john.setSection(section);
    }

    @Test
    void testCourseAdminOfStudentsCourseIsAdmittedEvenWhenNotAssignedToHerCourseSection() {
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.studentRepository.findById(4)).willReturn(Optional.of(this.john));

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).isTrue();
    }

    @Test
    void testAnotherUserIsNotAdminOfStudentsCourse() {
        given(this.userUtils.getUserId()).willReturn(3);
        given(this.studentRepository.findById(4)).willReturn(Optional.of(this.john));

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).isFalse();
    }

    @Test
    void testUnknownStudentIsDeniedWithoutThrowing() {
        given(this.userUtils.getUserId()).willReturn(1);
        given(this.studentRepository.findById(999)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(999)).isFalse();
    }

}
