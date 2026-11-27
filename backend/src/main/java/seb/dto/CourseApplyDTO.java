package seb.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CourseApplyDTO {

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 50, message = "课程名称不能超过50个字符")
    private String courseName;

    private Integer courseType;

    @Size(max = 50, message = "技术体系不能超过50个字符")
    private String techSystem;

    @Size(max = 50, message = "技术方向不能超过50个字符")
    private String techDirection;

    @Size(max = 20, message = "课程等级不能超过20个字符")
    private String courseLevel;

    private Integer isFree;

    @DecimalMin(value = "0.00", message = "价格不能小于0")
    @DecimalMax(value = "30000.00", message = "价格不能超过30000")
    private BigDecimal price;

    @Size(max = 255, message = "封面图片地址过长")
    private String coverImage;

    @Size(max = 2000, message = "课程描述不能超过2000个字符")
    private String description;

    @Size(max = 500, message = "申请理由不能超过500个字符")
    private String reason;

    private List<ChapterItem> chapters;

    @Data
    public static class ChapterItem {
        @Size(max = 100, message = "章节名称不能超过100个字符")
        private String chapterName;

        private Integer duration;

        @Size(max = 255, message = "视频地址过长")
        private String videoUrl;
    }
}
