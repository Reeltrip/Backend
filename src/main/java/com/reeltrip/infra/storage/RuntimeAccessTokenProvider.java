package com.reeltrip.infra.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

@Component
public class RuntimeAccessTokenProvider implements AccessTokenProvider {
    private final BoundedHttp http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String localToken;
    private String cached;
    private Instant until = Instant.EPOCH;

    public RuntimeAccessTokenProvider(BoundedHttp http, ObjectMapper mapper, Clock clock,
            @Value("${GCP_ACCESS_TOKEN:}") String localToken) {
        this.http=http; this.mapper=mapper; this.clock=clock; this.localToken=localToken;
    }

    @Override public synchronized String token() throws IOException {
        if (!localToken.isBlank()) { return localToken; }
        if (cached != null && clock.instant().isBefore(until)) { return cached; }
        // 런타임 서비스 계정용 고정 메타데이터 주소이며 사용자 입력은 받지 않아요.
        var request=HttpRequest.newBuilder(URI.create("http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"))
                .header("Metadata-Flavor", "Google").timeout(Duration.ofSeconds(2)).GET().build();
        var response=http.send(request, 8192, Duration.ofSeconds(3));
        if(response.statusCode()!=200) { throw new IOException("런타임 인증을 사용할 수 없어요."); }
        var body=mapper.readTree(response.body());
        String value=body.path("access_token").asText();
        long seconds=body.path("expires_in").asLong();
        if(value.isBlank() || seconds<60 || seconds>7200) { throw new IOException("런타임 인증 응답이 잘못됐어요."); }
        cached=value; until=clock.instant().plusSeconds(seconds-60);
        return value;
    }
}
