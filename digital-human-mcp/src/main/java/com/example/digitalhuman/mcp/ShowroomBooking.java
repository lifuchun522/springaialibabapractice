package com.example.digitalhuman.mcp;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 展厅预约场次：这份数据归 MCP Server 所有。 */
@Entity
@Table(name = "showroom_booking")
public class ShowroomBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "showroom", nullable = false, length = 64)
    private String showroom;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_label", nullable = false, length = 32)
    private String slotLabel;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "booked", nullable = false)
    private int booked;

    protected ShowroomBooking() {
        // JPA 需要
    }

    public Long getId() {
        return id;
    }

    public String getShowroom() {
        return showroom;
    }

    public LocalDate getSlotDate() {
        return slotDate;
    }

    public String getSlotLabel() {
        return slotLabel;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getBooked() {
        return booked;
    }

    public int remaining() {
        return capacity - booked;
    }
}
