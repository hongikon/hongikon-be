package com.hongmap.hongmapbackend.user;

import com.hongmap.hongmapbackend.user.dto.MeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AppNicknameService {

    static final String DUPLICATE_MESSAGE = "이미 다른 사람이 쓰고 있는 닉네임이에요.";

    private final UserRepository userRepository;
    private final AppNicknameChangeLimiter changeLimiter;

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        return MeResponse.of(findUser(userId));
    }

    /** 빈 값(null·공백)이면 지운다. 같은 값으로 다시 저장하는 건 횟수에 넣지 않는다. */
    @Transactional
    public MeResponse change(Long userId, String rawNickname) {
        User user = findUser(userId);
        String nickname = AppNicknamePolicy.normalize(rawNickname);
        if (Objects.equals(nickname, user.getAppNickname())) {
            return MeResponse.of(user);
        }
        if (nickname != null) {
            AppNicknamePolicy.violation(nickname).ifPresent(message -> {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
            });
            if (userRepository.existsByAppNicknameIgnoreCaseAndIdNot(nickname, userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
            }
        }
        if (!changeLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "닉네임은 하루에 " + AppNicknameChangeLimiter.MAX_CHANGES + "번까지 바꿀 수 있어요. 내일 다시 시도해 주세요.");
        }
        user.changeAppNickname(nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 닉네임을 저장한 경우 유니크 인덱스에서 걸린다.
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_MESSAGE);
        }
        return MeResponse.of(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
