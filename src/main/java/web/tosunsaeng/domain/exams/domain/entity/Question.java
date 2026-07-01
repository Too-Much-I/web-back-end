package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "questions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {
    @Id
    private String id;
    private String examPaperId; // 추가! (예: "paper_001", "paper_002")
    private Integer part;
    private String questionId;
    private String text;
    private Integer prepTimeSec;
    private Integer speakTimeSec;
}