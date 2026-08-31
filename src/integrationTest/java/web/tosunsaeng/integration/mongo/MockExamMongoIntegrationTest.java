package web.tosunsaeng.integration.mongo;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.integration.support.IntegrationContainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@ActiveProfiles("test")
@Testcontainers
class MockExamMongoIntegrationTest {

    private static final String DATABASE = "exam_dynamic_table_context";

    @Container
    static final MongoDBContainer MONGO = IntegrationContainers.mongo();

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.mongodb.database", () -> DATABASE);
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MockExamRepository mockExamRepository;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }

    @Test
    void readsDynamicTableContextWithoutDroppingNestedFields() {
        Document cells = new Document()
                .append("time", "8:30 AM - 8:50 AM")
                .append("activity", "Opening Remarks");
        Document item = new Document()
                .append("cells", cells)
                .append("status", "scheduled")
                .append("status_note", null)
                .append("strike_through", false);
        Document tableContext = new Document()
                .append("table_type", "orientation_schedule")
                .append("title", "Northstar Editorial Group")
                .append("subtitles", List.of("Spring Editorial Fellows Orientation"))
                .append("columns", List.of(new Document()
                        .append("key", "time")
                        .append("label", "Time")
                        .append("value_type", "time")))
                .append("items", List.of(item))
                .append("notes", List.of());
        mongoTemplate.getCollection("mock_exams").insertOne(new Document()
                .append("mock_exam_id", "mock_exam_001")
                .append("title", "토선생 모의고사 1회")
                .append("questions", List.of(new Document()
                        .append("part_number", 4)
                        .append("question_number", 8)
                        .append("question", "When does the first activity begin?")
                        .append("table_context", tableContext))));

        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_001")
                .orElseThrow();
        Map<String, Object> mappedTableContext = mockExam.getQuestions().getFirst()
                .getTableContext();

        assertThat(mappedTableContext)
                .containsEntry("table_type", "orientation_schedule")
                .containsEntry("title", "Northstar Editorial Group")
                .containsKeys("subtitles", "columns", "items", "notes");
        Object firstItem = ((List<?>) mappedTableContext.get("items")).getFirst();
        assertThat(firstItem).isInstanceOf(Map.class);
        Map<?, ?> mappedItem = (Map<?, ?>) firstItem;
        assertThat(mappedItem.get("status")).isEqualTo("scheduled");
        assertThat(mappedItem.get("status_note")).isNull();
        assertThat(mappedItem.get("strike_through")).isEqualTo(false);
        assertThat(mappedItem.get("cells")).isInstanceOf(Map.class);
        Map<?, ?> mappedCells = (Map<?, ?>) mappedItem.get("cells");
        assertThat(mappedCells.get("time")).isEqualTo("8:30 AM - 8:50 AM");
        assertThat(mappedCells.get("activity")).isEqualTo("Opening Remarks");
    }
}
