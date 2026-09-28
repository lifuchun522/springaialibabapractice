package com.example.digitalhuman.mcp;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * MCP Server 侧的工具单测：成功与业务失败走同一个返回结构，调用方不需要靠异常类型猜。
 */
class ShowroomBookingToolsTest {

    private final ShowroomBookingRepository repository = mock(ShowroomBookingRepository.class);
    private final ShowroomBookingTools tools = new ShowroomBookingTools(repository);

    private static ShowroomBooking booking(String date, String label, int capacity, int booked) {
        try {
            var constructor = ShowroomBooking.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ShowroomBooking entity = constructor.newInstance();
            set(entity, "slotDate", LocalDate.parse(date));
            set(entity, "slotLabel", label);
            set(entity, "capacity", capacity);
            set(entity, "booked", booked);
            set(entity, "showroom", "深圳展厅");
            return entity;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void set(ShowroomBooking entity, String field, Object value) throws ReflectiveOperationException {
        var declared = ShowroomBooking.class.getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(entity, value);
    }

    @Test
    @DisplayName("queryAvailability_shouldReturnRemainingPerSlot")
    void queryAvailability_shouldReturnRemainingPerSlot() {
        when(repository.findByShowroomAndSlotDateBetweenOrderBySlotDateAscIdAsc(any(), any(), any()))
                .thenReturn(List.of(booking("2026-10-03", "上午场", 30, 22),
                        booking("2026-10-03", "下午场", 30, 30)));

        var result = tools.queryAvailability("深圳展厅", "2026-10-03", null, null);

        assertThat(result.status()).isEqualTo("OK");
        assertThat(result.error()).isNull();
        assertThat(result.slots()).hasSize(2);
        assertThat(result.slots().get(0).remaining()).isEqualTo(8);
        assertThat(result.slots().get(1).remaining()).isZero();     // 满场也要如实返回
    }

    @Test
    @DisplayName("queryAvailability_shouldFilterByPeriod")
    void queryAvailability_shouldFilterByPeriod() {
        when(repository.findByShowroomAndSlotDateBetweenOrderBySlotDateAscIdAsc(any(), any(), any()))
                .thenReturn(List.of(booking("2026-10-04", "上午场", 30, 12),
                        booking("2026-10-04", "下午场", 30, 27)));

        var morning = tools.queryAvailability("深圳展厅", "2026-10-04", null,
                ShowroomBookingTools.SlotPeriod.MORNING);

        assertThat(morning.slots()).hasSize(1);
        assertThat(morning.slots().get(0).period()).isEqualTo("上午场");
        assertThat(morning.slots().get(0).remaining()).isEqualTo(18);
    }

    @Test
    @DisplayName("queryAvailability_shouldReturnBusinessErrorCodeInsteadOfThrowing")
    void queryAvailability_shouldReturnBusinessErrorCodeInsteadOfThrowing() {
        when(repository.findByShowroomAndSlotDateBetweenOrderBySlotDateAscIdAsc(any(), any(), any()))
                .thenReturn(List.of());

        var result = tools.queryAvailability("深圳展厅", "2026-12-31", null, null);

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.error().code()).isEqualTo("NO_SLOT_AVAILABLE");
        assertThat(result.slots()).isEmpty();
    }

    @Test
    @DisplayName("queryAvailability_shouldRejectMalformedDateAsInvalidArgument")
    void queryAvailability_shouldRejectMalformedDateAsInvalidArgument() {
        var result = tools.queryAvailability("深圳展厅", "十月三号", null, null);

        assertThat(result.error().code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(result.error().message()).contains("yyyy-MM-dd");
    }

    @Test
    @DisplayName("queryAvailability_shouldRejectBlankShowroom")
    void queryAvailability_shouldRejectBlankShowroom() {
        var result = tools.queryAvailability("  ", "2026-10-03", null, null);

        assertThat(result.error().code()).isEqualTo("INVALID_ARGUMENT");
    }
}
