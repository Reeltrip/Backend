package com.reeltrip.infra.fetcher.web;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/** JDK 소켓으로 제한된 HTTP/1.1 GET만 보내요. 연결 재사용·자동 리디렉션은 하지 않아요. */
@Component
public class PinnedHttpTransport {

    static final int MAX_BODY = 1024 * 1024;
    private final ScheduledThreadPoolExecutor watchdog = new ScheduledThreadPoolExecutor(1,
            Thread.ofPlatform().daemon().name("web-deadline-", 0).factory());

    public PinnedHttpTransport() {
        watchdog.setRemoveOnCancelPolicy(true);
        watchdog.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    Response exchange(URI uri, InetAddress address, FetchDeadline deadline) throws IOException {
        int port = uri.getPort() == -1 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
        try (Socket plain = new Socket(Proxy.NO_PROXY)) {
            var expiry = watchdog.schedule(() -> {
                try {
                    plain.close();
                } catch (IOException ignored) {
                    // 이미 닫힌 소켓은 추가로 처리할 필요가 없어요.
                }
            }, deadline.millis(10_000), TimeUnit.MILLISECONDS);
            try {
                plain.connect(new InetSocketAddress(address, port), deadline.millis(2000));
                plain.setSoTimeout(deadline.millis(3000));
                if (uri.getScheme().equalsIgnoreCase("https")) {
                    try (SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                            .createSocket(plain, PublicNetworkPolicy.host(uri), port, true)) {
                        var parameters = tls.getSSLParameters();
                        parameters.setEndpointIdentificationAlgorithm("HTTPS");
                        if (!PublicNetworkPolicy.host(uri).contains(":") && !PublicNetworkPolicy.host(uri).matches("[0-9.]+")) {
                            parameters.setServerNames(List.of(new SNIHostName(PublicNetworkPolicy.host(uri))));
                        }
                        tls.setSSLParameters(parameters);
                        tls.setSoTimeout(deadline.millis(3000));
                        tls.startHandshake();
                        return send(tls, uri, deadline);
                    }
                }
                return send(plain, uri, deadline);
            } finally {
                expiry.cancel(false);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
    }

    private Response send(Socket socket, URI uri, FetchDeadline deadline) throws IOException {
        URI ascii = URI.create(uri.toASCIIString());
        String path = ascii.getRawPath().isEmpty() ? "/" : ascii.getRawPath();
        String target = path + (ascii.getRawQuery() == null ? "" : "?" + ascii.getRawQuery());
        String request = "GET " + target + " HTTP/1.1\r\nHost: " + ascii.getRawAuthority()
                + "\r\nUser-Agent: Wayvi-LinkFetcher/1.0\r\nAccept: text/html, application/xhtml+xml"
                + "\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        var timed = new FilterInputStream(socket.getInputStream()) {
            @Override public int read() throws IOException {
                socket.setSoTimeout(deadline.millis(3000));
                return super.read();
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                socket.setSoTimeout(deadline.millis(3000));
                return in.read(bytes, offset, length);
            }
        };
        try (InputStream input = new BufferedInputStream(timed)) {
            return readResponse(input);
        }
    }

    static Response readResponse(InputStream input) throws IOException {
        for (int informational = 0; informational < 4; informational++) {
            String status = line(input);
            if (!status.matches("HTTP/1\\.[01] [1-5][0-9]{2}( .*)?")) {
                throw new IOException("지원하지 않는 HTTP 응답이에요.");
            }
            int code = Integer.parseInt(status.substring(9, 12));
            Map<String, String> headers = headers(input);
            if (code >= 100 && code < 200 && code != 101) {
                continue;
            }
            // 오류·리디렉션 본문은 읽지 않고 소켓을 닫아요.
            if (code != 200) {
                return new Response(code, headers, new byte[0]);
            }
            String encoding = headers.getOrDefault("content-encoding", "identity");
            if (!encoding.equalsIgnoreCase("identity") && !encoding.equalsIgnoreCase("gzip")) {
                throw new IOException("압축 응답은 지원하지 않아요.");
            }
            String transfer = headers.get("transfer-encoding");
            String length = headers.get("content-length");
            if (transfer != null && (length != null || !transfer.equalsIgnoreCase("chunked"))) {
                throw new IOException("지원하지 않는 응답 프레이밍이에요.");
            }
            byte[] body;
            if (transfer != null) {
                body = chunks(input);
            } else if (length != null) {
                long size = number(length, 10);
                if (size > MAX_BODY) {
                    throw new IOException("본문이 너무 커요.");
                }
                body = exact(input, (int) size);
            } else {
                body = input.readNBytes(MAX_BODY + 1);
                if (body.length > MAX_BODY) {
                    throw new IOException("본문이 너무 커요.");
                }
            }
            if (encoding.equalsIgnoreCase("gzip")) {
                try (var gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
                    body = gzip.readNBytes(MAX_BODY + 1);
                    if (body.length > MAX_BODY) {
                        throw new IOException("압축 해제한 본문이 너무 커요.");
                    }
                }
            }
            return new Response(code, headers, body);
        }
        throw new IOException("HTTP 응답이 끝나지 않아요.");
    }

    private static Map<String, String> headers(InputStream input) throws IOException {
        var headers = new LinkedHashMap<String, String>();
        int bytes = 0;
        for (int count = 0; count < 100; count++) {
            String line = line(input);
            bytes += line.length() + 2;
            if (bytes > 32 * 1024) {
                throw new IOException("응답 헤더가 너무 커요.");
            }
            if (line.isEmpty()) {
                return headers;
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || !line.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                throw new IOException("잘못된 응답 헤더예요.");
            }
            String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).strip();
            if (headers.putIfAbsent(name, value) != null
                    && List.of("content-length", "transfer-encoding", "content-type", "content-encoding", "location").contains(name)) {
                throw new IOException("중복된 응답 헤더예요.");
            }
        }
        throw new IOException("응답 헤더가 너무 많아요.");
    }

    private static byte[] chunks(InputStream input) throws IOException {
        var body = new ByteArrayOutputStream();
        for (int count = 0; count < 16_384; count++) {
            long size = number(line(input).split(";", 2)[0].strip(), 16);
            if (size > MAX_BODY - body.size()) {
                throw new IOException("본문이 너무 커요.");
            }
            if (size == 0) {
                headers(input);
                return body.toByteArray();
            }
            body.writeBytes(exact(input, (int) size));
            if (!line(input).isEmpty()) {
                throw new IOException("청크 응답이 잘못됐어요.");
            }
        }
        throw new IOException("청크 응답이 너무 복잡해요.");
    }

    private static byte[] exact(InputStream input, int length) throws IOException {
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("본문 수신이 중단됐어요.");
        }
        return value;
    }

    private static long number(String value, int radix) throws IOException {
        if (value.isEmpty() || !value.matches(radix == 16 ? "[0-9A-Fa-f]+" : "[0-9]+")) {
            throw new IOException("본문 길이가 잘못됐어요.");
        }
        try {
            return Long.parseLong(value, radix);
        } catch (NumberFormatException e) {
            throw new IOException("본문 길이가 너무 커요.");
        }
    }

    private static String line(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        while (bytes.size() <= 8192) {
            int value = input.read();
            if (value == -1) {
                throw new EOFException("HTTP 응답 수신이 중단됐어요.");
            }
            if (value == '\r') {
                if (input.read() != '\n') {
                    throw new IOException("응답 줄 구분이 잘못됐어요.");
                }
                return bytes.toString(StandardCharsets.ISO_8859_1);
            }
            if (value == '\n') {
                throw new IOException("응답 줄 구분이 잘못됐어요.");
            }
            bytes.write(value);
        }
        throw new IOException("응답 줄이 너무 길어요.");
    }

    record Response(int status, Map<String, String> headers, byte[] body) {
    }
}
