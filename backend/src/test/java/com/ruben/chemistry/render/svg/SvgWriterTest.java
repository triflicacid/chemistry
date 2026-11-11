package com.ruben.chemistry.render.svg;

import com.ruben.chemistry.render.Colour;
import com.ruben.chemistry.render.Point;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SvgWriterTest
{
    private static final Colour RED = Colour.of("#ff0000");

    @Test
    void writes_an_empty_document_of_the_size_asked_for()
    {
        assertThat(new SvgWriter(98, 48.5).render()).isEqualTo(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"98\" height=\"48.5\" viewBox=\"0 0 98 48.5\">\n</svg>\n");
    }

    @Test
    void refuses_a_document_with_a_negative_size()
    {
        assertThatThrownBy(() -> new SvgWriter(-1, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SvgWriter(10, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allows_a_document_with_no_height_as_a_single_horizontal_bond_has_none()
    {
        assertThat(new SvgWriter(50, 0).render()).contains("width=\"50\" height=\"0\" viewBox=\"0 0 50 0\"");
    }

    @Test
    void writes_the_background_before_everything_else_whenever_it_is_set()
    {
        SvgWriter svg = new SvgWriter(10, 10);
        svg.circle(new Point(5, 5), 1);
        svg.background(RED);

        assertThat(svg.render()).containsSubsequence("<rect x=\"0\" y=\"0\" width=\"10\" height=\"10\" fill=\"#ff0000\"/>", "<circle");
    }

    @Test
    void puts_attributes_on_the_document_and_leaves_out_missing_ones()
    {
        String svg = new SvgWriter(10, 10).attribute("data-a", "1 & 2").attribute("data-b", null).render();

        assertThat(svg).contains("viewBox=\"0 0 10 10\" data-a=\"1 &amp; 2\">").doesNotContain("data-b");
    }

    @Nested
    class Shapes
    {
        @Test
        void writes_a_line()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.line(new Point(1, 2), new Point(3.5, 4)).stroke(RED).strokeWidth(2).roundCaps();

            assertThat(svg.render()).contains(
                "  <line x1=\"1\" y1=\"2\" x2=\"3.5\" y2=\"4\" stroke=\"#ff0000\" stroke-width=\"2\" stroke-linecap=\"round\"/>\n");
        }

        @Test
        void writes_a_circle()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.circle(new Point(5, 6), 2.25).fill("none").stroke(RED);

            assertThat(svg.render()).contains("  <circle cx=\"5\" cy=\"6\" r=\"2.25\" fill=\"none\" stroke=\"#ff0000\"/>\n");
        }

        @Test
        void writes_a_rectangle()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.rect(new Point(1, 2), 3, 4).fill(RED).dash(4, 3);

            assertThat(svg.render()).contains("  <rect x=\"1\" y=\"2\" width=\"3\" height=\"4\" fill=\"#ff0000\" stroke-dasharray=\"4 3\"/>\n");
        }

        @Test
        void writes_a_polygon_from_its_corners()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.polygon(List.of(new Point(0, 0), new Point(2, 0), new Point(1, 1.5))).roundJoins();

            assertThat(svg.render()).contains("  <polygon points=\"0,0 2,0 1,1.5\" stroke-linejoin=\"round\"/>\n");
        }

        @Test
        void refuses_a_polygon_with_fewer_than_three_corners()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            List<Point> two = List.of(new Point(0, 0), new Point(1, 1));

            assertThatThrownBy(() -> svg.polygon(two)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void writes_text_with_plain_content_escaped()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.text(new Point(1, 2)).attribute("font-family", "Smith & \"Sons\"").content("a<b");

            assertThat(svg.render()).contains("  <text x=\"1\" y=\"2\" font-family=\"Smith &amp; &quot;Sons&quot;\">a&lt;b</text>\n");
        }

        @Test
        void writes_text_in_runs_each_with_a_look_of_its_own()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.text(new Point(1, 2)).span("13", "font-size", "7", "dy", "-4").span("C", "dy", null);

            assertThat(svg.render()).contains("<text x=\"1\" y=\"2\"><tspan font-size=\"7\" dy=\"-4\">13</tspan><tspan>C</tspan></text>\n");
        }

        @Test
        void writes_a_title_and_a_description()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.title("Ethane");
            svg.description("two carbons");

            assertThat(svg.render()).contains("  <title>Ethane</title>\n  <desc>two carbons</desc>\n");
        }

        @Test
        void writes_numbers_plainly()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.circle(new Point(0.1 + 0.2, 1e-9), 1234567.0004);

            assertThat(svg.render()).contains("cx=\"0.3\" cy=\"0\" r=\"1234567\"");
        }

        @Test
        void writes_attributes_in_the_order_they_are_given()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.circle(new Point(0, 0), 1).attribute("b", "2").attribute("a", 1).cssClass("x");

            assertThat(svg.render()).contains(" r=\"1\" b=\"2\" a=\"1\" class=\"x\"/>");
        }
    }

    @Nested
    class Groups
    {
        @Test
        void holds_what_is_written_until_it_is_closed()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.group().cssClass("bond").attribute("data-bond", "3");
            svg.circle(new Point(1, 1), 1);
            svg.close();
            svg.circle(new Point(2, 2), 1);

            assertThat(svg.render()).containsSubsequence(
                "  <g class=\"bond\" data-bond=\"3\">\n", "<circle cx=\"1\"", "  </g>\n", "<circle cx=\"2\"");
        }

        @Test
        void closes_groups_still_open_when_rendered()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.group();
            svg.group();

            assertThat(svg.render()).endsWith("  </g>\n  </g>\n</svg>\n");
        }

        @Test
        void renders_the_same_document_every_time()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.group();
            svg.line(Point.ORIGIN, new Point(1, 1));

            assertThat(svg.render()).isEqualTo(svg.render());
        }

        @Test
        void refuses_to_close_a_group_that_was_never_opened()
        {
            assertThatThrownBy(() -> new SvgWriter(10, 10).close()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void refuses_text_inside_the_group_tag()
        {
            assertThatThrownBy(() -> new SvgWriter(10, 10).group().content("no")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Flushing
    {
        @Test
        void can_not_change_an_element_once_the_next_one_is_started()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            Element first = svg.circle(new Point(1, 1), 1);
            svg.circle(new Point(2, 2), 1);

            assertThatThrownBy(() -> first.fill(RED)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void can_not_change_an_element_once_the_document_is_rendered()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            Element only = svg.circle(new Point(1, 1), 1);
            svg.render();

            assertThatThrownBy(() -> only.fill(RED)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void keeps_elements_in_the_order_they_were_started()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            svg.circle(new Point(1, 1), 1);
            svg.line(Point.ORIGIN, new Point(1, 1));
            svg.circle(new Point(2, 2), 1);

            assertThat(svg.render()).containsSubsequence("<circle cx=\"1\"", "<line", "<circle cx=\"2\"");
        }
    }

    @Nested
    class Gradients
    {
        private final List<SvgWriter.Stop> stops = List.of(new SvgWriter.Stop(0, RED), new SvgWriter.Stop(1, Colour.WHITE));

        @Test
        void defines_a_gradient_before_the_body_and_hands_back_a_reference()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            String paint = svg.linearGradient("bond", new Point(0, 0), new Point(5, 0), stops);
            svg.line(Point.ORIGIN, new Point(5, 0)).stroke(paint);

            assertThat(paint).isEqualTo("url(#bond0)");
            assertThat(svg.render()).isEqualTo("""
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 10 10">
                  <defs>
                    <linearGradient id="bond0" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="5" y2="0">
                      <stop offset="0" stop-color="#ff0000"/>
                      <stop offset="1" stop-color="#ffffff"/>
                    </linearGradient>
                  </defs>
                  <line x1="0" y1="0" x2="5" y2="0" stroke="url(#bond0)"/>
                </svg>
                """);
        }

        @Test
        void keeps_the_names_of_gradients_apart()
        {
            SvgWriter svg = new SvgWriter(10, 10);

            assertThat(svg.linearGradient("g", Point.ORIGIN, new Point(1, 0), stops)).isEqualTo("url(#g0)");
            assertThat(svg.linearGradient("g", Point.ORIGIN, new Point(1, 0), stops)).isEqualTo("url(#g1)");
        }

        @Test
        void refuses_a_gradient_that_goes_nowhere()
        {
            SvgWriter svg = new SvgWriter(10, 10);
            List<SvgWriter.Stop> one = List.of(new SvgWriter.Stop(0, RED));

            assertThatThrownBy(() -> svg.linearGradient("g", Point.ORIGIN, new Point(1, 0), one)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refuses_a_stop_off_the_ends_of_the_gradient()
        {
            assertThatThrownBy(() -> new SvgWriter.Stop(1.1, RED)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
