package seb.config;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import seb.entity.User;
import seb.mapper.UserMapper;

/**
 * 启动时初始化一个默认管理员账号（仅当数据库中没有管理员时）。
 * 默认账号：admin / admin123
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Resource
    private UserMapper userMapper;

    @Override
    public void run(String... args) {
        long adminCount = userMapper.selectCount(new QueryWrapper<User>().eq("role", 2));
        if (adminCount > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(BCrypt.hashpw("admin123", BCrypt.gensalt()));
        admin.setNickname("管理员");
        admin.setRole(2);
        admin.setStatus(1);
        userMapper.insert(admin);
    }
}
