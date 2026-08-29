package com.example.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体。
 * 学习阶段 user-service 和 order-service 共用 micro 库（表分开：t_user / t_order）；
 * 真实微服务应是"一个服务一个库"，模块 09 讲 Seata 时会展开这个设计取舍。
 */
@Getter
@Setter
@Entity
@Table(name = "t_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 订单号：业务上对外展示的编号（与数据库自增主键 id 区分） */
    @Column(nullable = false, unique = true, length = 32)
    private String orderNo;

    /** 下单用户的 id。模块 03 起会通过 OpenFeign 去 user-service 校验该用户是否存在 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    /** 订单金额：金额一律用 BigDecimal，禁止用 double（精度丢失） */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** 订单状态：CREATED 已创建 / PAID 已支付 / CANCELLED 已取消 */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
