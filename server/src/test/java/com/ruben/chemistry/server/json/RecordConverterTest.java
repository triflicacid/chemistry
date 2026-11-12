package com.ruben.chemistry.server.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordConverterTest
{
    enum Layout { ROW, COLUMN }

    record Colour(String element, int rgb)
    {
    }

    record Options(Layout layout, Boolean metadata, Double scale, Colour background, List<Colour> colours,
                   Map<String, Colour> named, Integer limit, Object extra)
    {
    }

    record Request(String smiles, Options options)
    {
    }

    record Positive(int n)
    {
        Positive
        {
            if (n <= 0)
            {
                throw new IllegalArgumentException("must be positive");
            }
        }
    }

    @Test
    void should_bind_nested_records_lists_maps_and_enums()
    {
        Request bound = parse("""
                {"smiles":"CCO","options":{"layout":"COLUMN","metadata":true,"scale":2,
                "background":{"element":"C","rgb":16},
                "colours":[{"element":"N","rgb":1},{"element":"O","rgb":2}],
                "named":{"x":{"element":"H","rgb":3}},"extra":[1,"a"]}}""", Request.class);

        assertThat(bound.smiles()).isEqualTo("CCO");
        assertThat(bound.options().layout()).isEqualTo(Layout.COLUMN);
        assertThat(bound.options().metadata()).isTrue();
        assertThat(bound.options().scale()).isEqualTo(2.0);
        assertThat(bound.options().background()).isEqualTo(new Colour("C", 16));
        assertThat(bound.options().colours()).containsExactly(new Colour("N", 1), new Colour("O", 2));
        assertThat(bound.options().named()).containsEntry("x", new Colour("H", 3));
        assertThat(bound.options().limit()).isNull();
        assertThat(bound.options().extra()).isEqualTo(List.of(1L, "a"));
    }

    @Test
    void should_leave_missing_reference_components_null()
    {
        Request bound = parse("{\"smiles\":\"C\"}", Request.class);

        assertThat(bound.options()).isNull();
    }

    @Test
    void should_require_primitive_components()
    {
        assertThatThrownBy(() -> parse("{}", Positive.class))
                .isInstanceOf(JsonException.class).hasMessage("n: is required");
    }

    @Test
    void should_reject_unknown_properties_with_their_path()
    {
        assertThatThrownBy(() -> parse("{\"smiles\":\"C\",\"options\":{\"nope\":1}}", Request.class))
                .isInstanceOf(JsonException.class).hasMessage("options.nope: unknown property");
    }

    @Test
    void should_report_type_mismatches_with_their_path()
    {
        assertThatThrownBy(() -> parse("{\"smiles\":1}", Request.class))
                .hasMessage("smiles: expected a string, got an integer");
        assertThatThrownBy(() -> parse("{\"options\":[]}", Request.class))
                .hasMessage("options: expected an object, got an array");
        assertThatThrownBy(() -> parse("{\"options\":{\"colours\":[{\"rgb\":\"x\"}]}}", Request.class))
                .hasMessage("options.colours[0].rgb: expected an integer, got a string");
        assertThatThrownBy(() -> parse("{\"options\":{\"layout\":\"GRID\"}}", Request.class))
                .hasMessage("options.layout: must be one of [ROW, COLUMN]");
        assertThatThrownBy(() -> parse("{\"n\":1.5}", Positive.class))
                .hasMessage("n: expected an integer, got a number");
        assertThatThrownBy(() -> parse("{\"n\":3000000000}", Positive.class))
                .hasMessage("n: must be between -2147483648 and 2147483647");
    }

    @Test
    void should_report_a_failing_record_constructor()
    {
        assertThatThrownBy(() -> parse("{\"n\":0}", Positive.class))
                .isInstanceOf(JsonException.class).hasMessage("must be positive");
    }

    record Named(String myValue, int httpCode, String smilesURL, Colour innerColour)
    {
    }

    @Test
    void should_use_snake_case_keys_both_ways()
    {
        Named named = new Named("a", 7, "u", new Colour("C", 1));
        String json = JsonWriter.write(named);

        assertThat(json).isEqualTo(
                "{\"my_value\":\"a\",\"http_code\":7,\"smiles_url\":\"u\",\"inner_colour\":{\"element\":\"C\",\"rgb\":1}}");
        assertThat(parse(json, Named.class)).isEqualTo(named);
    }

    @Test
    void should_reject_camel_case_keys()
    {
        assertThatThrownBy(() -> parse("{\"myValue\":\"a\",\"http_code\":1}", Named.class))
                .hasMessage("myValue: unknown property");
    }

    @Test
    void should_name_nested_paths_in_snake_case()
    {
        assertThatThrownBy(() -> parse("{\"inner_colour\":{\"rgb\":\"x\"},\"http_code\":1}", Named.class))
                .hasMessage("inner_colour.rgb: expected an integer, got a string");
    }

    @Test
    void should_read_keys_through_a_custom_function()
    {
        Named named = RecordConverter.bind(
                Map.of("my_value", "a", "http_code", 7L, "smiles_url", "u"), Named.class, JsonNames::of);

        assertThat(named).isEqualTo(new Named("a", 7, "u", null));
        assertThatThrownBy(() -> RecordConverter.bind(Map.of("my_value", "a"), Named.class, key -> key))
                .hasMessage("my_value: unknown property");
    }

    @Test
    void should_reject_types_that_are_not_records()
    {
        assertThatThrownBy(() -> RecordConverter.bind(Map.of(), (Class) String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static <T extends Record> T parse(String json, Class<T> type)
    {
        return RecordConverter.bind(JsonParser.parse(json), type, JsonNames::of);
    }
}
