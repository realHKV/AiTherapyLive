package com.hkv.AiTherapy.service.job;

import com.hkv.AiTherapy.service.payment.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs daily to downgrade PRO users whose subscription has expired back to FREE.
 */
@Component
public class SubscriptionDowngradeJob {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionDowngradeJob.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionDowngradeJob(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /** Run once every 24 hours (at midnight). */
    @Scheduled(cron = "0 0 0 * * *")
    public void downgradeExpiredSubscriptions() {
        int count = subscriptionService.downgradeExpiredUsers();
        if (count > 0) {
            log.info("Downgraded {} expired PRO subscription(s) to FREE.", count);
        }
    }
}
