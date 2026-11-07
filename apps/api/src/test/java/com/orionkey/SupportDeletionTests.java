package com.orionkey;

import com.orionkey.entity.SupportMessage;
import com.orionkey.exception.BusinessException;
import com.orionkey.repository.*;
import com.orionkey.service.SupportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportDeletionTests {
    @Autowired SupportService support;
    @Autowired SupportConversationRepository conversations;
    @Autowired SupportMessageRepository messages;
    @Autowired SupportImageRepository images;
    @Autowired MockMvc mvc;

    @Test
    void guestCannotDeleteConversation() throws Exception {
        var conversation = support.create(null, null, "hello");
        mvc.perform(delete("/api/admin/support/" + conversation.id()).contextPath("/api"))
                .andExpect(status().isUnauthorized());
        assertTrue(conversations.existsById(conversation.id()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deletionRemovesOnlySelectedConversationIncludingImagesAndCannotResurrect() throws Exception {
        var first = support.create(null, null, "first customer");
        var second = support.create(null, null, "second customer");
        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        var photo = support.customerImage(first.id(), first.token(), new MockMultipartFile("file", "photo.png", "image/png", png));
        SupportMessage pending = messages.findById(photo.id()).orElseThrow();
        var staleConversation = conversations.findById(first.id()).orElseThrow();

        mvc.perform(delete("/api/admin/support/" + first.id()).contextPath("/api"))
                .andExpect(status().isOk());

        assertFalse(conversations.existsById(first.id()));
        assertFalse(images.existsById(photo.id()));
        assertTrue(messages.findTop200ByConversationIdOrderByCreatedAtDesc(first.id()).isEmpty());
        assertEquals(1, support.customerConversation(second.id(), second.token()).messages().size());
        assertThrows(BusinessException.class, () -> support.customerMessage(first.id(), first.token(), "late reply"));
        assertThrows(OptimisticLockingFailureException.class, () -> messages.saveAndFlush(pending));
        assertThrows(OptimisticLockingFailureException.class, () -> conversations.saveAndFlush(staleConversation));
        assertFalse(conversations.existsById(first.id()));
    }
}
