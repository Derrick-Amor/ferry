package seb.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiInterviewSubmitDTO {
    private Long interviewId;
    private List<String> answers;
}
