package com.example.digitalhuman.ai;

/**
 * 一个可用的模型条目。
 *
 * <p>{@code provider} 是路由键（决定请求打到哪个 endpoint、用哪套鉴权头），
 * {@code id} 只是该 provider 下面的一个普通字符串——这两件事不能混成一个字段。
 *
 * @param id          传给 provider 的模型名，例如 deepseek-flash
 * @param provider    提供方标识，例如 deepseek
 * @param displayName 给运营看的中文名
 * @param description 适用场景说明，供后台下拉选择时展示
 */
public record ModelDefinition(String id, String provider, String displayName, String description) {
}
