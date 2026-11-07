package team.projectpulse.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);

    /**
     * The student, only if the caller may act on her as a course admin or instructor (BR-section-scoped-access): the
     * caller is one of the instructors of her course section, or the admin of her course. The flat
     * {@code /students/{studentId}} routes carry no container id, so the scope comes from the caller.
     */
    @Query("""
            select s from Student s join s.section sec join sec.course c
            where s.id = :studentId
              and (c.courseAdmin.id = :userId
                   or exists (select 1 from Section sec2 join sec2.instructors i where sec2 = sec and i.id = :userId))
            """)
    Optional<Student> findByIdInCallerScope(@Param("studentId") Integer studentId, @Param("userId") Integer userId);
}
