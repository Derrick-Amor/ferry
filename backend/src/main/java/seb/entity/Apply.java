package seb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

//申请类

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("apply")
public class Apply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer applyType; //1.课程上架 2.职位上架 3.课程上架 4.职位下架
    private Long targetId;
    private Long applicantId;
    private String reason;
    private Integer status; // 0.待审核 1.通过 2.驳回
    private Long auditorId;
    private String auditComment;
    private LocalDateTime auditTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
