package com.example.digitalhuman.graph;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;

/**
 * 把检查点落到 MySQL 的 saver：**进程重启之后，图还能从断点接着跑**。
 *
 * <p>为什么要自己写（第 11 掌的验收第四条）：框架自带的内存 saver 一重启就什么都没了——
 * 卡在「等人工确认」的工单会全部回到起点，运营主管昨天点过的同意要重新点一遍。
 * 框架自带的持久化实现只有 Postgres 版，我们要的是 MySQL。
 *
 * <p>做法沿框架自己的路子：**继承 {@link MemorySaver}，只覆写五个落盘钩子**
 * （PostgresSaver 就是这么做的）。这样「检查点的组织与查找语义」仍然由框架负责，
 * 我们只负责「读／写这一份状态」——边界清楚，出错面小。
 *
 * <p>状态的序列化用框架的 {@link SpringAIJacksonStateSerializer}：它能处理
 * Spring AI 的 Message / Document，而我们的 biz 节点是一个 ReactAgent（状态里本来就有消息）。
 * 存的是字节 + content_type，以后换序列化器不用改表。
 */
public class MysqlCheckpointSaver extends MemorySaver {

    private static final Logger log = LoggerFactory.getLogger(MysqlCheckpointSaver.class);

    private final DataSource dataSource;
    private final StateSerializer stateSerializer;

    public MysqlCheckpointSaver(DataSource dataSource) {
        this.dataSource = dataSource;
        this.stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new);
    }

    /** 会话不存在就建一条：检查点有外键，先有 thread 才能有 checkpoint。 */
    private void ensureThread(Connection connection, String threadId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "insert ignore into graph_thread (thread_id, is_released) values (?, 0)")) {
            statement.setString(1, threadId);
            statement.executeUpdate();
        }
    }

    @Override
    protected LinkedList<Checkpoint> loadedCheckpoints(RunnableConfig config,
                                                       LinkedList<Checkpoint> checkpoints) throws Exception {
        String threadId = threadIdOf(config);
        if (threadId == null) {
            return checkpoints;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     // 按自增 seq 排序，不按 saved_at：同一毫秒内会写多条，
                     // 按时间排序等于随机排序，恢复时就会挑错检查点（第一版就是这么错的）
                     "select checkpoint_id, node_id, next_node_id, state_data from graph_checkpoint "
                             + "where thread_id = ? order by seq asc")) {
            statement.setString(1, threadId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    byte[] stateData = rows.getBytes("state_data");
                    Map<String, Object> state = stateSerializer.dataFromBytes(stateData);
                    checkpoints.add(Checkpoint.builder()
                            .id(rows.getString("checkpoint_id"))
                            .nodeId(rows.getString("node_id"))
                            .nextNodeId(rows.getString("next_node_id"))
                            .state(state)
                            .build());
                }
            }
        }
        log.debug("[graph] 从 MySQL 载入检查点 thread={} 条数={}", threadId, checkpoints.size());
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                      Checkpoint checkpoint) throws Exception {
        write(config, checkpoint, false);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                     Checkpoint checkpoint) throws Exception {
        write(config, checkpoint, true);
    }

    private void write(RunnableConfig config, Checkpoint checkpoint, boolean update) throws Exception {
        String threadId = threadIdOf(config);
        if (threadId == null) {
            return;
        }
        byte[] stateData = stateSerializer.dataToBytes(checkpoint.getState());
        try (Connection connection = dataSource.getConnection()) {
            ensureThread(connection, threadId);
            String sql = update
                    ? "update graph_checkpoint set node_id = ?, next_node_id = ?, state_data = ?, "
                      + "state_content_type = ?, saved_at = ? where checkpoint_id = ?"
                    : "insert into graph_checkpoint (checkpoint_id, thread_id, node_id, next_node_id, "
                      + "state_data, state_content_type, saved_at) values (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                Timestamp now = Timestamp.from(Instant.now());
                if (update) {
                    statement.setString(1, checkpoint.getNodeId());
                    statement.setString(2, checkpoint.getNextNodeId());
                    statement.setBytes(3, stateData);
                    statement.setString(4, stateSerializer.contentType());
                    statement.setTimestamp(5, now);
                    statement.setString(6, checkpoint.getId());
                } else {
                    statement.setString(1, checkpoint.getId());
                    statement.setString(2, threadId);
                    statement.setString(3, checkpoint.getNodeId());
                    statement.setString(4, checkpoint.getNextNodeId());
                    statement.setBytes(5, stateData);
                    statement.setString(6, stateSerializer.contentType());
                    statement.setTimestamp(7, now);
                }
                statement.executeUpdate();
            }
        }
    }

    @Override
    protected void releasedCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints,
                                        BaseCheckpointSaver.Tag tag) throws Exception {
        String threadId = threadIdOf(config);
        if (threadId == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update graph_thread set is_released = 1 where thread_id = ?")) {
            statement.setString(1, threadId);
            statement.executeUpdate();
        }
        log.info("[graph] 会话已释放 thread={}（释放后不再用于恢复）", threadId);
    }

    /** 只读查询：给「这张工单现在卡在哪一步」这个问题一个可查询的答案。 */
    public List<CheckpointRecord> history(String threadId) {
        List<CheckpointRecord> records = new java.util.ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select checkpoint_id, node_id, next_node_id, saved_at from graph_checkpoint "
                             + "where thread_id = ? order by seq asc")) {
            statement.setString(1, threadId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    records.add(new CheckpointRecord(rows.getString("checkpoint_id"),
                            rows.getString("node_id"), rows.getString("next_node_id"),
                            rows.getTimestamp("saved_at").toInstant()));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("读取检查点失败：" + ex.getMessage(), ex);
        }
        return records;
    }

    private static String threadIdOf(RunnableConfig config) {
        return config == null ? null : config.threadId().orElse(null);
    }

    public record CheckpointRecord(String checkpointId, String nodeId, String nextNodeId, Instant savedAt) {
    }
}
