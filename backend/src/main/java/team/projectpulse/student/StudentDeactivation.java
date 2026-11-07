package team.projectpulse.student;

/**
 * The outcome of deactivating a student (UC-STU-deactivate-student): the student as she now is, and whether this
 * request is what deactivated her. {@code changed} is false when someone else had already deactivated her (6a).
 */
public record StudentDeactivation(Student student, boolean changed) {
}
