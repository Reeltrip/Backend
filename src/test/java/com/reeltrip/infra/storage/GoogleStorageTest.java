package com.reeltrip.infra.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

class GoogleStorageTest {
    @Test @DisplayName("공식 업로드 주소에 인증 헤더를 보내고 저장 결과로 공개 주소를 만든다.")
    @SuppressWarnings("unchecked")
    void uploadsViaFixedGoogleOrigin() throws Exception {
        // given
        var http=mock(BoundedHttp.class); var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"name\":\"users/1/test.png\",\"bucket\":\"test-bucket\"}".getBytes(StandardCharsets.UTF_8));
        when(http.send(any(),anyInt(),any())).thenReturn(response);
        var storage=new GoogleCloudStorageClient(http,()->"test-token",new ObjectMapper(),"test-bucket");
        // when / then
        assertThat(storage.store("users/1/test.png",new byte[]{1},"image/png"))
                .isEqualTo("https://storage.googleapis.com/test-bucket/users/1/test.png");
        var request=ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(),anyInt(),any());
        assertThat(request.getValue().uri().getHost()).isEqualTo("storage.googleapis.com");
        assertThat(request.getValue().uri().toString()).contains("ifGenerationMatch=0").doesNotContain("test-token");
        assertThat(request.getValue().headers().firstValue("Authorization")).contains("Bearer test-token");
    }

    @Test @DisplayName("버킷 미설정은 인증·외부 호출 전에 실패하고 로컬 토큰은 메타데이터를 호출하지 않는다.")
    void failsClosedWithoutBucket() throws Exception {
        // given
        var http=mock(BoundedHttp.class); var token=mock(AccessTokenProvider.class);
        var storage=new GoogleCloudStorageClient(http,token,new ObjectMapper(),"");
        // when / then
        assertThatThrownBy(()->storage.store("users/1/test.png",new byte[]{1},"image/png")).isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(http,token);
        assertThat(new RuntimeAccessTokenProvider(http,new ObjectMapper(),Clock.systemUTC(),"test-token").token()).isEqualTo("test-token");
        verifyNoInteractions(http);
    }
}
