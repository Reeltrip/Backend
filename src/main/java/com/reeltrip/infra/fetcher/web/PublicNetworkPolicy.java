package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/** DNS에서 검사한 주소를 그대로 소켓에 넘겨 재조회·리바인딩을 막아요. */
@Component
public class PublicNetworkPolicy {

    private final ThreadPoolExecutor dns = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("web-dns-", 0).factory());
    private final Lookup lookup;

    public PublicNetworkPolicy() {
        this(InetAddress::getAllByName);
    }

    PublicNetworkPolicy(Lookup lookup) {
        this.lookup = lookup;
    }

    static URI parse(String value) throws IOException {
        try {
            if (value == null || value.isBlank() || value.length() > 2048) {
                throw new IOException("지원하지 않는 웹 주소예요.");
            }
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            boolean https = "https".equalsIgnoreCase(scheme);
            if ((!https && !"http".equalsIgnoreCase(scheme)) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != (https ? 443 : 80))) {
                throw new IOException("지원하지 않는 웹 주소예요.");
            }
            String host = host(uri).toLowerCase(Locale.ROOT);
            if (host.contains("%") || host.equals("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".")) {
                throw new IOException("내부 주소는 수집하지 않아요.");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IOException("지원하지 않는 웹 주소예요.");
        }
    }

    List<InetAddress> resolve(URI uri, FetchDeadline deadline) throws IOException {
        var result = dns.submit(() -> lookup.resolve(host(uri)));
        try {
            InetAddress[] addresses = result.get(deadline.millis(2000), TimeUnit.MILLISECONDS);
            if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !isPublic(address))) {
                throw new IOException("공개 인터넷 주소만 수집할 수 있어요.");
            }
            return List.copyOf(Arrays.asList(addresses));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("수집 작업이 중단됐어요.");
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("웹 주소를 확인하지 못했어요.");
        } finally {
            result.cancel(true);
        }
    }

    static String host(URI uri) {
        String host = uri.getHost();
        return host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        if (bytes.length == 4) {
            int c = Byte.toUnsignedInt(bytes[2]);
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && b >= 64 && b <= 127) && !(a == 169 && b == 254)
                    && !(a == 172 && b >= 16 && b <= 31) && !(a == 192 && b == 168)
                    && !(a == 192 && b == 0 && (c == 0 || c == 2)) && !(a == 192 && b == 88 && c == 99)
                    && !(a == 198 && (b == 18 || b == 19)) && !(a == 198 && b == 51 && c == 100)
                    && !(a == 203 && b == 0 && c == 113);
        }
        // IPv6는 전역 유니캐스트만 허용하고 전환·문서용 대역도 제외해요.
        return (a & 0xe0) == 0x20 && !(a == 0x20 && b == 0x02)
                && !(a == 0x20 && b == 0x01 && Byte.toUnsignedInt(bytes[2]) < 2)
                && !(a == 0x20 && b == 0x01 && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8);
    }

    @PreDestroy
    void shutdown() {
        dns.shutdownNow();
    }

    @FunctionalInterface
    interface Lookup {
        InetAddress[] resolve(String host) throws IOException;
    }
}
