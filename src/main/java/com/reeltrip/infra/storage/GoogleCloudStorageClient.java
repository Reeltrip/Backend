package com.reeltrip.infra.storage;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

@Component
public class GoogleCloudStorageClient implements StorageClient {
    private final BoundedHttp http;
    private final AccessTokenProvider tokens;
    private final ObjectMapper mapper;
    private final String bucket;

    public GoogleCloudStorageClient(BoundedHttp http, AccessTokenProvider tokens, ObjectMapper mapper,
            @Value("${GCS_BUCKET:}") String bucket) {
        this.http=http; this.tokens=tokens; this.mapper=mapper; this.bucket=bucket;
    }

    @Override public String store(String name, byte[] bytes, String type) throws IOException {
        if(!bucket.matches("[a-z0-9][a-z0-9._-]{1,61}[a-z0-9]")
                || !name.matches("[A-Za-z0-9/_-]+\\.(png|jpg)")) {
            throw new IOException("이미지 저장소 설정이 필요해요.");
        }
        URI endpoint=URI.create("https://storage.googleapis.com/upload/storage/v1/b/"+bucket+"/o?uploadType=media&ifGenerationMatch=0&name="
                +URLEncoder.encode(name, StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer "+tokens.token()).header("Content-Type",type)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var response=http.send(request, 64*1024, Duration.ofSeconds(10));
        if(response.statusCode()!=200) { throw new IOException("이미지를 저장하지 못했어요."); }
        var body=mapper.readTree(response.body());
        if(!body.path("name").asText().equals(name) || !body.path("bucket").asText().equals(bucket)) {
            throw new IOException("저장 결과를 확인하지 못했어요.");
        }
        return "https://storage.googleapis.com/"+bucket+"/"+name;
    }
}
