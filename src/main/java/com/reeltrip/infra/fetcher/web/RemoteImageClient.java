package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * 외부 사진 한 장을 내려받아요 (#87 카드 사진 보관용).
 * 링크 본문 수집과 같은 규칙을 써요: https만, 공개 주소만, 이동할 때마다 다시 검사, 검사한 IP로만 접속, 본문 1MiB 이하.
 */
@Component
public class RemoteImageClient {
    private static final int MAX_REDIRECTS = 2;
    private static final List<String> IMAGE_TYPES = List.of("image/jpeg", "image/png");

    private final PublicNetworkPolicy policy;
    private final PinnedHttpTransport transport;

    public RemoteImageClient(PublicNetworkPolicy policy, PinnedHttpTransport transport) {
        this.policy = policy;
        this.transport = transport;
    }

    public Downloaded download(String url, Duration timeout) throws IOException {
        var deadline = new FetchDeadline(timeout);
        URI current = PublicNetworkPolicy.parse(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!"https".equalsIgnoreCase(current.getScheme())) { throw new IOException("https 사진만 가져와요."); }
            var addresses = policy.resolve(current, deadline);
            var response = transport.exchange(current, addresses.getFirst(), deadline);
            if (List.of(301, 302, 303, 307, 308).contains(response.status())) {
                String location = response.headers().get("location");
                if (hop == MAX_REDIRECTS || location == null || location.isBlank()) {
                    throw new IOException("사진 주소 이동을 완료하지 못했어요.");
                }
                try {
                    current = PublicNetworkPolicy.parse(current.resolve(location).toString());
                } catch (IllegalArgumentException e) {
                    throw new IOException("사진 주소가 잘못됐어요.");
                }
                continue;
            }
            String mime = response.headers().getOrDefault("content-type", "").split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (response.status() != 200 || !IMAGE_TYPES.contains(mime) || response.body().length == 0) {
                throw new IOException("사진을 가져오지 못했어요.");
            }
            return new Downloaded(response.body(), mime);
        }
        throw new IOException("사진 주소 이동을 완료하지 못했어요.");
    }

    public record Downloaded(byte[] bytes, String contentType) {
    }
}
