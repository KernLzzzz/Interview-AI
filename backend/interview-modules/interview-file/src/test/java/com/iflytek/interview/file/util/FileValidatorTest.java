package com.iflytek.interview.file.util;

import com.iflytek.interview.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    @BeforeEach
    void setUp() {
        validator.setAllowedTypes(List.of("audio/webm", "video/webm"));
        validator.setMaxSize(1024);
    }

    @Test
    void acceptsMediaRecorderMimeTypeWithCodecParameter() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "interview.webm", "audio/webm;codecs=opus", new byte[32]);

        assertDoesNotThrow(() -> validator.validate(file));
    }

    @Test
    void rejectsOversizedMedia() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "interview.webm", "video/webm", new byte[1025]);

        assertThrows(BusinessException.class, () -> validator.validate(file));
    }
}
