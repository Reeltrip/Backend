package com.reeltrip.infra.mail;

import java.time.Duration;
import java.util.regex.Pattern;

/** S0-1b 회원가입 인증번호 메일. HTML을 막아 둔 메일 앱을 위해 글자 본문도 같이 만들어요. */
public record SignupCodeMail(String subject, String text, String html) {

    private static final Pattern CODE_FORMAT = Pattern.compile("[0-9]{6}");
    private static final String SUBJECT = "[Wayvi] 회원가입 인증번호";

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
        String body = """
                        <tr>
                          <td style="padding:12px 32px 0 32px;">
                            <p style="margin:0;font-size:15px;color:%4$s;line-height:1.6;">아래 6자리 숫자를 가입 화면에 입력해 주세요.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:24px 32px 0 32px;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0">
                              <tr>
                                <td align="center" style="padding:20px 0;background:#EEF3FF;border-radius:12px;">
                                  <span style="font-family:'SFMono-Regular',Consolas,'Courier New',monospace;font-size:36px;font-weight:700;letter-spacing:10px;color:%3$s;">%1$s</span>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 0 32px;">
                            <p style="margin:0;font-size:14px;color:%4$s;line-height:1.6;">이 번호는 <strong style="color:%5$s;">%2$d분 동안</strong> 쓸 수 있어요.</p>
                          </td>
                        </tr>
                """.formatted(code, minutes, MailLayout.BRAND, MailLayout.SUB_TEXT, MailLayout.TEXT);
        return MailLayout.page(
                "Wayvi 회원가입 인증번호",
                "인증번호 %s · %d분 안에 입력해 주세요.".formatted(code, minutes),
                "회원가입 인증번호를 알려 드려요",
                body,
                "요청하지 않았다면 이 메일은 무시해 주세요. 누군가 이메일 주소를 잘못 입력했을 수 있어요.");
    }
}
