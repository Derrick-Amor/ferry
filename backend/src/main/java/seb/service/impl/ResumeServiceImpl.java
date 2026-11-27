package seb.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import seb.common.BusinessException;
import seb.entity.Resume;
import seb.mapper.ResumeMapper;
import seb.service.ResumeService;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ResumeService} 的实现。
 */
@Service
public class ResumeServiceImpl extends ServiceImpl<ResumeMapper, Resume> implements ResumeService {

    private static final String UPLOAD_DIR = "uploads";

    @Override
    public Map<String, Object> upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择要上传的简历文件");
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("仅支持上传 PDF 格式的简历");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("pdf")) {
            throw new BusinessException("仅支持上传 PDF 格式的简历");
        }

        File dir = new File(UPLOAD_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(500, "上传目录创建失败");
        }

        String suffix = original.substring(original.lastIndexOf('.'));
        String storedName = IdUtil.fastSimpleUUID() + suffix;
        File dest = new File(dir, storedName);
        try {
            file.transferTo(dest.getAbsoluteFile());
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败");
        }

        String url = "/" + UPLOAD_DIR + "/" + storedName;
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(original);
        resume.setFileUrl(url);
        this.save(resume);

        Map<String, Object> map = new HashMap<>();
        map.put("resumeId", resume.getId());
        map.put("fileName", original);
        map.put("url", url);
        return map;
    }

    @Override
    public List<Resume> list(Long userId) {
        return this.list(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getCreateTime));
    }

    @Override
    public void delete(Long userId, Long id) {
        Resume resume = this.getById(id);
        if (resume == null || !resume.getUserId().equals(userId)) throw new BusinessException("简历不存在");
        if (resume.getFileUrl() != null && resume.getFileUrl().startsWith("/")) {
            File f = new File(resume.getFileUrl().substring(1));
            if (f.exists()) f.delete();
        }
        this.removeById(id);
    }
}
