package seb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("job_collect")
public class JobCollect {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long jobId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
