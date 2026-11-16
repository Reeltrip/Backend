package com.reeltrip.infra.mail;

import java.time.Duration;

/**
 * S0-1a 비밀번호 재설정 메일. 가입 메일과 같은 틀에 "새 비밀번호 정하기" 버튼을 둬요.
 * 버튼을 못 누르는 메일 앱을 위해 링크 주소도 글자로 같이 보여 줘요.
 */
public record PasswordResetMail(String subject, String text, String html) {

    private static final String SUBJECT = "[Wayvi] 비밀번호 재설정";

    /** @param url 검증을 마친 FE 재설정 화면 주소(토큰 포함) */
    public static PasswordResetMail of(String url, Duration validFor) {
        long minutes = validFor.toMinutes();
        return new PasswordResetMail(SUBJECT, text(url, minutes), html(MailLayout.escape(url), minutes));
    }

    private static String text(String url, long minutes) {
        return """
                Wayvi 비밀번호 재설정 링크예요.

                %s

                %d분 안에 새 비밀번호를 정해 주세요. 한 번만 사용할 수 있어요.
                요청하지 않았다면 이 메일은 무시해 주세요. 지금 비밀번호는 그대로예요.

                Wayvi · 링크 하나로 시작하는 여행 계획
                """.formatted(url, minutes);
    }

    private static String html(String url, long minutes) {
        String body = """
                        <tr>
                          <td style="padding:12px 32px 0 32px;">
                            <p style="margin:0;font-size:15px;color:%4$s;line-height:1.6;">아래 버튼을 눌러 새 비밀번호를 정해 주세요.</p>
                          </td>
                        </tr>
                        <tr>
                          <td align="center" style="padding:24px 32px 0 32px;">
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                              <tr>
                                <td align="center" style="background:%3$s;border-radius:12px;">
                                  <a href="%1$s" target="_blank" style="display:inline-block;padding:16px 40px;font-size:16px;font-weight:700;color:#FFFFFF;text-decoration:none;">새 비밀번호 정하기</a>
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 0 32px;">
                            <p style="margin:0;font-size:14px;color:%4$s;line-height:1.6;">이 링크는 <strong style="color:%5$s;">%2$d분 동안, 한 번만</strong> 쓸 수 있어요.</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px 0 32px;">
                            <p style="margin:0;font-size:12px;color:%6$s;line-height:1.6;">버튼이 눌리지 않으면 아래 주소를 브라우저에 붙여 넣어 주세요.<br><a href="%1$s" target="_blank" style="color:%3$s;word-break:break-all;">%1$s</a></p>
                          </td>
                        </tr>
                """.formatted(url, minutes, MailLayout.BRAND, MailLayout.SUB_TEXT, MailLayout.TEXT, MailLayout.MUTED);
        return MailLayout.page(
                "Wayvi 비밀번호 재설정",
                "%d분 안에 새 비밀번호를 정해 주세요.".formatted(minutes),
                "비밀번호를 다시 정할 수 있어요",
                body,
                "요청하지 않았다면 이 메일은 무시해 주세요. 지금 비밀번호는 그대로예요.");
    }
}
