package com.reeltrip.domain.image;

import java.io.IOException;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.reeltrip.domain.user.repository.UserRepository;
import com.reeltrip.global.exception.BusinessException;
import com.reeltrip.global.exception.ErrorCode;
import com.reeltrip.global.security.LoginUser;
import com.reeltrip.infra.storage.StorageClient;

@RestController
public class ImageController {
    private final ImageValidator validator;
    private final StorageClient storage;
    private final UserRepository users;
    public ImageController(ImageValidator validator, StorageClient storage, UserRepository users) {
        this.validator=validator; this.storage=storage; this.users=users;
    }

    @PostMapping(value="/images", consumes="multipart/form-data")
    public ResponseEntity<ImageResponse> upload(@LoginUser Long userId, @RequestPart("file") MultipartFile file) {
        if(!users.existsById(userId)) { throw new BusinessException(ErrorCode.UNAUTHORIZED); }
        byte[] bytes;
        String extension;
        try {
            if(file.getSize()>ImageValidator.MAX_BYTES) { throw new IOException(); }
            bytes=file.getBytes(); extension=validator.validate(bytes,file.getContentType());
        } catch(IOException | RuntimeException e) { throw new BusinessException(ErrorCode.INVALID_INPUT); }
        try {
            String imageUrl=storage.store("users/"+userId+"/"+UUID.randomUUID()+"."+extension,bytes,file.getContentType());
            return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(new ImageResponse(imageUrl));
        } catch(IOException | RuntimeException e) { throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED); }
    }

    public record ImageResponse(String imageUrl) { }
}
