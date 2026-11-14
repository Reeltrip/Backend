package com.reeltrip.domain.image;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.security.JwtProvider;
import com.reeltrip.infra.storage.StorageClient;
import com.reeltrip.support.TestFixtures;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class ImageApiTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @MockitoBean StorageClient storage;

    @Test @DisplayName("정상 PNG를 회원별 UUID 이름으로 저장하고 201과 키 없는 주소를 반환한다.")
    void uploadsValidatedImage() throws Exception {
        // given
        var user=users.save(TestFixtures.user("image@example.test"));
        when(storage.store(anyString(),any(),eq("image/png"))).thenReturn("https://storage.googleapis.com/test/photo.png");
        var image=new MockMultipartFile("file","../../same.png","image/png",png());
        // when / then
        mvc.perform(multipart("/images").file(image).header("Authorization","Bearer "+jwt.createAccessToken(user.getUserId())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.imageUrl").value("https://storage.googleapis.com/test/photo.png"));
        verify(storage).store(matches("users/"+user.getUserId()+"/[a-f0-9-]+\\.png"),any(),eq("image/png"));
    }

    @Test @DisplayName("위장 형식·빈 파일·초과 크기와 미인증은 저장 전에 거부한다.")
    void rejectsInvalidFilesAndUnauthenticated() throws Exception {
        // given
        var user=users.save(TestFixtures.user("image-invalid@example.test"));
        String token="Bearer "+jwt.createAccessToken(user.getUserId());
        for(var file:new MockMultipartFile[]{new MockMultipartFile("file","x.png","image/png",new byte[0]),
                new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes()),
                new MockMultipartFile("file","x.jpg","image/jpeg",png()),
                new MockMultipartFile("file","x.png","image/png",new byte[ImageValidator.MAX_BYTES+1])}) {
            // when / then
            mvc.perform(multipart("/images").file(file).header("Authorization",token)).andExpect(status().isBadRequest());
        }
        mvc.perform(multipart("/images").file(new MockMultipartFile("file","x.png","image/png",png())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }

    @Test @DisplayName("저장소 실패는 비밀 값 없이 공통 업로드 오류로 반환한다.")
    void mapsStorageFailure() throws Exception {
        // given
        var user=users.save(TestFixtures.user("image-fail@example.test"));
        when(storage.store(anyString(),any(),anyString())).thenThrow(new java.io.IOException("test-only-sensitive-message"));
        // when / then
        mvc.perform(multipart("/images").file(new MockMultipartFile("file","x.png","image/png",png()))
                .header("Authorization","Bearer "+jwt.createAccessToken(user.getUserId())))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("IMAGE_UPLOAD_FAILED"));
    }

    private static byte[] png() throws Exception {
        var output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",output);
        return output.toByteArray();
    }
}
