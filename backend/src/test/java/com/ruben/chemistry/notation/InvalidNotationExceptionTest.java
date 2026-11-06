package com.ruben.chemistry.notation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvalidNotationExceptionTest
{
    private static final String NL = System.lineSeparator();

    @Test
    void message_includes_line_and_column_without_context_frames()
    {
        InvalidNotationException exception = new InvalidNotationException("Unexpected atom 'Zz'", "CC(=O)O", 6);

        String expected = "Unexpected atom 'Zz' at line 1, column 7" + NL
            + "CC(=O)O" + NL
            + "      ^";

        assertThat(exception.getMessage()).isEqualTo(expected);
    }

    @Test
    void message_appends_single_character_context_frame_block()
    {
        InvalidNotationException.ContextFrame ring =
            new InvalidNotationException.ContextFrame("inside aromatic ring opened", 0);

        InvalidNotationException exception = new InvalidNotationException(
            "Unexpected atom 'Hg'", "c1ccccc1[Hg]", 9, List.of(ring));

        String expected = "Unexpected atom 'Hg' at line 1, column 10" + NL
            + "c1ccccc1[Hg]" + NL
            + "         ^" + NL
            + "  inside aromatic ring opened at line 1, column 1" + NL
            + "c1ccccc1[Hg]" + NL
            + "^";

        assertThat(exception.getMessage()).isEqualTo(expected);
    }

    @Test
    void message_renders_caret_and_tildes_for_a_ranged_context_frame()
    {
        // "XYZ[XXX]XXX" - highlight the three X's inside the brackets (indices 4,5,6).
        InvalidNotationException.ContextFrame insideBrackets =
            new InvalidNotationException.ContextFrame("inside brackets", 4, 7);

        InvalidNotationException exception = new InvalidNotationException(
            "Found explicit range target", "XYZ[XXX]XXX", 0, List.of(insideBrackets));

        String expected = "Found explicit range target at line 1, column 1" + NL
            + "XYZ[XXX]XXX" + NL
            + "^" + NL
            + "  inside brackets at line 1, column 5" + NL
            + "XYZ[XXX]XXX" + NL
            + "    ^~~";

        assertThat(exception.getMessage()).isEqualTo(expected);
    }

    @Test
    void context_frame_span_is_clamped_to_its_own_line()
    {
        // Frame's endPosition (10) overshoots both its line and the whole source.
        InvalidNotationException.ContextFrame overshooting =
            new InvalidNotationException.ContextFrame("desc", 1, 10);

        InvalidNotationException exception = new InvalidNotationException(
            "Err", "AB\nCDEF", 0, List.of(overshooting));

        String expected = "Err at line 1, column 1" + NL
            + "AB" + NL
            + "^" + NL
            + "  desc at line 1, column 2" + NL
            + "AB" + NL
            + " ^";

        assertThat(exception.getMessage()).isEqualTo(expected);
    }

    @Test
    void multiple_context_frames_are_rendered_in_list_order()
    {
        var innermost = new InvalidNotationException.ContextFrame("inside branch", 2);
        var outermost = new InvalidNotationException.ContextFrame("inside ring", 0);

        InvalidNotationException exception = new InvalidNotationException(
            "Bad token", "C(C)C", 4, List.of(innermost, outermost));

        assertThat(exception.getMessage())
            .containsSubsequence("inside branch", "inside ring");
    }

    @Test
    void context_frame_single_argument_constructor_defaults_end_position_to_position_plus_one()
    {
        var frame = new InvalidNotationException.ContextFrame("desc", 5);

        assertThat(frame.position()).isEqualTo(5);
        assertThat(frame.endPosition()).isEqualTo(6);
    }

    @Test
    void with_context_appends_a_frame_without_mutating_the_original_exception()
    {
        InvalidNotationException base = new InvalidNotationException("Bad token", "CCO", 1);

        InvalidNotationException enriched = base.withContext(new InvalidNotationException.ContextFrame("inside ring opened", 0, 3));

        assertThat(base.contextFrames()).isEmpty();
        assertThat(enriched.contextFrames()).hasSize(1);
        assertThat(enriched.contextFrames().get(0).description()).isEqualTo("inside ring opened");
        assertThat(enriched.rawMessage()).isEqualTo(base.rawMessage());
    }

    @Test
    void with_context_preserves_existing_frames_and_appends_in_order()
    {
        var first = new InvalidNotationException.ContextFrame("inside branch", 2);
        var second = new InvalidNotationException.ContextFrame("inside ring", 0);

        InvalidNotationException chained = new InvalidNotationException("Bad token", "C(C)C", 4)
                .withContext(first)
                .withContext(second);

        assertThat(chained.contextFrames()).containsExactly(first, second);
    }

    @Test
    void context_frames_list_is_unmodifiable()
    {
        InvalidNotationException exception = new InvalidNotationException(
            "Bad token", "CCO", 1,
            List.of(new InvalidNotationException.ContextFrame("inside branch", 0)));

        assertThrows(
            UnsupportedOperationException.class,
            () -> exception.contextFrames().add(new InvalidNotationException.ContextFrame("x", 0)));
    }

    @Test
    void raw_message_excludes_position_and_context_decoration()
    {
        InvalidNotationException exception = new InvalidNotationException(
            "Unexpected atom 'Hg'", "c1ccccc1[Hg]", 9,
            List.of(new InvalidNotationException.ContextFrame("inside ring", 0)));

        assertThat(exception.rawMessage()).isEqualTo("Unexpected atom 'Hg'");
    }

    @Test
    void negative_position_is_clamped_to_zero()
    {
        InvalidNotationException exception = new InvalidNotationException("Bad token", "abc", -5);

        assertThat(exception.position()).isZero();
        assertThat(exception.line()).isEqualTo(1);
        assertThat(exception.column()).isEqualTo(1);
    }

    @Test
    void position_past_the_end_of_source_is_clamped_to_source_length()
    {
        InvalidNotationException exception = new InvalidNotationException("Bad token", "abc", 50);

        assertThat(exception.position()).isEqualTo(3);
        assertThat(exception.column()).isEqualTo(4);
    }

    @Test
    void line_and_column_account_for_embedded_newlines()
    {
        InvalidNotationException exception = new InvalidNotationException("Bad token", "CCO\nXYZ", 5);

        assertThat(exception.line()).isEqualTo(2);
        assertThat(exception.column()).isEqualTo(2);
    }
}
