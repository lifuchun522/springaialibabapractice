package com.example.digitalhuman.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话键的组装规则：userId:projectId:sessionId。
 *
 * <p>这是本掌最该先定死的一行规则——一旦串线，体验、评测、历史查询全部失去意义。
 */
class ConversationIdTest {

    @Test
    @DisplayName("of_shouldComposeThreeLevels")
    void of_shouldComposeThreeLevels() {
        ConversationId id = ConversationId.of(7L, 3L, "abc123");

        assertThat(id.value()).isEqualTo("7:3:abc123");
        assertThat(id.sessionId()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("of_shouldMakeMissingPartsVisibleInsteadOfSharingDefault")
    void of_shouldMakeMissingPartsVisibleInsteadOfSharingDefault() {
        // 匿名 + 无项目 + 无 sessionId：全部落到显式占位符，日志里一眼可辨
        assertThat(ConversationId.of(null, null, null).value()).isEqualTo("anon:0:default");
        // 同一项目下两个浏览器会话必须是两个 key（项目隔离不等于会话隔离）
        assertThat(ConversationId.of(null, 5L, "s1").value())
                .isNotEqualTo(ConversationId.of(null, 5L, "s2").value());
        // 同一会话下两个用户也不能共享
        assertThat(ConversationId.of(1L, 5L, "s1").value())
                .isNotEqualTo(ConversationId.of(2L, 5L, "s1").value());
    }

    @Test
    @DisplayName("of_shouldRejectSessionIdWithUnexpectedCharacters")
    void of_shouldRejectSessionIdWithUnexpectedCharacters() {
        assertThatThrownBy(() -> ConversationId.of(1L, 1L, "有空格 的 id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
        assertThatThrownBy(() -> ConversationId.of(1L, 1L, "x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
