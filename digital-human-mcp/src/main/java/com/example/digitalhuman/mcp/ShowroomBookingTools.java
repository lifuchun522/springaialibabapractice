package com.example.digitalhuman.mcp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 展厅预约查询能力（MCP Server 侧）。
 *
 * <p>两条设计约束，都是被远程化逼出来的：
 * <ol>
 *   <li><b>描述就是接口契约</b>：远程调用方只能读到描述与 Schema，签名帮不上忙，
 *       所以日期格式、展厅名、枚举值都要写死在描述与类型里；</li>
 *   <li><b>业务错误要带 code 返回</b>：协议只分得清「工具不存在 / 参数不合 Schema / 执行失败」，
 *       「这天没排期」属于业务语义，必须由这里回结构化结果，而不是抛 Java 异常。</li>
 * </ol>
 */
@Component
public class ShowroomBookingTools {

    private final ShowroomBookingRepository bookings;

    public ShowroomBookingTools(ShowroomBookingRepository bookings) {
        this.bookings = bookings;
    }

    public enum SlotPeriod {
        /** 全天，按场次逐条返回。 */
        ALL,
        /** 只返回上午场。 */
        MORNING,
        /** 只返回下午场。 */
        AFTERNOON
    }

    @Tool(name = "showroom_query_availability",
            description = "查询深圳展厅某一天（或某段日期区间）的预约余位。"
                    + "日期格式必须是 yyyy-MM-dd；只支持深圳展厅。"
                    + "返回每个场次的总容量、已预约与剩余名额；若当天没有排期，返回 NO_SLOT_AVAILABLE 错误码。")
    public BookingAvailability queryAvailability(
            @ToolParam(description = "展厅名称，目前只支持「深圳展厅」") String showroom,
            @ToolParam(description = "开始日期，格式 yyyy-MM-dd，例如 2026-10-03") String fromDate,
            @ToolParam(description = "结束日期，格式 yyyy-MM-dd；留空表示只查开始日期那一天", required = false) String toDate,
            @ToolParam(description = "场次筛选：ALL=全部，MORNING=上午场，AFTERNOON=下午场；留空等同 ALL", required = false)
            SlotPeriod period) {

        if (showroom == null || showroom.isBlank()) {
            return BookingAvailability.failed(ToolError.badArgument("showroom 不能为空，目前只支持「深圳展厅」"));
        }
        LocalDate from;
        try {
            from = LocalDate.parse(fromDate);
        } catch (RuntimeException ex) {
            return BookingAvailability.failed(ToolError.badArgument("fromDate 必须是 yyyy-MM-dd 格式：" + fromDate));
        }
        LocalDate to = toDate == null || toDate.isBlank() ? from : LocalDate.parse(toDate);
        SlotPeriod effectivePeriod = period == null ? SlotPeriod.ALL : period;

        List<ShowroomBooking> slots = bookings.findByShowroomAndSlotDateBetweenOrderBySlotDateAscIdAsc(
                showroom.trim(), from, to).stream()
                .filter(slot -> matches(effectivePeriod, slot))
                .toList();

        if (slots.isEmpty()) {
            return BookingAvailability.failed(ToolError.noSlots(showroom, from + (from.equals(to) ? "" : " ~ " + to)));
        }
        return BookingAvailability.available(showroom, slots);
    }

    private static boolean matches(SlotPeriod period, ShowroomBooking slot) {
        return switch (period) {
            case ALL -> true;
            case MORNING -> slot.getSlotLabel().contains("上午");
            case AFTERNOON -> slot.getSlotLabel().contains("下午");
        };
    }

    /** 成功与业务失败走同一个返回结构：调用方不需要靠异常类型猜发生了什么。 */
    public record BookingAvailability(String status, String showroom, List<Slot> slots, ToolError error) {

        public record Slot(String date, String period, int capacity, int booked, int remaining) {
        }

        static BookingAvailability available(String showroom, List<ShowroomBooking> rows) {
            return new BookingAvailability("OK", showroom, rows.stream()
                    .map(row -> new Slot(String.valueOf(row.getSlotDate()), row.getSlotLabel(),
                            row.getCapacity(), row.getBooked(), row.remaining()))
                    .toList(), null);
        }

        static BookingAvailability failed(ToolError error) {
            return new BookingAvailability("ERROR", null, List.of(), error);
        }
    }
}
