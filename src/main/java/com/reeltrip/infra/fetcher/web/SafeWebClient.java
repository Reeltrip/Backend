package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

@Component
public class SafeWebClient {

    private final PublicNetworkPolicy policy;
    private final PinnedHttpTransport transport;

    public SafeWebClient(PublicNetworkPolicy policy, PinnedHttpTransport transport) {
        this.policy = policy;
        this.transport = transport;
    }

    Page fetch(URI original, FetchDeadline deadline) throws IOException {
        return fetch(original, deadline, uri -> true);
    }

    Page fetch(URI original, FetchDeadline deadline, Predicate<URI> allowedTarget) throws IOException {
        URI current = original;
        var visited = new HashSet<URI>();
        for (int hop = 0; hop <= 3; hop++) {
            current = PublicNetworkPolicy.parse(current.toString());
            // 제한된 단축 링크는 DNS 조회·접속 전에 이동 대상을 검사해요.
            if (!allowedTarget.test(current)) { throw new IOException("허용하지 않은 이동 주소예요."); }
            if (!visited.add(current.normalize())) {
                throw new IOException("리디렉션이 반복돼요.");
            }
            var addresses = policy.resolve(current, deadline);
            var response = transport.exchange(current, addresses.getFirst(), deadline);
            if (List.of(301, 302, 303, 307, 308).contains(response.status())) {
                String location = response.headers().get("location");
                if (hop == 3 || location == null || location.isBlank()) {
                    throw new IOException("리디렉션을 완료하지 못했어요.");
                }
                URI next;
                try {
                    next = PublicNetworkPolicy.parse(current.resolve(location).toString());
                } catch (IllegalArgumentException e) {
                    throw new IOException("리디렉션 주소가 잘못됐어요.");
                }
                if (current.getScheme().equalsIgnoreCase("https") && !next.getScheme().equalsIgnoreCase("https")) {
                    throw new IOException("HTTPS에서 HTTP로 이동하지 않아요.");
                }
                current = next;
                continue;
            }
            String type = response.headers().getOrDefault("content-type", "");
            String mime = type.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (response.status() != 200 || !List.of("text/html", "application/xhtml+xml").contains(mime)) {
                throw new IOException("공개 HTML 본문을 가져오지 못했어요.");
            }
            return new Page(current, type, response.body());
        }
        throw new IOException("리디렉션을 완료하지 못했어요.");
    }

    String thumbnail(String value, URI base, FetchDeadline deadline) {
        try {
            URI uri = PublicNetworkPolicy.parse(base.resolve(value).toString());
            if (!uri.getScheme().equalsIgnoreCase("https")) {
                return null;
            }
            policy.resolve(uri, deadline);
            return uri.toString();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    record Page(URI uri, String contentType, byte[] bytes) {
    }
}
