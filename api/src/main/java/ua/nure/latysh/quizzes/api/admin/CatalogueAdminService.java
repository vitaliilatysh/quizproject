package ua.nure.latysh.quizzes.api.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ua.nure.latysh.quizzes.api.admin.AdminModels.LevelResponse;
import ua.nure.latysh.quizzes.api.admin.AdminModels.SubjectResponse;
import ua.nure.latysh.quizzes.api.domain.LevelRepository;
import ua.nure.latysh.quizzes.api.domain.QuizRepository;
import ua.nure.latysh.quizzes.api.domain.Subject;
import ua.nure.latysh.quizzes.api.domain.SubjectRepository;
import ua.nure.latysh.quizzes.api.support.ResourceConflictException;

import java.util.List;

/**
 * The reference data an administrator picks from when describing a quiz.
 *
 * <p>Subjects and levels sit together because that is how they are used — a
 * quiz names one of each — and because levels have no administration of their
 * own beyond being listed. See the package documentation for transactions.
 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CatalogueAdminService {
    private static final String SUBJECT_RESOURCE = "Subject";

    private final SubjectRepository subjectRepository;
    private final LevelRepository levelRepository;
    private final QuizRepository quizRepository;

    public CatalogueAdminService(
            SubjectRepository subjectRepository,
            LevelRepository levelRepository,
            QuizRepository quizRepository) {
        this.subjectRepository = subjectRepository;
        this.levelRepository = levelRepository;
        this.quizRepository = quizRepository;
    }

    public List<SubjectResponse> subjects() {
        return subjectRepository.findAllByOrderByNameAsc().stream()
                .map(subject -> new SubjectResponse(subject.getId(), subject.getName()))
                .toList();
    }

    public List<LevelResponse> levels() {
        return levelRepository.findAllByOrderByIdAsc().stream()
                .map(level -> new LevelResponse(level.getId(), level.getLabel()))
                .toList();
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public SubjectResponse createSubject(String name) {
        String normalizedName = name.trim();
        var subject = new Subject(normalizedName);
        AdminErrors.saveUnique(SUBJECT_RESOURCE, normalizedName, () -> subjectRepository.saveAndFlush(subject));
        return new SubjectResponse(subject.getId(), normalizedName);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public SubjectResponse updateSubject(int subjectId, String name) {
        String normalizedName = name.trim();
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> AdminErrors.missing(SUBJECT_RESOURCE, subjectId));
        subject.setName(normalizedName);
        AdminErrors.saveUnique(SUBJECT_RESOURCE, normalizedName, () -> subjectRepository.saveAndFlush(subject));
        return new SubjectResponse(subjectId, normalizedName);
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deleteSubject(int subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> AdminErrors.missing(SUBJECT_RESOURCE, subjectId));
        if (quizRepository.existsBySubject_Id(subjectId)) {
            throw new ResourceConflictException("Subject " + subjectId + " is used by a quiz");
        }
        subjectRepository.delete(subject);
    }
}
