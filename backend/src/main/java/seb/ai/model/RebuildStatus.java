package seb.ai.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 全量重建的进度快照。
 *
 * <p>刻意不落库：重建是可重试操作，进程重启后进度丢失没有实际损失，
 * 为此加一张任务表反而要处理僵尸任务、并发清理、状态机迁移。
 * 代价是「重启后查不到上一次的结果」，写进 HANDOFF 说明即可。
 */
@Data
public class RebuildStatus {

    public static final String IDLE = "IDLE";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    /** 状态：IDLE / RUNNING / DONE / FAILED */
    private String state = IDLE;

    /** 待重建文档总数 */
    private int total;

    /** 已开始处理的文档数 */
    private int done;

    /** 当前正在处理的文档标题 */
    private String currentTitle;

    /** 开始时间（epoch ms） */
    private Long startedAt;

    /** 结束时间（epoch ms） */
    private Long finishedAt;

    /** 已耗时（毫秒）；运行中为实时值 */
    private long elapsedMs;

    /** 成功文档数 */
    private int docSuccess;

    /** 失败文档数 */
    private int docFailed;

    /** 本次写入的分块数 */
    private int chunkTotal;

    /** 本次写入的向量数 */
    private int vectorTotal;

    /** 失败明细，最多保留前 10 条，避免响应体失控 */
    private List<String> errors = new ArrayList<>();

    /**
     * 空闲状态。
     *
     * @return 初始快照
     */
    public static RebuildStatus idle() {
        return new RebuildStatus();
    }
}
