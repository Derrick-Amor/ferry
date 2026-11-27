package seb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("course_collect")
public class CourseCollect {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long courseId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
