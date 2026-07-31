package web.tosunsaeng.domain.exams.domain.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ExamsRepositoryScanTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void registersExistingExamsMongoRepositories() {
        assertThat(applicationContext.getBean(AzureResultRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(MockExamRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(SpeechAceResultRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(ExamResultRepository.class)).isNotNull();
        assertThat(applicationContext.getBean(QuestionRepository.class)).isNotNull();
    }
}
