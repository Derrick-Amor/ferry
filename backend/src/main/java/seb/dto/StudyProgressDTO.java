package seb.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudyProgressDTO {
    private Long courseId;
    private Long chapterId;
    private BigDecimal progress;
}
