package seb.dto;

import lombok.Data;

@Data
public class CourseQuery {

    private Integer courseType; //1.理论 2.实训 3.认证
    private String techSystem;
    private String techDirection;
    private String courseLevel;
    private Integer isFree;
    private String sortBy; // time | enroll | rating
    private String order; //asc | desc
    private String keyword; // 课程名|讲师名
    private Long page = 1L;
    private Long size = 10L;

}
