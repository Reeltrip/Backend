package com.reeltrip.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.reeltrip.domain.image.ImageValidator;
import com.reeltrip.infra.fetcher.web.RemoteImageClient;
import com.reeltrip.infra.storage.StorageClient;

class PlaceImageArchiverTest {
    private static final String INSTAGRAM = "https://scontent-ssn1-1.cdninstagram.com/v/t51/1_n.jpg?oe=6921ABCD";
    private final RemoteImageClient remote = mock(RemoteImageClient.class);
    private final StorageClient storage = mock(StorageClient.class);
    private final PlaceImageArchiver archiver = new PlaceImageArchiver(remote, new ImageValidator(), storage, true);

    @Test
    @DisplayName("만료되는 외부 사진은 우리 저장소로 옮기고 저장소 주소를 돌려준다.")
    void archivesExpiringImage() throws Exception {
        // given
        byte[] jpeg = jpeg();
        when(remote.download(eq(INSTAGRAM), any())).thenReturn(new RemoteImageClient.Downloaded(jpeg, "image/jpeg"));
        when(storage.store(anyString(), any(), anyString())).thenReturn("https://storage.googleapis.com/bucket/places/archive/x.jpg");
        // when
        String result = archiver.archive(INSTAGRAM);
        // then
        assertThat(result).isEqualTo("https://storage.googleapis.com/bucket/places/archive/x.jpg");
        var name = ArgumentCaptor.forClass(String.class);
        verify(storage).store(name.capture(), eq(jpeg), eq("image/jpeg"));
        assertThat(name.getValue()).matches("places/archive/[0-9a-f-]{36}\\.jpg");
    }

    @Test
    @DisplayName("이미 우리 저장소에 있거나 만료가 없는 유튜브 사진, 빈 값은 그대로 두고 내려받지 않는다.")
    void leavesStableImages() {
        // when / then
        assertThat(archiver.archive("https://storage.googleapis.com/bucket/users/1/a.jpg")).isEqualTo("https://storage.googleapis.com/bucket/users/1/a.jpg");
        assertThat(archiver.archive("https://i.ytimg.com/vi/abc/hqdefault.jpg")).isEqualTo("https://i.ytimg.com/vi/abc/hqdefault.jpg");
        assertThat(archiver.archive(null)).isNull();
        assertThat(archiver.archive(" ")).isEqualTo(" ");
        verifyNoInteractions(remote, storage);
    }

    @Test
    @DisplayName("내려받기·형식 검사·저장 중 하나라도 실패하면 원래 주소를 그대로 돌려줘서 카드 저장을 막지 않는다.")
    void fallsBackToOriginalOnFailure() throws Exception {
        // given: 내려받기 실패
        when(remote.download(eq(INSTAGRAM), any())).thenThrow(new IOException("gone"));
        // when / then
        assertThat(archiver.archive(INSTAGRAM)).isEqualTo(INSTAGRAM);

        // given: 사진이 아닌 바이트
        String broken = "https://cdn.example/broken.jpg";
        when(remote.download(eq(broken), any())).thenReturn(new RemoteImageClient.Downloaded(new byte[]{1, 2, 3}, "image/jpeg"));
        // when / then
        assertThat(archiver.archive(broken)).isEqualTo(broken);
        verifyNoInteractions(storage);

        // given: 저장 실패
        String unsaved = "https://cdn.example/unsaved.jpg";
        when(remote.download(eq(unsaved), any())).thenReturn(new RemoteImageClient.Downloaded(jpeg(), "image/jpeg"));
        when(storage.store(anyString(), any(), anyString())).thenThrow(new IOException("no bucket"));
        // when / then
        assertThat(archiver.archive(unsaved)).isEqualTo(unsaved);
    }

    @Test
    @DisplayName("기능을 끄면 아무것도 내려받지 않고 원래 주소를 돌려준다.")
    void disabledDoesNothing() {
        // given
        var disabled = new PlaceImageArchiver(remote, new ImageValidator(), storage, false);
        // when / then
        assertThat(disabled.archive(INSTAGRAM)).isEqualTo(INSTAGRAM);
        verifyNoInteractions(remote, storage);
    }

    private static byte[] jpeg() throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }
}
