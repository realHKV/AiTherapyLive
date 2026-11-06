package com.hkv.AiTherapy.service.usage;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases abandoned reservations so a crashed worker cannot consume a user's allowance forever. */
@Component
public class UsageReconciliationJob {
    private final UsageAccountingService accounting;

    public UsageReconciliationJob(UsageAccountingService accounting) { this.accounting = accounting; }

    @Scheduled(fixedDelayString = "${ai.usage.reconcile-delay-ms:60000}")
    public void reconcileExpiredReservations() { accounting.reconcileExpiredReservations(); }
}
