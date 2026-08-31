package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.robot.dto.FilePreviewDTO;
import com.mola.molachat.robot.dto.FilePreviewRequest;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.team.solution.TeamCommandException;
import com.mola.molachat.team.solution.TeamGatewaySolution;
import com.mola.molachat.team.solution.TeamRobotProjectionSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FilePreviewSolution {

    static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private TeamGatewaySolution teamGatewaySolution;

    public FilePreviewDTO preview(FilePreviewRequest request) {
        validateRequest(request);
        Session session = requireSessionMember(request.getSessionId(), request.getChatterId());
        if (isRemote(request.getTarget())) {
            return previewRemote(request);
        }
        return previewLocal(request, session);
    }

    private FilePreviewDTO previewLocal(FilePreviewRequest request, Session session) {
        RobotChatter robot = session.getChatterSet().stream()
                .filter(RobotChatter.class::isInstance)
                .map(RobotChatter.class::cast)
                .filter(candidate -> "acp".equals(candidate.getRobotGroup())
                        || TeamRobotProjectionSolution.TEAM_ACP_GROUP.equals(
                        candidate.getRobotGroup()))
                .findFirst()
                .orElseThrow(() -> failure("NOT_ACP_SESSION",
                        HttpServletResponse.SC_BAD_REQUEST, "当前会话不是ACP会话"));

        JSONObject data;
        String path = normalizeLocalTarget(request.getTarget());
        if (TeamRobotProjectionSolution.TEAM_ACP_GROUP.equals(robot.getRobotGroup())) {
            try {
                data = teamGatewaySolution.readTextFile(request.getChatterId(),
                        robot.getTeamId(), robot.getTeamMemberId(), robot.getId(), path,
                        MAX_BYTES);
            } catch (TeamCommandException e) {
                throw mapCommandFailure(e.getCode(), e.getMessage(), e);
            }
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("schemaVersion", 1);
            payload.put("requestId", java.util.UUID.randomUUID().toString());
            payload.put("groupId", request.getSessionId());
            payload.put("path", path);
            payload.put("maxBytes", MAX_BYTES);
            data = invokeMain(request.getSessionId(), payload);
        }
        return toDto(data, "LOCAL", request.getRequestedLine(), null);
    }

    private JSONObject invokeMain(String groupId, Map<String, Object> payload) {
        try {
            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE.send(
                    "readTextFile", groupId,
                    new String[]{JSON.toJSONString(payload)});
            Map<String, String> result = response == null || response.getData() == null
                    ? null : response.getData().getResultMap();
            if (result == null || result.isEmpty()) {
                throw failure("ACP_NOT_CONNECTED",
                        HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "当前cmd-proxy不可用");
            }
            if (!Boolean.parseBoolean(result.get("accepted"))) {
                throw mapCommandFailure(result.get("code"), result.get("message"), null);
            }
            if (StringUtils.isBlank(result.get("data"))) {
                throw failure("IO_ERROR", HttpServletResponse.SC_BAD_GATEWAY,
                        "cmd-proxy未返回文件内容");
            }
            return JSON.parseObject(result.get("data"));
        } catch (FilePreviewException e) {
            throw e;
        } catch (Exception e) {
            log.warn("本地文件预览调用失败, groupId={}", groupId, e);
            throw failure("ACP_NOT_CONNECTED",
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "当前cmd-proxy不可用", e);
        }
    }

    private FilePreviewDTO previewRemote(FilePreviewRequest request) {
        URI current = parseRemoteUri(request.getTarget());
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(3000)
                .setConnectionRequestTimeout(3000)
                .setSocketTimeout(8000)
                .setRedirectsEnabled(false)
                .build();
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .disableAutomaticRetries()
                .build()) {
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                validateRemoteDestination(current);
                HttpGet get = new HttpGet(current);
                get.setHeader("Accept", "text/*, application/json, application/xml, "
                        + "application/javascript, application/xhtml+xml;q=0.9, */*;q=0.2");
                try (CloseableHttpResponse response = client.execute(get)) {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 300 && status < 400) {
                        Header location = response.getFirstHeader("Location");
                        if (location == null || redirect == MAX_REDIRECTS) {
                            throw failure("REMOTE_FETCH_FAILED",
                                    HttpServletResponse.SC_BAD_GATEWAY,
                                    "远程链接重定向失败");
                        }
                        current = current.resolve(location.getValue());
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw failure("REMOTE_FETCH_FAILED",
                                HttpServletResponse.SC_BAD_GATEWAY,
                                "远程文件响应状态异常");
                    }
                    HttpEntity entity = response.getEntity();
                    if (entity == null) {
                        throw failure("REMOTE_FETCH_FAILED",
                                HttpServletResponse.SC_BAD_GATEWAY,
                                "远程文件内容为空");
                    }
                    if (entity.getContentLength() > MAX_BYTES) {
                        throw failure("REMOTE_TOO_LARGE",
                                HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                                "远程文件过大");
                    }
                    byte[] bytes = readLimited(entity.getContent(), MAX_BYTES);
                    String mediaType = mediaType(entity);
                    DecodedText decoded = decodeText(bytes, charset(entity), mediaType);
                    JSONObject data = new JSONObject();
                    data.put("fileName", displayName(current));
                    data.put("content", decoded.content);
                    data.put("charset", decoded.charset);
                    data.put("mediaType", mediaType);
                    data.put("bytesRead", bytes.length);
                    data.put("truncated", false);
                    return toDto(data, "REMOTE", request.getRequestedLine(), current.toString());
                }
            }
        } catch (FilePreviewException e) {
            throw e;
        } catch (Exception e) {
            log.warn("远程文件预览失败, host={}", current.getHost(), e);
            throw failure("REMOTE_FETCH_FAILED", HttpServletResponse.SC_BAD_GATEWAY,
                    "远程文件读取失败", e);
        }
        throw failure("REMOTE_FETCH_FAILED", HttpServletResponse.SC_BAD_GATEWAY,
                "远程文件读取失败");
    }

    private Session requireSessionMember(String sessionId, String chatterId) {
        Session session = sessionFactory.selectById(sessionId);
        if (session == null || session.getChatterSet() == null) {
            throw failure("SESSION_NOT_FOUND", HttpServletResponse.SC_NOT_FOUND,
                    "会话不存在");
        }
        boolean member = session.getChatterSet().stream()
                .map(Chatter::getId).anyMatch(chatterId::equals);
        if (!member) {
            throw failure("UNAUTHORIZED", HttpServletResponse.SC_FORBIDDEN,
                    "无权访问该会话");
        }
        return session;
    }

    private FilePreviewDTO toDto(JSONObject data, String source,
                                 Integer requestedLine, String baseUrl) {
        String content = data.getString("content");
        if (content == null) {
            throw failure("IO_ERROR", HttpServletResponse.SC_BAD_GATEWAY,
                    "文件内容为空");
        }
        String fileName = StringUtils.defaultIfBlank(
                data.getString("fileName"), "未命名文本");
        String mediaType = StringUtils.defaultIfBlank(
                data.getString("mediaType"), "text/plain");
        FilePreviewDTO dto = new FilePreviewDTO();
        dto.setSource(source);
        dto.setDisplayName(fileName);
        dto.setContent(content);
        dto.setMediaType(mediaType);
        dto.setCharset(StringUtils.defaultIfBlank(data.getString("charset"), "UTF-8"));
        dto.setRenderMode(renderMode(fileName, mediaType));
        dto.setLanguage(language(fileName));
        dto.setRequestedLine(requestedLine);
        dto.setLineCount(countLines(content));
        dto.setSize(data.getLongValue("bytesRead"));
        dto.setTruncated(data.getBooleanValue("truncated"));
        dto.setBaseUrl(baseUrl);
        return dto;
    }

    static String renderMode(String fileName, String mediaType) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = mediaType == null ? "" : mediaType.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".markdown")
                || type.contains("markdown")) return "MARKDOWN";
        if (lower.endsWith(".html") || lower.endsWith(".htm")
                || type.contains("text/html") || type.contains("xhtml")) return "HTML";
        return language(fileName) == null ? "TEXT" : "CODE";
    }

    static String language(String fileName) {
        if (fileName == null) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        Map<String, String> languages = new HashMap<>();
        languages.put(".java", "java"); languages.put(".kt", "kotlin");
        languages.put(".js", "javascript"); languages.put(".ts", "typescript");
        languages.put(".py", "python"); languages.put(".c", "c");
        languages.put(".cpp", "cpp"); languages.put(".h", "cpp");
        languages.put(".cs", "csharp"); languages.put(".xml", "xml");
        languages.put(".json", "json"); languages.put(".sh", "bash");
        languages.put(".sql", "sql"); languages.put(".yaml", "yaml");
        languages.put(".yml", "yaml"); languages.put(".properties", "properties");
        for (Map.Entry<String, String> entry : languages.entrySet()) {
            if (lower.endsWith(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private void validateRequest(FilePreviewRequest request) {
        if (request == null || StringUtils.isBlank(request.getChatterId())
                || StringUtils.isBlank(request.getSessionId())
                || StringUtils.isBlank(request.getTarget())
                || request.getTarget().length() > 8192) {
            throw failure("INVALID_ARGUMENT", HttpServletResponse.SC_BAD_REQUEST,
                    "文件预览参数不完整");
        }
        if (request.getRequestedLine() != null && request.getRequestedLine() < 1) {
            throw failure("INVALID_ARGUMENT", HttpServletResponse.SC_BAD_REQUEST,
                    "行号必须大于0");
        }
    }

    private static boolean isRemote(String target) {
        String lower = target.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String normalizeLocalTarget(String target) {
        String value = target.trim();
        if (value.regionMatches(true, 0, "file:///", 0, 8)) {
            value = value.substring(7);
            if (value.matches("^/[A-Za-z]:/.*")) value = value.substring(1);
        } else if (value.regionMatches(true, 0, "file://", 0, 7)) {
            value = value.substring(7);
        }
        return value;
    }

    private static URI parseRemoteUri(String value) {
        try {
            URI uri = new URI(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw failure("REMOTE_BLOCKED", HttpServletResponse.SC_FORBIDDEN,
                        "远程链接不允许访问");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw failure("INVALID_ARGUMENT", HttpServletResponse.SC_BAD_REQUEST,
                    "远程链接格式错误", e);
        }
    }

    private static void validateRemoteDestination(URI uri) throws IOException {
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw failure("REMOTE_BLOCKED", HttpServletResponse.SC_FORBIDDEN,
                    "远程链接不允许访问");
        }
        String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if ("localhost".equals(host) || host.endsWith(".localhost")) {
            throw failure("REMOTE_BLOCKED", HttpServletResponse.SC_FORBIDDEN,
                    "远程链接不允许访问");
        }
        for (InetAddress address : InetAddress.getAllByName(host)) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress() || isUniqueLocal(address)) {
                throw failure("REMOTE_BLOCKED", HttpServletResponse.SC_FORBIDDEN,
                        "远程链接不允许访问");
            }
        }
    }

    private static boolean isUniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) return false;
        byte first = address.getAddress()[0];
        return (first & 0xfe) == 0xfc;
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) >= 0) {
                total += read;
                if (total > limit) {
                    throw failure("REMOTE_TOO_LARGE",
                            HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                            "远程文件过大");
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static DecodedText decodeText(byte[] bytes, String declaredCharset,
                                          String mediaType) {
        Charset charset = null;
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            charset = StandardCharsets.UTF_8; offset = 3;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xff
                && bytes[1] == (byte) 0xfe) {
            charset = StandardCharsets.UTF_16LE; offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xfe
                && bytes[1] == (byte) 0xff) {
            charset = StandardCharsets.UTF_16BE; offset = 2;
        } else if (StringUtils.isNotBlank(declaredCharset)) {
            try { charset = Charset.forName(declaredCharset); } catch (Exception ignored) { }
        }
        if (charset == null) charset = StandardCharsets.UTF_8;
        if (looksBinary(bytes, offset, charset)) {
            throw failure("REMOTE_NOT_TEXT", HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "远程链接不是文本文件");
        }
        try {
            CharBuffer decoded = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
            return new DecodedText(decoded.toString(), charset.name());
        } catch (CharacterCodingException e) {
            throw failure("REMOTE_NOT_TEXT", HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "远程链接不是可识别文本", e);
        }
    }

    private static boolean looksBinary(byte[] bytes, int offset, Charset charset) {
        if (StandardCharsets.UTF_16LE.equals(charset)
                || StandardCharsets.UTF_16BE.equals(charset)) return false;
        int controls = 0;
        int sample = Math.min(bytes.length, offset + 8192);
        for (int i = offset; i < sample; i++) {
            int value = bytes[i] & 0xff;
            if (value == 0) return true;
            if (value < 0x20 && value != '\n' && value != '\r' && value != '\t'
                    && value != '\f' && value != '\b') controls++;
        }
        return sample > offset && controls * 20 > sample - offset;
    }

    private static String mediaType(HttpEntity entity) {
        Header type = entity.getContentType();
        if (type == null) return "application/octet-stream";
        String value = type.getValue();
        int separator = value.indexOf(';');
        return (separator < 0 ? value : value.substring(0, separator)).trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String charset(HttpEntity entity) {
        Header type = entity.getContentType();
        if (type == null) return null;
        for (String part : type.getValue().split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                return trimmed.substring("charset=".length()).replace("\"", "").trim();
            }
        }
        return null;
    }

    private static String displayName(URI uri) {
        String path = uri.getPath();
        if (StringUtils.isBlank(path) || path.endsWith("/")) return uri.getHost();
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static int countLines(String content) {
        if (content.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < content.length(); i++) if (content.charAt(i) == '\n') count++;
        return count;
    }

    private static FilePreviewException mapCommandFailure(String code, String message,
                                                          Throwable cause) {
        String normalized = StringUtils.defaultIfBlank(code, "IO_ERROR");
        int status = HttpServletResponse.SC_BAD_GATEWAY;
        if ("FILE_NOT_FOUND".equals(normalized)) status = HttpServletResponse.SC_NOT_FOUND;
        else if ("PATH_OUTSIDE_WORKSPACE".equals(normalized)
                || "PATH_NOT_ALLOWED".equals(normalized)
                || "PERMISSION_DENIED".equals(normalized)) status = HttpServletResponse.SC_FORBIDDEN;
        else if ("FILE_TOO_LARGE".equals(normalized)) status = HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE;
        else if ("BINARY_FILE".equals(normalized)
                || "INVALID_ENCODING".equals(normalized)
                || "NOT_REGULAR_FILE".equals(normalized)) status = HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
        else if ("BUSY".equals(normalized)) status = 429;
        else if ("TIMEOUT".equals(normalized)) status = HttpServletResponse.SC_GATEWAY_TIMEOUT;
        else if ("TEAM_NOT_READY".equals(normalized)
                || "ROUTE_NOT_FOUND".equals(normalized)) status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
        return failure(normalized, status,
                StringUtils.defaultIfBlank(message, "文件读取失败"), cause);
    }

    private static FilePreviewException failure(String code, int status, String message) {
        return new FilePreviewException(code, status, message);
    }

    private static FilePreviewException failure(String code, int status, String message,
                                                Throwable cause) {
        return new FilePreviewException(code, status, message, cause);
    }

    private static final class DecodedText {
        private final String content;
        private final String charset;

        private DecodedText(String content, String charset) {
            this.content = content;
            this.charset = charset;
        }
    }
}
