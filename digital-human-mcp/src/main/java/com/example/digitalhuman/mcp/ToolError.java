package com.example.digitalhuman.mcp;

/**
 * 业务错误的统一结构。
 *
 * <p>MCP 把「工具不存在」「参数不符合 Schema」「工具执行失败」在协议层分开表达，
 * 但「客户不存在」这种**业务**错误协议不认识——它必须由 Server 自己用带 code 的结构回出去，
 * 而不是把 Java 异常原文丢给调用方。调用方要能据此写出确定的分支。
 */
public record ToolError(String code, String message) {

    public static ToolError notFound(String what) {
        return new ToolError("SHOWROOM_NOT_FOUND", what);
    }

    public static ToolError noSlots(String showroom, String date) {
        return new ToolError("NO_SLOT_AVAILABLE",
                "展厅「" + showroom + "」在 " + date + " 没有排期，请换一天或换展厅。");
    }

    public static ToolError badArgument(String message) {
        return new ToolError("INVALID_ARGUMENT", message);
    }
}
