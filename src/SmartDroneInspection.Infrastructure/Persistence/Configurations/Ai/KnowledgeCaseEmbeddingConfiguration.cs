using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;
using Pgvector;
using SmartDroneInspection.Domain.Ai;
using SmartDroneInspection.Infrastructure.Persistence.Configurations;

namespace SmartDroneInspection.Infrastructure.Persistence.Configurations.Ai;

public sealed class KnowledgeCaseEmbeddingConfiguration : IEntityTypeConfiguration<KnowledgeCaseEmbedding>
{
    public void Configure(EntityTypeBuilder<KnowledgeCaseEmbedding> builder)
    {
        builder.ConfigureBase("knowledge_case_embeddings");
        builder.Property(x => x.Embedding)
            .HasConversion(
                vector => new Vector(vector.Values.ToArray()),
                vector => new EmbeddingVector(vector.ToArray()))
            .HasColumnType("vector(1536)")
            .IsRequired();
        builder.Property(x => x.ModelName).HasMaxLength(150).IsRequired(); builder.Property(x => x.ModelVersion).HasMaxLength(100); builder.Property(x => x.EmbeddedAt).IsRequired();
        builder.HasIndex(x => x.KnowledgeCaseId).IsUnique(); builder.HasIndex(x => x.Embedding).HasMethod("hnsw").HasOperators("vector_cosine_ops");
        builder.HasOne<KnowledgeCase>().WithMany().HasForeignKey(x => x.KnowledgeCaseId).OnDelete(DeleteBehavior.Cascade);
    }
}