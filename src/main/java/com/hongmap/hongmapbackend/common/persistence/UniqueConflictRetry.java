package com.hongmap.hongmapbackend.common.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * "없으면 만든다(확인 → INSERT)" 를 유니크 키 경합에 안전하게 돌리는 도우미.
 *
 * 같은 요청이 거의 동시에 두 번 오면(더블 탭, 앱 재시도, 재로그인 직후 기기 등록이 두 번 등) 둘 다 "없음"을 보고 INSERT 해서
 * 늦은 쪽이 유니크 키 위반(DataIntegrityViolationException)으로 500 이 났다. 여기서는 작업을 <b>새 트랜잭션</b>으로 돌리고,
 * 유니크 위반이면 <b>한 번만</b> 새 트랜잭션으로 다시 돌린다 — 두 번째에는 먼저 끝난 쪽의 행이 보여 "이미 있음" 경로(기존 행
 * 반환·갱신)로 간다. 위반이 난 트랜잭션은 rollback-only 가 되므로 같은 트랜잭션 안에서 잡아 이어 가면 커밋 때 터진다 —
 * 그래서 REQUIRES_NEW 로 매번 따로 연다. 두 번째도 실패하면 그대로 던지고 GlobalExceptionHandler 가 409 로 바꾼다.
 *
 * 작업(work)은 반환값(DTO)까지 안에서 만들어야 한다 — 트랜잭션이 끝난 뒤엔 지연 로딩이 안 된다.
 * 호출하는 쪽 public 메서드에는 @Transactional 을 붙이지 않는다(붙이면 바깥 트랜잭션 커넥션을 쥔 채 새 커넥션을 하나 더 쓴다).
 */
@Slf4j
@Component
public class UniqueConflictRetry {

    private final TransactionTemplate requiresNew;

    public UniqueConflictRetry(PlatformTransactionManager transactionManager) {
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public <T> T execute(String label, Supplier<T> work) {
        try {
            return requiresNew.execute(status -> work.get());
        } catch (DataIntegrityViolationException first) {
            // 값(토큰·키워드 등)이 메시지에 들어 있을 수 있어 예외 메시지는 남기지 않는다.
            log.info("유니크 키 경합으로 한 번 더 시도: {}", label);
            return requiresNew.execute(status -> work.get());
        }
    }
}
