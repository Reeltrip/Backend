package com.reeltrip.infra.mail;

/**
 * Wayvi 메일 공통 틀(파란 로고 머리 · 흰 카드 · 아래 안내). 가입 인증번호와 비밀번호 재설정이 같이 써요.
 * 메일 앱은 외부 CSS·스크립트를 지우므로 표 배치와 인라인 스타일만 써요.
 */
final class MailLayout {

    // 로고는 외부 주소 대신 메일 안에 넣어(cid) 이미지 서버 없이도 보이게 해요.
    static final String LOGO_CONTENT_ID = "wayvi-logo";
    static final String LOGO_PATH = "mail/wayvi-logo.png";

    static final String BRAND = "#2F6BFF";
    static final String TEXT = "#191F28";
    static final String SUB_TEXT = "#4E5968";
    static final String MUTED = "#8B95A1";
    static final String FONT = "'Apple SD Gothic Neo','Malgun Gothic','맑은 고딕',sans-serif";

    private MailLayout() {
    }

    /**
     * @param title    브라우저 탭·접근성용 제목
     * @param preheader 받은편지함 목록에 제목 옆으로 보이는 한 줄
     * @param heading  카드 안 큰 제목
     * @param body     제목 아래에 들어갈 표 행(&lt;tr&gt;…&lt;/tr&gt;)들
     * @param notice   카드 맨 아래 회색 안내 문구
     */
    static String page(String title, String preheader, String heading, String body, String notice) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$s</title>
                </head>
                <body style="margin:0;padding:0;background:#F3F5F9;">
                <div style="display:none;max-height:0;overflow:hidden;">%2$s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#F3F5F9;">
                  <tr>
                    <td align="center" style="padding:40px 16px;">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;background:#FFFFFF;border-radius:16px;font-family:%6$s;">
                        <tr>
                          <td align="center" style="padding:28px 32px;background:%7$s;border-radius:16px 16px 0 0;">
                            <img src="cid:%8$s" width="120" height="31" alt="WAYVI" style="display:block;border:0;width:120px;height:31px;color:#FFFFFF;font-size:22px;font-weight:800;">
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:32px 32px 0 32px;">
                            <p style="margin:0;font-size:20px;font-weight:700;color:%9$s;line-height:1.4;">%3$s</p>
                          </td>
                        </tr>
                %4$s
                        <tr>
                          <td style="padding:24px 32px 32px 32px;">
                            <p style="margin:0;padding-top:20px;border-top:1px solid #E5E8EB;font-size:13px;color:%10$s;line-height:1.6;">%5$s</p>
                          </td>
                        </tr>
                      </table>
                      <p style="margin:16px 0 0 0;font-size:12px;color:%10$s;font-family:%6$s;">Wayvi · 링크 하나로 시작하는 여행 계획</p>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(title, preheader, heading, body, notice, FONT, BRAND, LOGO_CONTENT_ID, TEXT, MUTED);
    }

    /** 링크처럼 밖에서 받은 값을 HTML 속성·본문에 넣을 때 써요. */
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
