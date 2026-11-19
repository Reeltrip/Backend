package com.reeltrip.infra.map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reeltrip.infra.http.BoundedHttp;

class GooglePlacePhotoTest {
    private static final String NAME="places/ChIJabc-_1/photos/AbC-_9";

    @Test @DisplayName("장소의 첫 사진 주소와 출처를 읽고, 키는 헤더에만 넣고 사진 항목만 요청한다.")
    void readsFirstPhotoAndAttribution() throws Exception {
        // given
        var http=mock(BoundedHttp.class);
        var details=response(200,"{\"photos\":[{\"name\":\""+NAME+"\",\"authorAttributions\":[{\"displayName\":\"홍길동\",\"uri\":\"https://maps.google.com/maps/contrib/1\"}]},{\"name\":\"places/ChIJabc-_1/photos/second\"}]}");
        var media=response(200,"{\"name\":\""+NAME+"/media\",\"photoUri\":\"https://lh3.googleusercontent.com/p/x=w400\"}");
        when(http.send(any(),anyInt(),any())).thenReturn(details,media);
        // when
        var photo=new GooglePlacePhotoClient(http,new ObjectMapper(),"test-key").findPhoto("ChIJabc-_1");
        // then
        assertThat(photo.photoUrl()).isEqualTo("https://lh3.googleusercontent.com/p/x=w400");
        assertThat(photo.attributions()).containsExactly(new MapsPlace.Attribution("홍길동","https://maps.google.com/maps/contrib/1"));
        var request=ArgumentCaptor.forClass(HttpRequest.class); verify(http,times(2)).send(request.capture(),anyInt(),any());
        var first=request.getAllValues().get(0); var second=request.getAllValues().get(1);
        assertThat(first.uri().toString()).isEqualTo("https://places.googleapis.com/v1/places/ChIJabc-_1");
        assertThat(first.headers().firstValue("X-Goog-FieldMask")).contains("photos");
        assertThat(second.uri().toString()).isEqualTo("https://places.googleapis.com/v1/"+NAME+"/media?maxWidthPx=400&skipHttpRedirect=true");
        assertThat(request.getAllValues()).allSatisfy(r->{
            assertThat(r.headers().firstValue("X-Goog-Api-Key")).contains("test-key");
            assertThat(r.uri().toString()).doesNotContain("test-key");
        });
    }

    @Test @DisplayName("사진이 없는 장소는 빈 결과이고 사진 요금이 드는 두 번째 호출을 하지 않는다.")
    void placeWithoutPhotoSkipsMediaCall() throws Exception {
        // given
        var http=mock(BoundedHttp.class);
        var empty=response(200,"{}");
        when(http.send(any(),anyInt(),any())).thenReturn(empty);
        // when
        var photo=new GooglePlacePhotoClient(http,new ObjectMapper(),"test-key").findPhoto("ChIJabc-_1");
        // then
        assertThat(photo.photoUrl()).isNull(); assertThat(photo.attributions()).isEmpty();
        verify(http,times(1)).send(any(),anyInt(),any());
    }

    @Test @DisplayName("키가 없거나 장소 ID 형식이 이상하면 외부 호출 전에 실패한다.")
    void failsClosedBeforeCalling() {
        // given
        var http=mock(BoundedHttp.class);
        // when / then
        assertThatThrownBy(()->new GooglePlacePhotoClient(http,new ObjectMapper(),"").findPhoto("ChIJabc")).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->new GooglePlacePhotoClient(http,new ObjectMapper(),"k").findPhoto("a/../b")).isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(()->new GooglePlacePhotoClient(http,new ObjectMapper(),"k").findPhoto("a?key=1")).isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(http);
    }

    @Test @DisplayName("Google이 준 사진 이름이나 주소가 예상한 모양이 아니면 따라가지 않고 빈 결과로 둔다.")
    void ignoresUnexpectedPhotoNameAndUri() throws Exception {
        // given: 다른 주소로 새는 사진 이름
        var http=mock(BoundedHttp.class);
        var leaking=response(200,"{\"photos\":[{\"name\":\"places/x/photos/y/../../../v1/other\"}]}");
        when(http.send(any(),anyInt(),any())).thenReturn(leaking);
        // when / then
        assertThat(new GooglePlacePhotoClient(http,new ObjectMapper(),"k").findPhoto("ChIJabc").photoUrl()).isNull();
        verify(http,times(1)).send(any(),anyInt(),any());

        // given: https가 아닌 사진 주소
        var http2=mock(BoundedHttp.class);
        var details=response(200,"{\"photos\":[{\"name\":\""+NAME+"\"}]}");
        var media=response(200,"{\"photoUri\":\"http://example.test/a.jpg\"}");
        when(http2.send(any(),anyInt(),any())).thenReturn(details,media);
        // when / then
        assertThat(new GooglePlacePhotoClient(http2,new ObjectMapper(),"k").findPhoto("ChIJabc").photoUrl()).isNull();
    }

    @Test @DisplayName("Google 호출이 실패하면 오류로 알린다.")
    void providerFailureThrows() throws Exception {
        // given
        var http=mock(BoundedHttp.class);
        var denied=response(403,"{}");
        when(http.send(any(),anyInt(),any())).thenReturn(denied);
        // when / then
        assertThatThrownBy(()->new GooglePlacePhotoClient(http,new ObjectMapper(),"k").findPhoto("ChIJabc")).isInstanceOf(java.io.IOException.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> response(int status,String body) {
        var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
