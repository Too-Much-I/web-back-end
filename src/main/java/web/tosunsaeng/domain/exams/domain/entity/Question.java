package web.tosunsaeng.domain.exams.domain.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Document(collection = "questions")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Question {

    @Id
    private String id;

    private String examPaperId;

    @Field("part_number")
    private Integer partNumber;

    @Field("question_number")
    private Integer questionNumber;

    private String audioUrl;
    private String imageUrl;
    private String referenceText;
    private String question;
    private TableContext tableContext;

    private Integer prepTimeSec;
    private Integer speakTimeSec;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableContext {
        private String title;
        private String location;
        private String date;
        private String fee;
        private List<TableItem> items;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableItem {
        private String time;
        private String sessionTitle;
        private String speaker;
        private String note;
    }
}