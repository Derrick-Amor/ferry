package seb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

//ai面试
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_interview")
public class AiInterview {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long jobId;
    private Integer status;
    private BigDecimal score;
    private String result;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
