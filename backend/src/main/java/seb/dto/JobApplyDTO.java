package seb.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class JobApplyDTO {

    @NotBlank(message = "职位名称不能为空")
    @Size(max = 50, message = "职位名称不能超过50个字符")
    private String jobName;

    private Long companyId;

    @Size(max = 50, message = "公司名称不能超过50个字符")
    private String companyName;

    @Size(max = 30, message = "职位类别不能超过30个字符")
    private String jobCategory;

    @DecimalMin(value = "2500.00" , message = "最低薪资不得低于2500元")
    @DecimalMax(value = "100000.00" , message = "最低薪资不得高于100000元")
    private BigDecimal salaryMin;
    @DecimalMin(value = "2500.00" , message = "最高薪资不得低于2500元")
    @DecimalMax(value = "100000.00" , message = "最高薪资不得高于100000元")
    private BigDecimal salaryMax;

    @Size(max = 20, message = "城市不能超过20个字符")
    private String city;

    @Size(max = 20, message = "学历要求不能超过20个字符")
    private String education;

    @Size(max = 20, message = "经验要求不能超过20个字符")
    private String experience;

    @Size(max = 2000, message = "职位描述不能超过2000个字符")
    private String description;

    @Size(max = 500, message = "申请理由不能超过500个字符")
    private String reason;

    private List<String> duties;

    @AssertTrue(message = "最低薪资必须小于最高薪资")
    public boolean isSalaryValid() {
        if (salaryMin == null || salaryMax == null) return true;
        return salaryMin.compareTo(salaryMax) < 0;
    }
}
