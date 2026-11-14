package com.reeltrip.infra.fetcher.web;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import org.springframework.stereotype.Component;

/** JDK HTML 파서로 정적 본문만 읽어요. 스크립트·외부 리소스는 실행하지 않아요. */
@Component
public class HtmlContentExtractor {

    static final int MAX_TEXT = 30_000;
    private static final Pattern CHARSET = Pattern.compile("(?i)charset\\s*=\\s*['\"]?([A-Za-z0-9._-]+)");
    private static final Set<String> OMIT = Set.of("head", "script", "style", "noscript", "template", "nav", "header",
            "footer", "aside", "form", "button", "select", "input", "svg", "canvas", "iframe", "frame");
    private static final Set<String> VOID = Set.of("area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr", "frame");

    Extracted extract(SafeWebClient.Page page, boolean naver) throws IOException {
        var builder = new TreeBuilder();
        try {
            // JDK 파서는 콜백의 RuntimeException을 삼킬 수 있어요. 한도를 넘기면 Reader도 중단해요.
            var reader = new StringReader(stripRawText(decode(page))) {
                @Override public int read(char[] buffer, int offset, int length) throws IOException {
                    checkLimit();
                    return super.read(buffer, offset, length);
                }
                @Override public int read() throws IOException {
                    checkLimit();
                    return super.read();
                }
                private void checkLimit() throws IOException {
                    if (builder.limitExceeded) {
                        throw new IOException("HTML 구조가 너무 복잡해요.");
                    }
                }
            };
            new ParserDelegator().parse(reader, builder, true);
        } catch (IllegalStateException e) {
            throw new IOException("HTML 구조가 너무 복잡해요.");
        }
        if (builder.limitExceeded) {
            throw new IOException("HTML 구조가 너무 복잡해요.");
        }
        List<Node> nodes = new ArrayList<>();
        flatten(builder.root, nodes);
        Node main = nodes.stream().filter(node -> rank(node, naver) < 10)
                .filter(node -> !text(node).isBlank())
                .min(Comparator.<Node>comparingInt(node -> rank(node, naver))
                        .thenComparing(Comparator.comparingInt((Node node) -> text(node).length()).reversed()))
                .orElse(null);
        String text = main == null ? "" : text(main);
        if (text.length() > MAX_TEXT) {
            int end = Character.isHighSurrogate(text.charAt(MAX_TEXT - 1)) ? MAX_TEXT - 1 : MAX_TEXT;
            text = text.substring(0, end).stripTrailing();
        }
        String thumbnail = nodes.stream().filter(node -> node.tag.equals("meta"))
                .filter(node -> "og:image".equalsIgnoreCase(node.attr("property")))
                .map(node -> node.attr("content")).filter(value -> !value.isBlank()).findFirst()
                .orElseGet(() -> nodes.stream().filter(node -> node.tag.equals("meta"))
                        .filter(node -> "twitter:image".equalsIgnoreCase(node.attr("name")))
                        .map(node -> node.attr("content")).filter(value -> !value.isBlank()).findFirst().orElse(null));
        String frame = nodes.stream().filter(node -> Set.of("iframe", "frame").contains(node.tag))
                .filter(node -> "mainFrame".equalsIgnoreCase(node.attr("id")))
                .map(node -> node.attr("src")).filter(value -> !value.isBlank()).findFirst().orElse(null);
        return new Extracted(text, thumbnail, frame, meta(nodes, "og:description", "twitter:description"), meta(nodes, "og:url"));
    }

    private static String meta(List<Node> nodes, String... names) {
        for (String name : names) {
            String value = nodes.stream().filter(node -> node.tag.equals("meta"))
                    .filter(node -> name.equalsIgnoreCase(node.attr("property")) || name.equalsIgnoreCase(node.attr("name")))
                    .map(node -> node.attr("content")).filter(content -> !content.isBlank()).findFirst().orElse(null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int rank(Node node, boolean naver) {
        if (node.omitted) {
            return 10;
        }
        String classes = " " + node.attr("class") + " ";
        if (classes.contains(" se-main-container ") || node.attr("id").equals("postViewArea")) {
            return 0;
        }
        if (naver) {
            return 10;
        }
        if (node.tag.equals("article")) {
            return 1;
        }
        if (node.tag.equals("main") || node.attr("role").equals("main")) {
            return 2;
        }
        if (Set.of("content", "article", "post-content", "entry-content").contains(node.attr("id"))
                || classes.contains(" entry-content ") || classes.contains(" post-content ")) {
            return 3;
        }
        return node.tag.equals("body") ? 4 : 10;
    }

    private static String text(Node node) {
        var result = new StringBuilder();
        append(node, result);
        return result.toString().strip();
    }

    private static void append(Node node, StringBuilder result) {
        if (node.omitted || result.length() > MAX_TEXT) {
            return;
        }
        for (Object child : node.children) {
            if (child instanceof Node element) {
                append(element, result);
            } else {
                String value = child.toString().replaceAll("[\\s\\u00a0]+", " ").strip();
                if (!value.isEmpty() && result.length() <= MAX_TEXT) {
                    result.append(' ').append(value, 0, Math.min(value.length(), MAX_TEXT + 1 - result.length()));
                }
            }
        }
    }

    private static void flatten(Node node, List<Node> output) {
        output.add(node);
        for (Object child : node.children) {
            if (child instanceof Node element) {
                flatten(element, output);
            }
        }
    }

    private static String decode(SafeWebClient.Page page) throws IOException {
        byte[] bytes = page.bytes();
        int offset = 0;
        String encoding = null;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            encoding = "UTF-8";
            offset = 3;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff) {
            encoding = "UTF-16BE";
            offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe) {
            encoding = "UTF-16LE";
            offset = 2;
        }
        var header = CHARSET.matcher(page.contentType());
        if (encoding == null && header.find()) {
            encoding = header.group(1);
        }
        if (encoding == null) {
            String prefix = new String(bytes, 0, Math.min(bytes.length, 8192), StandardCharsets.ISO_8859_1);
            var meta = Pattern.compile("(?is)<meta\\b[^>]{0,512}>").matcher(prefix);
            while (meta.find()) {
                var charset = CHARSET.matcher(meta.group());
                if (charset.find()) {
                    encoding = charset.group(1);
                    break;
                }
            }
        }
        try {
            Charset charset = Charset.forName(encoding == null ? "UTF-8" : encoding);
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (IllegalArgumentException e) {
            throw new IOException("지원하지 않는 문자 인코딩이에요.");
        }
    }

    // JDK 파서는 body 안의 style 태그를 버리고 CSS를 일반 텍스트로 알릴 수 있어요.
    // 따옴표·주석을 구분하는 선형 스캔으로 script/style 원문을 먼저 제외해요.
    private static String stripRawText(String html) {
        var lowerBuilder = new StringBuilder(html.length());
        for (int index = 0; index < html.length(); index++) {
            char value = html.charAt(index);
            lowerBuilder.append(value >= 'A' && value <= 'Z' ? (char) (value + ('a' - 'A')) : value);
        }
        String lower = lowerBuilder.toString();
        var output = new StringBuilder(html.length());
        int position = 0;
        while (position < html.length()) {
            if (html.startsWith("<!--", position)) {
                int end = html.indexOf("-->", position + 4);
                if (end < 0) {
                    break;
                }
                output.append(html, position, end + 3);
                position = end + 3;
                continue;
            }
            if (html.charAt(position) != '<') {
                output.append(html.charAt(position++));
                continue;
            }
            int nameEnd = position + 1;
            while (nameEnd < html.length() && Character.isLetterOrDigit(html.charAt(nameEnd))) {
                nameEnd++;
            }
            if (nameEnd == position + 1) {
                output.append(html.charAt(position++));
                continue;
            }
            String name = lower.substring(position + 1, nameEnd);
            int openEnd = tagEnd(html, nameEnd);
            if (Set.of("script", "style").contains(name)) {
                int close = openEnd;
                while ((close = lower.indexOf("</" + name, close)) >= 0) {
                    int boundary = close + name.length() + 2;
                    if (boundary == html.length() || Character.isWhitespace(html.charAt(boundary))
                            || html.charAt(boundary) == '>' || html.charAt(boundary) == '/') {
                        break;
                    }
                    close = boundary;
                }
                if (close < 0) {
                    break;
                }
                position = tagEnd(html, close);
                output.append(' ');
            } else {
                output.append(html, position, openEnd);
                position = openEnd;
            }
        }
        return output.toString();
    }

    private static int tagEnd(String html, int start) {
        char quote = 0;
        for (int index = start; index < html.length(); index++) {
            char current = html.charAt(index);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '>') {
                return index + 1;
            }
        }
        return html.length();
    }

    record Extracted(String text, String thumbnail, String mainFrame, String description, String canonical) {
    }

    private static final class Node {
        final String tag;
        final Map<String, String> attributes;
        final List<Object> children = new ArrayList<>();
        final boolean omitted;

        Node(String tag, Map<String, String> attributes, boolean inheritedOmission) {
            this.tag = tag;
            this.attributes = attributes;
            String style = attr("style").toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            String markers = (attr("class") + " " + attr("id")).toLowerCase(Locale.ROOT);
            this.omitted = inheritedOmission || OMIT.contains(tag) || attributes.containsKey("hidden")
                    || attr("aria-hidden").equalsIgnoreCase("true") || style.contains("display:none") || style.contains("visibility:hidden")
                    || markers.matches(".*(?:^|[ _-])(sidebar|comments?|advertisement|navigation|gnb|lnb|menu|breadcrumb|pagination|header|footer)(?:$|[ _-]).*");
        }

        String attr(String name) {
            return attributes.getOrDefault(name, "");
        }
    }

    private static final class TreeBuilder extends HTMLEditorKit.ParserCallback {
        final Node root = new Node("root", Map.of(), false);
        final Deque<Node> stack = new ArrayDeque<>();
        int nodes;
        boolean limitExceeded;

        TreeBuilder() {
            stack.push(root);
        }

        @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            add(tag, attributes, true);
        }

        @Override public void handleEndTag(HTML.Tag tag, int position) {
            if (limitExceeded) {
                return;
            }
            String name = tag.toString().toLowerCase(Locale.ROOT);
            if (stack.stream().anyMatch(node -> node.tag.equals(name))) {
                while (stack.size() > 1 && !stack.pop().tag.equals(name)) {
                    // 잘못 닫힌 정적 HTML은 일치하는 태그까지만 복구해요.
                }
            }
        }

        @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
            if (attributes.isDefined(HTML.Attribute.ENDTAG)) {
                handleEndTag(tag, position);
            } else {
                add(tag, attributes, tag instanceof HTML.UnknownTag && !VOID.contains(tag.toString()));
            }
        }

        private void add(HTML.Tag tag, MutableAttributeSet attributes, boolean open) {
            if (limitExceeded) {
                return;
            }
            if (++nodes > 20_000 || stack.size() > 128) {
                limitExceeded = true;
                return;
            }
            var values = new HashMap<String, String>();
            var keys = attributes.getAttributeNames();
            while (keys.hasMoreElements()) {
                Object key = keys.nextElement();
                values.put(key.toString().toLowerCase(Locale.ROOT), attributes.getAttribute(key).toString());
            }
            var node = new Node(tag.toString().toLowerCase(Locale.ROOT), values, stack.peek().omitted);
            stack.peek().children.add(node);
            if (open) {
                stack.push(node);
            }
        }

        @Override public void handleText(char[] value, int position) {
            if (!limitExceeded) {
                stack.peek().children.add(new String(value));
            }
        }
    }
}
