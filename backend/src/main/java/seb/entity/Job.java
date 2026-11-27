package seb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;
    private Long companyId;
    private String companyName;
    private String jobCategory;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String city;
    private String education;
    private String experience;
    private String description;
    private Integer applyCount;
    private Integer collectCount;
    private LocalDateTime listTime;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
