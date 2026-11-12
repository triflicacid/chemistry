package com.ruben.chemistry.server.json;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonTest
{
    @Nested
    class Parsing
    {
        @Test
        void should_read_every_kind_of_value()
        {
            Map<String, Object> parsed = JsonParser.parse(
                    " {\"s\":\"a\\n\\u0041\\\"\",\"i\":-12,\"d\":1.5e2,\"t\":true,\"f\":false,\"n\":null,\"l\":[1,[2]],\"o\":{}} ");

            assertThat(parsed).containsEntry("s", "a\nA\"")
                    .containsEntry("i", -12L)
                    .containsEntry("d", 150.0)
                    .containsEntry("t", true)
                    .containsEntry("f", false)
                    .containsEntry("n", null)
                    .containsEntry("l", List.of(1L, List.of(2L)))
                    .containsEntry("o", Map.of());
        }

        @Test
        void should_keep_document_order()
        {
            assertThat(JsonParser.parse("{\"b\":1,\"a\":2}").keySet()).containsExactly("b", "a");
        }

        @Test
        void should_reject_anything_but_an_object_at_the_top()
        {
            for (String text : new String[]{"[]", "1", "\"x\"", "true", "null", ""})
            {
                assertThatThrownBy(() -> JsonParser.parse(text)).isInstanceOf(JsonException.class);
            }
        }

        @Test
        void should_reject_malformed_documents()
        {
            for (String text : new String[]{
                    "{", "{\"a\"}", "{\"a\":1,}", "{\"a\":[1,]}", "{a:1}", "{\"a\":01}", "{\"a\":1.}",
                    "{\"a\":NaN}", "{\"a\":1e999}", "{\"a\":\"\\x\"}", "{\"a\":\"\\u12\"}", "{\"a\":\"line\nbreak\"}",
                    "{\"a\":1} extra", "{\"a\":1,\"a\":2}", "{\"a\":tru}"})
            {
                assertThatThrownBy(() -> JsonParser.parse(text)).as(text).isInstanceOf(JsonException.class);
            }
        }

        @Test
        void should_reject_excessive_nesting()
        {
            String deep = "{\"a\":" + "[".repeat(100) + "]".repeat(100) + "}";
            assertThatThrownBy(() -> JsonParser.parse(deep)).isInstanceOf(JsonException.class);
        }

        @Test
        void should_report_the_offset()
        {
            assertThatThrownBy(() -> JsonParser.parse("{\"a\": ?}"))
                    .isInstanceOfSatisfying(JsonException.class, e -> assertThat(e.offset()).isEqualTo(6));
        }
    }

    @Nested
    class Writing
    {
        @Test
        void should_write_compact_json()
        {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("a", 1);
            value.put("b", List.of(true, "x"));
            value.put("c", null);

            assertThat(JsonWriter.write(value)).isEqualTo("{\"a\":1,\"b\":[true,\"x\"],\"c\":null}");
        }

        @Test
        void should_escape_strings()
        {
            assertThat(JsonWriter.write(Map.of("k", "\"\\\n\t\u0001é"))).isEqualTo("{\"k\":\"\\\"\\\\\\n\\t\\u0001é\"}");
        }

        record Point(int xCoord, int yCoord)
        {
        }

        record Shape(String shapeName, Point firstPoint, List<Point> otherPoints, String smilesURL, Integer missing)
        {
        }

        @Test
        void should_write_records_with_snake_case_names()
        {
            Shape shape = new Shape("tri", new Point(1, 2), List.of(new Point(3, 4)), "u", null);

            assertThat(JsonWriter.write(shape)).isEqualTo(
                    "{\"shape_name\":\"tri\",\"first_point\":{\"x_coord\":1,\"y_coord\":2},"
                            + "\"other_points\":[{\"x_coord\":3,\"y_coord\":4}],\"smiles_url\":\"u\",\"missing\":null}");
        }

        @Test
        void should_write_records_inside_maps()
        {
            assertThat(JsonWriter.write(Map.of("p", new Point(0, 0)))).isEqualTo("{\"p\":{\"x_coord\":0,\"y_coord\":0}}");
        }

        @Test
        void should_reject_non_finite_numbers_and_unknown_types()
        {
            assertThatThrownBy(() -> JsonWriter.write(Map.of("a", Double.NaN))).isInstanceOf(JsonException.class);
            assertThatThrownBy(() -> JsonWriter.write(Map.of("a", new Object()))).isInstanceOf(JsonException.class);
        }
    }

    @Nested
    class ExtractProperty
    {
        private final Map<String, Object> document = JsonParser.parse(
                "{\"a\":{\"b\":[10,{\"c\":\"x\"}]},\"n\":null,\"0\":\"zero\"}");

        @Test
        void should_follow_keys_and_indexes()
        {
            String c = Json.extractProperty(document, "a", "b", "1", "c");
            Long ten = Json.extractProperty(document, "a", "b", "0");

            assertThat(c).isEqualTo("x");
            assertThat(ten).isEqualTo(10L);
        }

        @Test
        void should_treat_a_numeric_key_inside_a_map_as_a_key()
        {
            String zero = Json.extractProperty(document, "0");
            assertThat(zero).isEqualTo("zero");
        }

        @Test
        void should_return_the_value_itself_for_an_empty_path()
        {
            Map<String, Object> same = Json.extractProperty(document);
            assertThat(same).isSameAs(document);
        }

        @Test
        void should_return_null_for_a_missing_last_key()
        {
            assertThat((Object) Json.extractProperty(document, "a", "missing")).isNull();
            assertThat((Object) Json.extractProperty(document, "a", "b", "9")).isNull();
            assertThat((Object) Json.extractProperty(document, "n")).isNull();
        }

        @Test
        void should_throw_for_any_other_mistake()
        {
            assertThatThrownBy(() -> Json.extractProperty(document, "missing", "x")).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> Json.extractProperty(document, "n", "x")).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> Json.extractProperty(document, "a", "b", "x")).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> Json.extractProperty(document, "a", "b", "-1")).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> Json.extractProperty(document, "a", "b", "9", "c")).isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> Json.extractProperty(document, "0", "x")).isInstanceOf(NoSuchElementException.class);
        }
    }
}
