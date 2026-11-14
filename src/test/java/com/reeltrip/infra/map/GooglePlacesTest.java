package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

class GooglePlacesTest {
    @Test @DisplayName("공식 Text Search의 주소·좌표를 읽고 키는 헤더에만 넣는다.")
    @SuppressWarnings("unchecked")
    void parsesRealContractWithoutPhotoKeys() throws Exception {
        // given
        var http=mock(BoundedHttp.class); var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"places\":[{\"id\":\"id\",\"displayName\":{\"text\":\"가게\"},\"formattedAddress\":\"교토\",\"location\":{\"latitude\":35,\"longitude\":135}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(http.send(any(),anyInt(),any())).thenReturn(response);
        // when
        var places=new GooglePlacesClient(http,new ObjectMapper(),"test-key").search("가게 교토");
        // then
        assertThat(places).hasSize(1); assertThat(places.getFirst().latitude()).isEqualTo(35);
        var request=ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(),anyInt(),any());
        assertThat(request.getValue().uri().toString()).isEqualTo("https://places.googleapis.com/v1/places:searchText");
        assertThat(request.getValue().headers().firstValue("X-Goog-Api-Key")).contains("test-key");
        assertThat(request.getValue().headers().firstValue("X-Goog-FieldMask").orElseThrow()).doesNotContain("photos");
    }

    @Test @DisplayName("키가 없으면 외부 호출 전에 실패한다.")
    void missingKeyFailsClosed() {
        // given
        var http=mock(BoundedHttp.class);
        // when / then
        assertThatThrownBy(()->new GooglePlacesClient(http,new ObjectMapper(),"").search("교토")).isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(http);
    }
}
