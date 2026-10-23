package com.smartdroneinspection.maintenance.repository;

import com.smartdroneinspection.maintenance.domain.RepairKnowledgeChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepairKnowledgeChunkRepository extends JpaRepository<RepairKnowledgeChunk, UUID> {

  List<RepairKnowledgeChunk> findByCategoryIgnoreCase(String category);

  @Query(
      value =
          """
          SELECT * FROM repair_knowledge_chunks
          WHERE category = :category
             OR LOWER(defect_patterns) LIKE LOWER(CONCAT('%', :keyword, '%'))
             OR LOWER(title) LIKE LOWER(CONCAT('%', :keyword, '%'))
          ORDER BY id
          LIMIT :limit
          """,
      nativeQuery = true)
  List<RepairKnowledgeChunk> findRelevantChunks(
      @Param("category") String category,
      @Param("keyword") String keyword,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT * FROM repair_knowledge_chunks
          WHERE embedding IS NOT NULL
          ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
          LIMIT :limit
          """,
      nativeQuery = true)
  List<RepairKnowledgeChunk> findSimilarByVector(
      @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);
}
