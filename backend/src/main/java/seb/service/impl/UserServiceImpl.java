package seb.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import seb.auth.JwtUtil;
import seb.common.BusinessException;
import seb.dto.ForgotPasswordDTO;
import seb.dto.LoginDTO;
import seb.dto.RegisterDTO;
import seb.dto.ResetPasswordDTO;
import seb.dto.UpdateUserInfoDTO;
import seb.entity.User;
import seb.mapper.UserMapper;
import seb.service.UserService;
import seb.vo.UserVO;

import jakarta.annotation.Resource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link UserService} 的实现。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public void register(RegisterDTO dto) {
        long count = this.count(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (count > 0) throw new BusinessException("用户名已存在");
        // 昵称是选填项，空串 / 空白串都要按「未填写」处理。
        // 只判 != null 的话，前端表单初值 nickname: '' 会被当成真实昵称参与查重，
        // 库里只要已存在空昵称用户，后续所有不填昵称的注册都会被误判为「昵称已被使用」；
        // 同时下面的用户名兜底分支也不会执行，昵称会被写成空串。
        String nickname = (dto.getNickname() == null || dto.getNickname().isBlank())
                ? null : dto.getNickname().trim();
        if (nickname != null
                && this.count(new QueryWrapper<User>().eq("nickname", nickname)) > 0) {
            throw new BusinessException("昵称已被使用");
        }
        if (dto.getEmail() != null
                && this.count(new QueryWrapper<User>().eq("email", dto.getEmail())) > 0) {
            throw new BusinessException("该邮箱已被注册");
        }
        Integer role = dto.getRole() != null ? dto.getRole() : 0;
        if (dto.getPhone() != null
                && this.count(new QueryWrapper<User>().eq("phone", dto.getPhone()).eq("role", role)) > 0) {
            throw new BusinessException("该手机号在此身份下已被使用");
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setNickname(nickname != null ? nickname : dto.getUsername());
        user.setPhone(dto.getPhone());
        user.setEmail(dto.getEmail());
        user.setRole(role);
        user.setStatus(1);
        this.save(user);
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        User user = this.getOne(new QueryWrapper<User>().eq("username", dto.getUsername()));
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == 0) throw new BusinessException("账号已被禁用");
        user.setLastLoginTime(LocalDateTime.now());
        this.updateById(user);

        String token = jwtUtil.createToken(user.getId(), user.getRole());
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        // 不能直接放 User 实体：会把 password 的 BCrypt 密文原样吐给前端
        map.put("user", UserVO.from(user));
        return map;
    }

    @Override
    public UserVO getInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        return UserVO.from(user);
    }

    @Override
    public void updateInfo(Long userId, UpdateUserInfoDTO dto) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (dto.getNickname() != null) {
            // 空白昵称不做查重（否则会与库中其它空昵称用户互相冲突），但仍按原样保存
            if (!dto.getNickname().isBlank()
                    && this.count(new QueryWrapper<User>().eq("nickname", dto.getNickname()).ne("id", userId)) > 0) {
                throw new BusinessException("昵称已被使用");
            }
            user.setNickname(dto.getNickname());
        }
        if (dto.getRealName() != null) user.setRealName(dto.getRealName());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        this.updateById(user);
    }

    @Override
    public void resetPassword(Long userId, ResetPasswordDTO dto) {
        User user = this.getById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(BCrypt.hashpw(dto.getNewPassword(), BCrypt.gensalt()));
        this.updateById(user);
    }

    @Override
    public void forgotPassword(ForgotPasswordDTO dto) {
        User user = this.getOne(new QueryWrapper<User>().eq("phone", dto.getPhone()));
        if (user == null) throw new BusinessException("该手机号未注册");
        user.setPassword(BCrypt.hashpw(dto.getNewPassword(), BCrypt.gensalt()));
        this.updateById(user);
    }
}
