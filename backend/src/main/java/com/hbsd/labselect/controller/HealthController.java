package com.hbsd.labselect.controller;

import com.hbsd.labselect.common.R;
import com.hbsd.labselect.service.PhaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final PhaseService phaseService;

    @GetMapping("/api/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "UP");
        map.put("time", LocalDateTime.now());
        map.put("phase", phaseService.active().map(p -> p.getCode()).orElse("NONE"));
        map.put("round", phaseService.currentRound());
        return R.ok(map);
    }
}
