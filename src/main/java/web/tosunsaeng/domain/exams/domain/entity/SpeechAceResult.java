package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;

@Document(collection = "speechace_results")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeechAceResult {

    @Id
    private String id;

    private String examId;
    private Integer questionNumber;
    private Integer retryCount;

    // SpeechAce API의 결과 데이터를 통째로 담을 맵
    private Map<String, Object> speechAceData;
}