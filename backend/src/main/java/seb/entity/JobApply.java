package seb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("job_apply")
public class JobApply {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long jobId;
    private Long resumeId;
    private Integer status; //0.已投递 2.已查看 3.面试邀请 4.已录用 5.已拒绝
    private LocalDateTime applyTime;
}
