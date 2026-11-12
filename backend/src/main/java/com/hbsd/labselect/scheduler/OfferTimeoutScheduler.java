package com.hbsd.labselect.scheduler;

import com.hbsd.labselect.service.OfferService;
import com.hbsd.labselect.service.PhaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 阶段 5 的自动化：
 *   1. 每 30 秒扫描到期未确认的 Offer，超时视为放弃并顺延递补；
 *   2. 确认阶段内，对"只收到 1 个 Offer"的学生执行自动确认。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferTimeoutScheduler {

    private final OfferService offerService;
    private final PhaseService phaseService;

    @Scheduled(fixedDelayString = "${app.offer.scan-interval-ms:30000}", initialDelay = 20000)
    public void scan() {
        try {
            int expired = offerService.handleTimeouts();
            if (expired > 0) {
                log.info("阶段5：{} 条 Offer 超时未确认，已释放名额并顺延递补", expired);
            }
            if (phaseService.inWindow(PhaseService.CONFIRM)) {
                int auto = offerService.autoConfirmSingles();
                if (auto > 0) {
                    log.info("阶段5：{} 名学生仅收到 1 个 Offer，已自动确认", auto);
                }
            }
        } catch (Exception e) {
            log.warn("Offer 定时任务执行失败: {}", e.getMessage());
        }
    }
}
