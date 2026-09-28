package com.example.digitalhuman.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 串行闸门：同一会话同时只允许一条在途请求。 */
class ConversationGuardTest {

    private final ConversationGuard guard = new ConversationGuard();

    @Test
    @DisplayName("guard_shouldAllowOnlyOneInFlightPerConversation")
    void guard_shouldAllowOnlyOneInFlightPerConversation() {
        assertThat(guard.tryAcquire("anon:1:s1")).isTrue();
        assertThat(guard.tryAcquire("anon:1:s1")).isFalse();
        assertThat(guard.inFlight()).isEqualTo(1);

        // 不同会话互不影响
        assertThat(guard.tryAcquire("anon:1:s2")).isTrue();
        assertThat(guard.inFlight()).isEqualTo(2);

        guard.release("anon:1:s1");
        assertThat(guard.tryAcquire("anon:1:s1")).isTrue();
    }
}
