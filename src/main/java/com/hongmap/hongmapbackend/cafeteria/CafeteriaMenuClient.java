package com.hongmap.hongmapbackend.cafeteria;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 홍익대 홈페이지의 식당 메뉴 JSON 을 가져온다.
 *
 * <b>보안(SSRF)</b>: 학교의 /sso/APICipher2.jsp 는 data.url 로 받은 내부 경로를 그대로 중계하는 범용 프록시다.
 * 그래서 요청 주소는 {@link #SOURCE_URI} 하나로 고정하고, 어떤 입력(요청 파라미터·DB 값·설정)도 주소에 넣지 않는다.
 * 리다이렉트는 따라가지 않는다(HttpClient 기본 NEVER). 연결·응답 시간과 본문 크기에 상한을 둔다.
 */
@Component
public class CafeteriaMenuClient {

    static final String SOURCE_BASE = "https://www.hongik.ac.kr/sso/APICipher2.jsp";
    /** 서울캠퍼스(CAMPUS=0) 이번 주 메뉴 목록. 바꾸지 않는다. */
    static final String SOURCE_DATA = "{\"url\":\"/homepage/get_food_list.php\",\"url2\":\"CAMPUS=\",\"url3\":\"0\"}";
    /** 실제로 부르는 유일한 주소. */
    public static final URI SOURCE_URI = URI.create(SOURCE_BASE + "?data="
            + URLEncoder.encode(SOURCE_DATA, StandardCharsets.UTF_8));

    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    /** 주소 하나를 받아 본문을 돌려주는 전송 계층. 테스트가 바꿔 끼운다(네트워크 없음). */
    @FunctionalInterface
    interface Transport {
        String get(URI uri) throws IOException, InterruptedException;
    }

    private final Transport transport;

    @Autowired
    public CafeteriaMenuClient(@Value("${menu.fetch.connect-timeout-ms:5000}") long connectTimeoutMs,
                               @Value("${menu.fetch.read-timeout-ms:10000}") long readTimeoutMs,
                               @Value("${menu.fetch.user-agent:HongikOnBot/1.0 (+https://hongikon.com/support; hongikonsupport@gmail.com)}")
                               String userAgent) {
        this(httpTransport(Duration.ofMillis(connectTimeoutMs), Duration.ofMillis(readTimeoutMs), userAgent));
    }

    CafeteriaMenuClient(Transport transport) {
        this.transport = transport;
    }

    /** 메뉴 JSON 본문. 실패하면 IOException(HTTP 오류·시간 초과·크기 초과 포함). */
    public String fetch() throws IOException, InterruptedException {
        return transport.get(SOURCE_URI);
    }

    private static Transport httpTransport(Duration connectTimeout, Duration readTimeout, String userAgent) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        long totalTimeoutMs = connectTimeout.plus(readTimeout).toMillis();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(readTimeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();
            // 응답 헤더까지는 request.timeout, 연결·본문까지 합친 전체는 아래 get(connect + read) 로 끊는다.
            CompletableFuture<String> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(response -> {
                        try (InputStream body = response.body()) {
                            if (response.statusCode() != 200) {
                                throw new UncheckedIOException(new IOException("HTTP " + response.statusCode()));
                            }
                            return new String(readCapped(body, MAX_BODY_BYTES), StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            try {
                return future.get(totalTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new HttpTimeoutException("응답 시간 초과(" + totalTimeoutMs + "ms)");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() instanceof UncheckedIOException u ? u.getCause() : e.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                throw new IOException(cause == null ? "요청 실패" : cause.getClass().getSimpleName(), cause);
            }
        };
    }

    /** 최대 maxBytes 까지 읽는다. 넘으면 IOException. */
    static byte[] readCapped(InputStream in, int maxBytes) throws IOException {
        byte[] buffer = in.readNBytes(maxBytes + 1);
        if (buffer.length > maxBytes) {
            throw new IOException("응답이 너무 큼(>" + maxBytes + " bytes)");
        }
        return Arrays.copyOf(buffer, buffer.length);
    }
}
