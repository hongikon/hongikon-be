package com.hongmap.hongmapbackend.user;

import jakarta.persistence.PrePersist;
import org.springframework.stereotype.Component;

/**
 * User 를 처음 저장할 때 회원 번호를 채우는 엔티티 리스너. 카카오·Apple·테스트 토큰 등 가입 경로가 어디든
 * User.builder() 로 만들어 저장하기만 하면 번호가 붙으므로, 경로마다 따로 챙길 필요가 없다.
 * Spring Boot 가 Hibernate 에 SpringBeanContainer 를 연결해 두므로 생성자 주입이 된다.
 */
@Component
public class MemberCodeAssigner {

    private final MemberCodeGenerator generator;

    public MemberCodeAssigner(MemberCodeGenerator generator) {
        this.generator = generator;
    }

    @PrePersist
    void assign(User user) {
        if (user.getMemberCode() == null) {
            user.assignMemberCode(generator.nextAvailable());
        }
    }
}
