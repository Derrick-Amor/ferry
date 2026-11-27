package seb.service;

import org.springframework.web.multipart.MultipartFile;
import seb.entity.Resume;

import java.util.List;
import java.util.Map;

/**
 * 简历业务接口：上传、列表与删除。
 */
public interface ResumeService {

    /**
     * 上传 PDF 简历，落盘后记录到数据库。
     *
     * @param userId 用户ID
     * @param file   上传的 PDF 文件
     * @return 含 {@code resumeId} / {@code fileName} / {@code url} 的 Map
     */
    Map<String, Object> upload(Long userId, MultipartFile file);

    /**
     * 查询指定用户的简历列表，按上传时间倒序。
     *
     * @param userId 用户ID
     * @return 简历列表
     */
    List<Resume> list(Long userId);

    /**
     * 删除简历记录及磁盘文件。
     *
     * @param userId 用户ID
     * @param id     简历ID
     */
    void delete(Long userId, Long id);
}
