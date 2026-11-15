package com.reeltrip.domain.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageValidator {
    public static final int MAX_BYTES=5*1024*1024;

    public String validate(byte[] bytes, String type) throws IOException {
        if(bytes.length==0 || bytes.length>MAX_BYTES) { throw new IOException("이미지 크기가 잘못됐어요."); }
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) { throw new IOException("이미지 형식이 잘못됐어요."); }
            var reader=readers.next();
            try {
                String format=reader.getFormatName();
                String mime=format.equalsIgnoreCase("PNG")?"image/png":format.equalsIgnoreCase("JPEG")?"image/jpeg":"";
                if(mime.isEmpty() || !mime.equals(type)) { throw new IOException("PNG와 JPEG만 업로드할 수 있어요."); }
                reader.setInput(input, true, true);
                int width=reader.getWidth(0), height=reader.getHeight(0);
                if(width<=0 || height<=0 || width>8192 || height>8192 || (long)width*height>16_000_000) {
                    throw new IOException("이미지 해상도가 너무 커요.");
                }
                var warning=new AtomicBoolean();
                reader.addIIOReadWarningListener((source,message)->warning.set(true));
                var params=reader.getDefaultReadParam();
                params.setSourceSubsampling(Math.max(1,(width+511)/512),Math.max(1,(height+511)/512),0,0);
                if(reader.read(0,params)==null || warning.get()) { throw new IOException("이미지를 읽지 못했어요."); }
                return mime.equals("image/png")?"png":"jpg";
            } finally { reader.dispose(); }
        }
    }
}
