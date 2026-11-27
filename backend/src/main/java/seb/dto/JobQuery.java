package seb.dto;

import lombok.Data;

@Data
public class JobQuery {

    private String jobCategory;
    private String keyword; //岗位名|公司名
    private long page = 1;
    private long size = 10;
}
