package seb.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String courseName;
    private Integer courseType; // 1.理论 2.实训 3.认证
    private String techSystem;
    private String techDirection;
    private String courseLevel;
    private Integer isFree;
    private BigDecimal price;
    private Long teacherId;
    private String teacherName;
    private String coverImage;
    private String description;
    private BigDecimal rating;
    private Integer enrollCount;
    private LocalDateTime listTime;
    private Integer status; //0.待审核 1.已上架 2.已下架 3.驳回

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill =  FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
