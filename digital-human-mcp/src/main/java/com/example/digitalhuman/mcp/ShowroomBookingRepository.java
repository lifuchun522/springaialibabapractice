package com.example.digitalhuman.mcp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowroomBookingRepository extends JpaRepository<ShowroomBooking, Long> {

    List<ShowroomBooking> findByShowroomAndSlotDateOrderByIdAsc(String showroom, LocalDate slotDate);

    List<ShowroomBooking> findByShowroomAndSlotDateBetweenOrderBySlotDateAscIdAsc(
            String showroom, LocalDate from, LocalDate to);
}
