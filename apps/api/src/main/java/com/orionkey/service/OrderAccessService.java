package com.orionkey.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orionkey.constant.ErrorCode;
import com.orionkey.context.RequestContext;
import com.orionkey.entity.Order;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.OrderRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderAccessService {
    private static final BCryptPasswordEncoder PASSWORDS = new BCryptPasswordEncoder();
    private final OrderRepository orders;
    private final ObjectMapper mapper;

    public record Proof(String token, String email, String password) {}

    public void initialize(Order order, Map<String, Object> request) {
        String token = Objects.toString(request.get("access_token"), "");
        String password = Objects.toString(request.get("lookup_password"), "");
        if (!token.matches("[a-f0-9]{64}") || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请设置至少 8 位查询密码（最多 72 字节）");
        }
        order.setAccessTokenHash(hash(token));
        order.setLookupPasswordHash(PASSWORDS.encode(password));
    }

    public boolean ownsCreation(Order order, Map<String, Object> request) {
        return validToken(order, Objects.toString(request.get("access_token"), ""));
    }

    public void authorize(UUID id, HttpServletRequest request) {
        require(find(id), decode(request.getHeader("X-Order-Proof")), request.getHeader("X-Session-Token"));
    }

    public List<String> authorizeBatch(Map<String, Object> body, HttpServletRequest request) {
        Object rawIds = body.get("order_ids");
        if (!(rawIds instanceof List<?> ids) || ids.isEmpty() || ids.size() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "每次可查询 1 至 20 个订单");
        }
        Map<?, ?> proofs = body.get("order_credentials") instanceof Map<?, ?> map ? map : Map.of();
        List<String> result = new ArrayList<>();
        for (Object raw : ids) {
            UUID id = parseId(raw);
            Object encoded = proofs.containsKey(id.toString()) ? proofs.get(id.toString()) : proofs.get(raw);
            Proof proof = decode(encoded instanceof String s ? s : request.getHeader("X-Order-Proof"));
            require(find(id), proof, request.getHeader("X-Session-Token"));
            result.add(id.toString());
        }
        return result;
    }

    public List<String> resolveQuery(Map<String, Object> body, HttpServletRequest request) {
        if (body.containsKey("emails")) {
            if (!(body.get("emails") instanceof List<?> emails) || emails.size() != 1
                    || !(emails.get(0) instanceof String email) || email.length() > 254) {
                deny();
            }
            String email = ((List<?>) body.get("emails")).get(0).toString().trim();
            String password = Objects.toString(body.get("lookup_password"), "");
            if (password.getBytes(StandardCharsets.UTF_8).length > 72) deny();
            Proof proof = new Proof(null, email, password);
            List<String> ids = orders.findTop20ByEmailIgnoreCaseAndIsDeletedOrderByCreatedAtDesc(email, 0)
                    .stream().filter(order -> permitted(order, proof, request.getHeader("X-Session-Token")))
                    .map(order -> order.getId().toString()).toList();
            if (ids.isEmpty()) deny();
            return ids;
        }
        return authorizeBatch(body, request);
    }

    private Order find(UUID id) {
        return orders.findById(id).filter(o -> o.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "订单不存在或查询凭证不正确", HttpStatus.FORBIDDEN));
    }

    private UUID parseId(Object raw) {
        try { return UUID.fromString(Objects.toString(raw, "")); }
        catch (IllegalArgumentException e) { deny(); return null; }
    }

    private void require(Order order, Proof proof, String session) {
        if (!permitted(order, proof, session)) deny();
    }

    private boolean permitted(Order order, Proof proof, String session) {
        if ("ADMIN".equals(RequestContext.getRole())) return true;
        if (validToken(order, proof.token())) return true;
        if ((proof.email() == null || (order.getEmail() != null && order.getEmail().equalsIgnoreCase(proof.email().trim())))
                && proof.password() != null && proof.password().length() >= 8
                && proof.password().getBytes(StandardCharsets.UTF_8).length <= 72
                && order.getLookupPasswordHash() != null
                && PASSWORDS.matches(proof.password(), order.getLookupPasswordHash())) return true;
        // Legacy orders have no new credential; only their original nonempty guest session remains valid.
        return order.getAccessTokenHash() == null && order.getSessionToken() != null
                && !order.getSessionToken().isBlank() && session != null && !session.isBlank()
                && MessageDigest.isEqual(order.getSessionToken().getBytes(StandardCharsets.UTF_8), session.getBytes(StandardCharsets.UTF_8));
    }

    private Proof decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return new Proof(null, null, null);
        if (encoded.length() > 2048) { deny(); }
        try {
            Proof proof = mapper.readValue(Base64.getUrlDecoder().decode(encoded), Proof.class);
            if (proof == null) deny();
            return proof;
        } catch (Exception e) { deny(); return null; }
    }

    private boolean validToken(Order order, String token) {
        return token != null && token.matches("[a-f0-9]{64}") && order.getAccessTokenHash() != null
                && MessageDigest.isEqual(order.getAccessTokenHash().getBytes(StandardCharsets.US_ASCII),
                hash(token).getBytes(StandardCharsets.US_ASCII));
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void deny() {
        throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED, "订单不存在或查询凭证不正确；旧订单请在原浏览器查询或联系客服", HttpStatus.FORBIDDEN);
    }
}
