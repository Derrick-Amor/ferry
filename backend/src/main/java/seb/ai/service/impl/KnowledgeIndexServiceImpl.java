package seb.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import seb.ai.model.ChunkResult;
import seb.ai.model.IndexResult;
import seb.ai.service.KnowledgeChunkService;
import seb.ai.service.KnowledgeIndexService;
import seb.entity.KnowledgeChunk;
import seb.entity.KnowledgeDoc;
import seb.mapper.KnowledgeChunkMapper;
import seb.mapper.KnowledgeDocMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * {@link KnowledgeIndexService} 的实现。
 *
 * <p>单篇文档的重建顺序（顺序不能颠倒）：
 * <ol>
 *   <li>查出该文档旧分块的 {@code vectorId}，先调用 {@link VectorStore#delete(List)} 清掉旧向量。
 *       VectorStore 没有「按 metadata 批量删除」的 API，只能靠库里记下的 id 列表来删，
 *       所以 {@code knowledge_chunk.vector_id} 这一列是幂等重建的前提。</li>
 *   <li>删除 MySQL 旧分块，重新分块。</li>
 *   <li>先写 {@code knowledge_chunk} 拿到自增主键，再用「主键字符串」作为向量 id 写入 Redis。
 *       反过来做的话，metadata 里的 chunkId 只能是临时值，S2-C 做引用溯源就对不上号。</li>
 *   <li>回写 {@code knowledge_doc.chunk_count}。</li>
 * </ol>
 *
 * <p>一致性说明：MySQL 的写入在一个事务里；Redis 不参与该事务。
 * 若 {@code vectorStore.add} 抛错，MySQL 会回滚，但旧向量已在第 1 步被删除——
 * 结果是该文档「暂无向量」而不是「新旧混杂」。重新执行一次 rebuild 即可恢复，
 * 对「重建索引」这类可重试操作是可以接受的取舍。
 */
@Service
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexServiceImpl.class);

    @Resource
    private VectorStore vectorStore;

    @Resource
    private KnowledgeDocMapper knowledgeDocMapper;

    @Resource
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Resource
    private KnowledgeChunkService knowledgeChunkService;

    /**
     * 全量重建。
     *
     * <p>刻意<b>不</b>加 {@code @Transactional}：37 篇文档要逐篇调用 embedding 接口，
     * 整个重建可能持续数分钟，把这么长的时间包在一个事务里会长时间占用连接并持有锁；
     * 而且某篇失败被 catch 住后，事务已被标记 rollback-only，提交时会抛
     * UnexpectedRollbackException，反而把「部分成功」也毁掉。
     *
     * <p>这里按「每篇文档独立提交」处理：重建本身是幂等的（先删旧再写新），
     * 单篇失败只影响它自己，重跑即可自愈。
     */
    @Override
    public IndexResult rebuildAll() {
        return rebuildAll(null);
    }

    @Override
    public IndexResult rebuildAll(BiConsumer<Integer, KnowledgeDoc> onDoc) {
        long start = System.currentTimeMillis();
        IndexResult total = new IndexResult();

        List<KnowledgeDoc> docs = knowledgeDocMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDoc>()
                        .eq(KnowledgeDoc::getStatus, 1)
                        .orderByAsc(KnowledgeDoc::getId));
        log.info("[index] 开始重建，待处理文档 {} 篇", docs.size());

        int i = 0;
        for (KnowledgeDoc doc : docs) {
            i++;
            if (onDoc != null) {
                onDoc.accept(i - 1, doc);
            }
            IndexResult one = doRebuild(doc);
            total.merge(one);
            log.info("[index] {}/{} docId={} 《{}》 -> 分块 {}，向量 {}",
                    i, docs.size(), doc.getId(), doc.getTitle(),
                    one.getChunkTotal(), one.getVectorTotal());
        }

        total.setElapsedMs(System.currentTimeMillis() - start);
        log.info("[index] 重建完成 {}", total);
        return total;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IndexResult rebuildOne(Long docId) {
        long start = System.currentTimeMillis();
        IndexResult result = new IndexResult();
        KnowledgeDoc doc = docId == null ? null : knowledgeDocMapper.selectById(docId);
        if (doc == null) {
            result.setDocTotal(1);
            result.setDocFailed(1);
            result.getErrors().add(docId + " -> 文档不存在");
            result.setElapsedMs(System.currentTimeMillis() - start);
            return result;
        }
        result.merge(doRebuild(doc));
        result.setElapsedMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * 单篇文档的实际重建逻辑。
     *
     * @param doc 文档实体
     * @return 该文档的统计结果（docTotal=1）
     */
    private IndexResult doRebuild(KnowledgeDoc doc) {
        IndexResult one = new IndexResult();
        one.setDocTotal(1);
        Long docId = doc.getId();

        try {
            // 1) 清掉旧向量：没有这一步，重复 rebuild 会在 Redis 里留下重复/孤儿向量
            List<KnowledgeChunk> olds = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));
            List<String> oldVectorIds = new ArrayList<>();
            for (KnowledgeChunk old : olds) {
                if (old.getVectorId() != null && !old.getVectorId().isBlank()) {
                    oldVectorIds.add(old.getVectorId());
                }
            }
            if (!oldVectorIds.isEmpty()) {
                vectorStore.delete(oldVectorIds);
            }

            // 2) 删除旧分块
            if (!olds.isEmpty()) {
                knowledgeChunkMapper.delete(
                        new LambdaQueryWrapper<KnowledgeChunk>().eq(KnowledgeChunk::getDocId, docId));
            }

            // 3) 重新分块
            List<ChunkResult> chunks = knowledgeChunkService.split(doc.getContent());
            if (chunks.isEmpty()) {
                doc.setChunkCount(0);
                knowledgeDocMapper.updateById(doc);
                one.setDocSuccess(1);
                log.warn("[index] docId={} 《{}》 正文为空或分块结果为空，已跳过", docId, doc.getTitle());
                return one;
            }

            // 4) 先落 MySQL 拿自增 id，再据此构造向量 id
            List<Document> documents = new ArrayList<>(chunks.size());
            for (ChunkResult chunk : chunks) {
                KnowledgeChunk row = new KnowledgeChunk();
                row.setDocId(docId);
                row.setChunkNo(chunk.getChunkNo());
                row.setContent(chunk.getContent());
                row.setTokenCount(chunk.getTokenCount());
                knowledgeChunkMapper.insert(row);

                String vectorId = String.valueOf(row.getId());
                row.setVectorId(vectorId);
                knowledgeChunkMapper.updateById(row);

                documents.add(new Document(vectorId, chunk.getContent(),
                        buildMetadata(doc, row)));
            }

            // 5) 写向量（内部会调用 embedding 模型，一次批量提交）
            vectorStore.add(documents);

            // 6) 回写分块数
            doc.setChunkCount(chunks.size());
            knowledgeDocMapper.updateById(doc);

            one.setDocSuccess(1);
            one.setChunkTotal(chunks.size());
            one.setVectorTotal(documents.size());
        } catch (Exception e) {
            one.setDocFailed(1);
            one.getErrors().add(docId + " 《" + doc.getTitle() + "》 -> " + e);
            log.error("[index] docId={} 《{}》 重建失败", docId, doc.getTitle(), e);
        }
        return one;
    }

    /**
     * 构造向量 metadata。S2-C 做引用溯源要依赖这几个字段，缺一不可。
     *
     * @param doc 文档
     * @param row 已落库的分块（含自增 id）
     * @return metadata
     */
    private Map<String, Object> buildMetadata(KnowledgeDoc doc, KnowledgeChunk row) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("docId", doc.getId());
        metadata.put("chunkId", row.getId());
        metadata.put("chunkNo", row.getChunkNo());
        metadata.put("title", doc.getTitle());
        // 来源类型决定「岗位咨询」能不能把课程正文挡在检索之外
        metadata.put("sourceType", doc.getSourceType() == null ? 1 : doc.getSourceType());
        // courseId 可能为空（上传文档、职位描述），统一落 0 避免下游空指针
        metadata.put("courseId", doc.getCourseId() == null ? 0L : doc.getCourseId());
        return metadata;
    }
}
