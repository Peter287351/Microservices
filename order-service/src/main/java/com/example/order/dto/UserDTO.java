package com.example.order.dto;

/**
 * 用户信息的"本地影子"（DTO）。
 * 微服务铁律：order-service 不能直接引用 user-service 的实体类——那是人家的内部结构。
 * 只能按对方接口返回的 JSON 结构，在自己这边定义一个字段对应的 DTO。
 * 两个服务之间只通过 HTTP + JSON 这个"契约"耦合，内部实现互不可见。
 */
public record UserDTO(Long id, String username, String email, String phone) {
}
