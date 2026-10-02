package com.hongmap.hongmapbackend.push;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hongmap.hongmapbackend.user.UserDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 푸시 토큰 원문이 로그에 남지 않는지(보안 점검 로깅 항목). */
class ExpoPushSenderLogTest {

    private static final String TOKEN = "ExponentPushToken[AbCdEfGhIjKlMnOpQrSt1234]";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExpoPushSender.class);

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    @Test
    void 토큰은_끝_4자만_남긴다() {
        assertThat(PushTokenMasker.mask(TOKEN)).isEqualTo("ExponentPushToken[…1234]");
        assertThat(PushTokenMasker.mask("fcm-raw-token-xyz9")).isEqualTo("…xyz9");
        assertThat(PushTokenMasker.mask("abc")).isEqualTo("…");
        assertThat(PushTokenMasker.mask(null)).isNull();
        assertThat(PushTokenMasker.maskWithin("\"" + TOKEN + "\" is not a valid Expo push token"))
                .isEqualTo("\"ExponentPushToken[…1234]\" is not a valid Expo push token");
    }

    @Test
    void Expo가_거부한_메시지_로그에_토큰_원문이_없다() {
        ExpoPushClient client = mock(ExpoPushClient.class);
        when(client.send(anyList())).thenReturn(List.of(new ExpoPushTicket("error", null,
                "\"" + TOKEN + "\" is not a valid Expo push token",
                Map.of("error", "InvalidCredentials", "expoPushToken", TOKEN))));
        ExpoPushSender sender = new ExpoPushSender(client, mock(UserDeviceRepository.class));

        sender.sendAll(List.of(ExpoPushMessage.of(TOKEN, "t", "b", Map.of())));

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                .doesNotContain("AbCdEfGhIjKlMnOpQrSt1234"));
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("ExponentPushToken[…1234]").contains("InvalidCredentials");
    }
}
