package com.example.digitalhuman.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.digitalhuman.domain.AgentConfig;
import com.example.digitalhuman.domain.DigitalHumanProject;
import com.example.digitalhuman.domain.KnowledgeDocument;
import com.example.digitalhuman.repository.AgentConfigRepository;
import com.example.digitalhuman.repository.DigitalHumanProjectRepository;
import com.example.digitalhuman.repository.KnowledgeDocumentRepository;

/**
 * L3 集成层：把外部依赖收进容器。
 *
 * <p>这一层**只验证配置与连接**，不验证回答质量——回答质量不归集成测试管，
 * 它归 L4/L5 的评测管。把两件事混起来，就会得到「集成测试里挂一个 Judge 调用」这种
 * 既慢又脆、失败还不知道是环境问题还是效果问题的东西。
 *
 * <p>与文章示例的一处差异（按本仓库实际基线核验后调整，不照抄）：
 * 文章用 {@code pgvector/pgvector:pg16}，因为那套项目把向量库真源放在 PostgreSQL；
 * 本仓库的真源是 **MySQL + Flyway**（{@code knowledge_document} 是真相，
 * 向量库只是可重建的投影，见第 8 掌），所以容器换成 MySQL——
 * 让集成层跑在一个和线上不同的数据库上，只能验证到「另一个数据库也能跑」，
 * 验证不到「我们的迁移脚本能跑」。
 *
 * <p>它被打上 {@code integration} tag，默认不进 CI：CI 只跑离线层，
 * 需要真实依赖的验证按需执行（见根 pom 里的 tag 隔离说明）。
 */
@Testcontainers
@Tag("integration")
@SpringBootTest(properties = {
        // 集成层测的是数据库这一路，不需要 MCP Server 在场
        "spring.ai.mcp.client.enabled=false",
        "digital-human.mcp.fail-fast=false",
        // 也不该需要真实模型 Key：这一层不调模型。给它一个占位 Key 而不是让它去读环境变量，
        // 否则「有没有 Key」会变成集成层能不能跑的前提——那是一层不该有的耦合。
        "spring.ai.openai.api-key=integration-test-key-not-a-secret"
})
class FlywayMysqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("digital_human");

    @DynamicPropertySource
    static void useContainerDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DigitalHumanProjectRepository projects;

    @Autowired
    private AgentConfigRepository agentConfigs;

    @Autowired
    private KnowledgeDocumentRepository documents;

    @Test
    @DisplayName("L3：Flyway 迁移在真实 MySQL 上全部成功（含第 11 掌的图检查点表）")
    void shouldApplyEveryMigrationOnRealMysql() {
        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success = 1 order by installed_rank",
                String.class);

        assertThat(applied).contains("1", "2", "3", "4", "5", "6");
        Integer checkpointTable = jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = database() and table_name = 'graph_checkpoint'
                """, Integer.class);
        assertThat(checkpointTable).as("第 11 掌的图检查点表必须由迁移建出来").isEqualTo(1);
    }

    @Test
    @DisplayName("L3：实体映射与迁移脚本一致（Hibernate validate 能过，读写能往返）")
    void shouldRoundTripEntitiesOnRealMysql() {
        DigitalHumanProject project = projects.save(
                new DigitalHumanProject(1L, "integration-project", "集成测试项目"));
        AgentConfig config = agentConfigs.save(AgentConfig.forProject(project.getId()));
        config.setTemperature(new BigDecimal("0.20"));
        config.setMaxTokens(256);
        agentConfigs.save(config);
        documents.save(new KnowledgeDocument(project.getId(), "集成资料", "报价规则：满 10 台 8 折。", 1));

        // ddl-auto=validate 已经证明映射与脚本一致；这里再证明写入路径真的通
        AgentConfig reloaded = agentConfigs.findByProjectId(project.getId()).orElseThrow();
        assertThat(reloaded.getMaxTokens()).isEqualTo(256);
        assertThat(reloaded.getTemperature()).isEqualByComparingTo("0.20");
        assertThat(documents.findByProjectIdOrderByIdAsc(project.getId())).hasSize(1);
    }
}
