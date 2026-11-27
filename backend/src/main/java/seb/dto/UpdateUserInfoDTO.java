package seb.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserInfoDTO {
    @Size(max = 20, message = "昵称不能超过20个字符")
    private String nickname;

    @Size(max = 20, message = "真实姓名不能超过20个字符")
    private String realName;

    @Size(max = 255, message = "头像地址过长")
    private String avatar;
}
