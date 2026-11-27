package seb.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("resume")
public class Resume {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String fileName;
    private String fileUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
