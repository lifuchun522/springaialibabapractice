-- MCP Server 自己的数据：展厅预约。
-- 关键点：这张表只存在于 Server 侧——数字人服务没有它，想拿数据只能走 MCP 协议。
-- 这就是「能力归属」的物证：拆出去的不是一段代码，是一份数据的归属。

CREATE TABLE showroom_booking (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    showroom     VARCHAR(64)  NOT NULL,
    slot_date    DATE         NOT NULL,
    slot_label   VARCHAR(32)  NOT NULL,
    capacity     INT          NOT NULL,
    booked       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_showroom_booking_slot (showroom, slot_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

INSERT INTO showroom_booking (showroom, slot_date, slot_label, capacity, booked) VALUES
    ('深圳展厅', '2026-10-03', '上午场', 30, 22),
    ('深圳展厅', '2026-10-03', '下午场', 30, 30),
    ('深圳展厅', '2026-10-04', '上午场', 30, 12),
    ('深圳展厅', '2026-10-04', '下午场', 30, 27),
    ('深圳展厅', '2026-10-05', '上午场', 30, 5),
    ('深圳展厅', '2026-10-05', '下午场', 30, 18);
