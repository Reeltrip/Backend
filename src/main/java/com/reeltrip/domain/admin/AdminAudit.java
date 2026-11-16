package com.reeltrip.domain.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AdminAudit {
    private static final Logger log = LoggerFactory.getLogger(AdminAudit.class);

    public void changed(Long actor, String action, Long owner, Long resource) {
        // 롤백된 작업을 성공 기록으로 남기지 않아요. 본문·주소·비밀번호·토큰은 기록하지 않아요.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                log.info("관리자 변경: actorId={}, action={}, targetUserId={}, resourceId={}", actor, action, owner, resource);
            }
        });
    }
}
