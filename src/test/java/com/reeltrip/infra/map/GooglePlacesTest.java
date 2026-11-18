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

    @Test @DisplayName("가격 범위는 현지 통화 문구로, 범위가 없으면 가격 수준 문구로 읽는다. 둘 다 없으면 비운다.")
    void readsPriceRangeThenLevel() throws Exception {
        // given: 범위+수준 / 시작가만 / 수준만 / 둘 다 없음 / 기호를 모르는 통화
        String body="{\"places\":["
                +place("a",",\"priceLevel\":\"PRICE_LEVEL_MODERATE\",\"priceRange\":{\"startPrice\":{\"currencyCode\":\"JPY\",\"units\":\"1000\"},\"endPrice\":{\"currencyCode\":\"JPY\",\"units\":\"2000\"}}")+","
                +place("b",",\"priceRange\":{\"startPrice\":{\"currencyCode\":\"JPY\",\"units\":\"10000\"}}")+","
                +place("c",",\"priceLevel\":\"PRICE_LEVEL_INEXPENSIVE\"")+","
                +place("d","")+","
                +place("e",",\"priceRange\":{\"startPrice\":{\"currencyCode\":\"THB\",\"units\":\"100\"},\"endPrice\":{\"currencyCode\":\"THB\",\"units\":\"200\"}}")+"]}";
        // when
        var places=client(body).search("가게 교토");
        // then
        assertThat(places).extracting(MapsPlace::priceText)
                .containsExactly("¥1,000~2,000","¥10,000~","가격대 낮음",null,"THB 100~200");
    }

    @Test @DisplayName("가격 값이 숫자가 아니거나 통화가 섞여 있으면 지어내지 않고 비운다.")
    void brokenPriceIsDropped() throws Exception {
        // given
        String body="{\"places\":["
                +place("a",",\"priceRange\":{\"startPrice\":{\"currencyCode\":\"JPY\",\"units\":\"abc\"}}")+","
                +place("b",",\"priceRange\":{\"startPrice\":{\"currencyCode\":\"JPY\",\"units\":\"1000\"},\"endPrice\":{\"currencyCode\":\"USD\",\"units\":\"20\"}}")+","
                +place("c",",\"priceLevel\":\"PRICE_LEVEL_UNSPECIFIED\"")+"]}";
        // when / then
        assertThat(client(body).search("가게 교토")).extracting(MapsPlace::priceText).containsExactly(null,null,null);
    }

    @Test @DisplayName("가격 항목을 요청한다. 리뷰 수·영업시간과 같은 요금 구간이라 단가는 그대로다.")
    @SuppressWarnings("unchecked")
    void asksForPriceFields() throws Exception {
        // given
        var http=mock(BoundedHttp.class); var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(http.send(any(),anyInt(),any())).thenReturn(response);
        // when
        new GooglePlacesClient(http,new ObjectMapper(),"test-key").search("가게 교토");
        // then
        var request=ArgumentCaptor.forClass(HttpRequest.class); verify(http).send(request.capture(),anyInt(),any());
        assertThat(request.getValue().headers().firstValue("X-Goog-FieldMask").orElseThrow())
                .contains("places.priceRange","places.priceLevel");
    }

    private static String place(String id,String extra) {
        return "{\"id\":\""+id+"\",\"displayName\":{\"text\":\"가게\"},\"location\":{\"latitude\":35,\"longitude\":135}"+extra+"}";
    }

    @SuppressWarnings("unchecked")
    private static GooglePlacesClient client(String body) throws Exception {
        var http=mock(BoundedHttp.class); var response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(http.send(any(),anyInt(),any())).thenReturn(response);
        return new GooglePlacesClient(http,new ObjectMapper(),"test-key");
    }
}
