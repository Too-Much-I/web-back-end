package web.tosunsaeng.domain.exams.domain.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.util.List;

@Document(collection = "mock_exams")
@Getter @Builder
@NoArgsConstructor @AllArgsConstructor
public class MockExam {
    @Id
    private String id;

    @Field("mock_exam_id")
    private String mockExamId;

    private String title;

    private List<Question> questions;
}