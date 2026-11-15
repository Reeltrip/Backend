package com.reeltrip.infra.mail;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * S0-1b 회원가입 인증번호 메일. HTML을 막아 둔 메일 앱을 위해 글자 본문도 같이 만들어요.
 * 메일 앱은 외부 CSS·스크립트를 지우므로 표 배치와 인라인 스타일만 써요.
 */
public record SignupCodeMail(String subject, String text, String html) {

    private static final Pattern CODE_FORMAT = Pattern.compile("[0-9]{6}");
    private static final String SUBJECT = "[Wayvi] 회원가입 인증번호";
    // 로고는 외부 주소 대신 메일 안에 넣어(cid) 이미지 서버 없이도 보이게 해요.
    public static final String LOGO_CONTENT_ID = "wayvi-logo";
    public static final String LOGO_PATH = "mail/wayvi-logo.png";

    public static SignupCodeMail of(String code, Duration validFor) {
        // HTML에 그대로 넣는 값이라 숫자 6자리만 받아요.
        if (code == null || !CODE_FORMAT.matcher(code).matches()) {
            throw new IllegalArgumentException("인증번호는 숫자 6자리여야 해요.");
        }
        long minutes = validFor.toMinutes();
        return new SignupCodeMail(SUBJECT, text(code, minutes), html(code, minutes));
    }

    private static String text(String code, long minutes) {
        return """
                Wayvi 회원가입 인증번호예요.

                %s

                %d분 안에 가입 화면에 입력해 주세요.
                요청하지 않았다면 이 메일은 무시해 주세요. 누군가 이메일 주소를 잘못 입력했을 수 있어요.

                Wayvi · 링크 하나로 시작하는 여행 계획
                """.formatted(code, minutes);
    }

    private static String html(String code, long minutes) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Wayvi 회원가입 인증번호</title>
                </head>
                <body style="margin:0;padding:0;background:#F3F5F9;">
                <div style="display:none;max-height:0;overflow:hidden;">인증번호 %1$s · %2$d분 안에 입력해 주세요.</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#F3F5F9;">
                  <tr>
                    <td align="center" style="padding:40px 16px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;background:#FFFFFF;border-radius:16px;font-family:'Apple SD Gothic Neo','Malgun Gothic','맑은 고딕',sans-serif;">
                        <tr>
                          <td align="center" style="padding:28px 32px;background:#2F6BFF;border-radius:16px 16px 0 0;">
                            <img src="cid:%3$s" width="120" height="31" alt="WAYVI" style="display:block;border:0;width:120px;height:31px;color:#FFFFFF;font-size:22px;font-weight:800;">
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px 32px 0 32px;">
                            <p style="margin:0;font-size:20px;font-weight:700;color:#191F28;line-height:1.4;">회원가입 인증번호를 알려 드려요</p>
                            <p style="margin:12px 0 0 0;font-size:15px;color:#4E5968;line-height:1.6;">아래 6자리 숫자를 가입 화면에 입력해 주세요.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:24px 32px 0 32px;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                              <tr>
                                <td align="center" style="padding:20px 0;background:#EEF3FF;border-radius:12px;">
                                  <span style="font-family:'SFMono-Regular',Consolas,'Courier New',monospace;font-size:36px;font-weight:700;letter-spacing:10px;color:#2F6BFF;">%1$s</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 0 32px;">
                            <p style="margin:0;font-size:14px;color:#4E5968;line-height:1.6;">이 번호는 <strong style="color:#191F28;">%2$d분 동안</strong> 쓸 수 있어요.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:24px 32px 32px 32px;">
                            <p style="margin:0;padding-top:20px;border-top:1px solid #E5E8EB;font-size:13px;color:#8B95A1;line-height:1.6;">요청하지 않았다면 이 메일은 무시해 주세요. 누군가 이메일 주소를 잘못 입력했을 수 있어요.</p>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:16px 0 0 0;font-size:12px;color:#8B95A1;font-family:'Apple SD Gothic Neo','Malgun Gothic','맑은 고딕',sans-serif;">Wayvi · 링크 하나로 시작하는 여행 계획</p>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(code, minutes, LOGO_CONTENT_ID);
    }
}
