package com.hbsd.labselect.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** 实训室统一面试排期。 */
public record ScheduleReq(
        Long tutorId,
        LocalDate interviewDate,
        LocalTime startTime,
        LocalTime endTime,
        String location,
        Integer capacity) {
}
