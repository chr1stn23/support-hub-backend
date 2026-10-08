package com.chrisdev.supporthub.common.util;

import com.chrisdev.supporthub.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TextsTest {

    @Test
    void escapeLikeNeutralisesWildcards() {
        assertThat(Texts.escapeLike("50%_\\")).isEqualTo("50\\%\\_\\\\");
    }

    @Test
    void blankToNullTrimsAndNormalises() {
        assertThat(Texts.blankToNull("   ")).isNull();
        assertThat(Texts.blankToNull(null)).isNull();
        assertThat(Texts.blankToNull("  hola ")).isEqualTo("hola");
    }

    @Test
    void requireTextRejectsBlankValues() {
        assertThat(Texts.requireText("title", "  x ")).isEqualTo("x");
        assertThatThrownBy(() -> Texts.requireText("title", "  ")).isInstanceOf(BusinessException.class);
    }
}
