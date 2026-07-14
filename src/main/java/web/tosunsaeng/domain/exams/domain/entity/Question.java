package web.tosunsaeng.domain.exams.domain.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

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
    private String guideAudioUrl;

    @Field("image_url")
    private String imageUrl;

    @Field("reference_text")
    private String referenceText;

    @Field("part_intro_text")
    private String partIntroText;

    private String question;

    @Field("table_context")
    private TableContext tableContext;

    private Integer prepTimeSec;
    private Integer speakTimeSec;

    private String corrected_answer;

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
        @Field("session_title")
        private String sessionTitle;
        private String speaker;
        private String note;
    }
}