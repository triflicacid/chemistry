package com.ruben.chemistry.render;

import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.smiles.SmilesParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

class SvgRendererTest
{
    @Nested
    class Document
    {
        @Test
        void is_well_formed_xml()
        {
            String svg = SvgRenderer.render(depict("CCO"));

            assertThatIsWellFormed(svg);
        }

        @Test
        void is_an_svg_element_carrying_the_svg_namespace()
        {
            String svg = SvgRenderer.render(Depiction.empty());

            assertThat(svg).startsWith("<svg ").endsWith("</svg>\n");
            assertThat(svg).contains("xmlns=\"http://www.w3.org/2000/svg\"");
        }

        @Test
        void carries_a_view_box_matching_its_size()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"), RenderStyle.builder().bondLength(50).margin(12).build());

            assertThat(svg).contains("width=\"98\"").contains("height=\"48\"");
            assertThat(svg).contains("viewBox=\"0 0 98 48\"");
        }

        @Test
        void is_still_valid_when_there_is_nothing_to_draw()
        {
            String svg = SvgRenderer.render(Depiction.empty());

            assertThatIsWellFormed(svg);
            assertThat(countOf(svg, "<line")).isZero();
            assertThat(countOf(svg, "<text")).isZero();
        }

        @Test
        void refuses_a_missing_depiction()
        {
            assertThatNullPointerException().isThrownBy(() -> SvgRenderer.render(null));
        }

        @Test
        void refuses_a_missing_style()
        {
            assertThatNullPointerException().isThrownBy(() -> SvgRenderer.render(Depiction.empty(), null));
        }
    }

    @Nested
    class Size
    {
        @Test
        void grows_with_the_bond_length()
        {
            RenderStyle shorter = RenderStyle.builder().bondLength(50).margin(0).build();
            RenderStyle longer = shorter.toBuilder().bondLength(100).build();

            assertThat(widthOf(SvgRenderer.render(twoAtoms("C", "C"), shorter))).isEqualTo(50 + 24);
            assertThat(widthOf(SvgRenderer.render(twoAtoms("C", "C"), longer))).isEqualTo(100 + 24);
        }

        @Test
        void grows_with_the_margin_on_both_sides()
        {
            RenderStyle bare = RenderStyle.builder().bondLength(50).margin(0).build();
            RenderStyle padded = bare.toBuilder().margin(20).build();

            double bareWidth = widthOf(SvgRenderer.render(twoAtoms("C", "C"), bare));
            double paddedWidth = widthOf(SvgRenderer.render(twoAtoms("C", "C"), padded));

            assertThat(paddedWidth - bareWidth).isEqualTo(40);
        }

        @Test
        void leaves_room_for_an_atom_sitting_on_the_very_edge()
        {
            // Both atoms lie on one line, so the drawing has no height of its own; what height it
            // has is the room the symbols themselves need.
            String svg = SvgRenderer.render(twoAtoms("C", "C"), RenderStyle.builder().margin(0).build());

            assertThat(heightOf(svg)).isPositive();
        }
    }

    @Nested
    class Background
    {
        @Test
        void fills_the_whole_document_in_the_chosen_colour()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"),
                RenderStyle.builder().background(Colour.of("#eeeeee")).build());

            assertThat(svg).contains("<rect").contains("fill=\"#eeeeee\"");
        }

        @Test
        void is_left_out_where_no_colour_was_chosen()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"), RenderStyle.builder().background(null).build());

            assertThat(svg).doesNotContain("<rect");
        }
    }

    @Nested
    class Atoms
    {
        @Test
        void are_written_as_their_symbols_in_text_mode()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"), RenderStyle.builder().atomShape(AtomShape.TEXT).build());

            assertThat(countOf(svg, "<text")).isEqualTo(2);
            assertThat(svg).doesNotContain("<circle");
            assertThat(svg).contains(">C</text>").contains(">O</text>");
        }

        @Test
        void are_drawn_as_balls_in_circle_mode()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"), RenderStyle.builder().atomShape(AtomShape.CIRCLE).build());

            assertThat(countOf(svg, "<circle")).isEqualTo(2);
            assertThat(svg).doesNotContain("<text");
        }

        @Test
        void are_written_in_the_colour_their_element_is_given()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"));

            assertThat(svg).contains("fill=\"" + ElementColours.of("O").hex() + "\"");
            assertThat(svg).contains("fill=\"" + ElementColours.of("C").hex() + "\"");
        }

        @Test
        void are_written_in_a_colour_the_style_chose_over_the_standard_one()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"),
                RenderStyle.builder().colour("O", Colour.of("#123456")).build());

            assertThat(svg).contains("fill=\"#123456\"");
            assertThat(svg).doesNotContain("fill=\"" + ElementColours.of("O").hex() + "\"");
        }

        @Test
        void are_drawn_as_a_smaller_ball_where_they_are_hydrogen()
        {
            RenderStyle style = RenderStyle.builder().atomShape(AtomShape.CIRCLE).build();

            String svg = SvgRenderer.render(twoAtoms("C", "H"), style);

            assertThat(svg).contains("r=\"" + (long) style.atomRadius() + "\"");
            assertThat(svg).contains("r=\"" + (long) style.hydrogenRadius() + "\"");
        }

        @Test
        void are_outlined_so_that_a_white_hydrogen_still_shows_up()
        {
            RenderStyle style = RenderStyle.defaults();

            String svg = SvgRenderer.render(twoAtoms("C", "H"), style);

            assertThat(svg).contains("fill=\"#ffffff\"");
            assertThat(svg).contains("stroke=\"" + style.atomOutline().hex() + "\" stroke-width=\"1\" paint-order=\"stroke\"");
        }

        @Test
        void sit_over_the_ends_of_their_bonds()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"));

            assertThat(svg.indexOf("<line")).isLessThan(svg.indexOf("<text"));
        }
    }

    @Nested
    class Labels
    {
        @Test
        void carry_a_mass_number_before_the_symbol()
        {
            String svg = SvgRenderer.render(isotope(13), RenderStyle.builder().isotopes(true).build());

            assertThat(svg).contains(">13</tspan>");
            assertThat(svg.indexOf(">13</tspan>")).isLessThan(svg.indexOf(">C</tspan>"));
        }

        @Test
        void leave_a_mass_number_out_where_the_style_says_to()
        {
            String svg = SvgRenderer.render(isotope(13), RenderStyle.builder().isotopes(false).build());

            assertThat(svg).doesNotContain("<tspan").contains(">C</text>");
        }

        @Test
        void leave_a_mass_number_out_on_a_ball_which_carries_no_writing()
        {
            String svg = SvgRenderer.render(isotope(13),
                RenderStyle.builder().atomShape(AtomShape.CIRCLE).isotopes(true).build());

            assertThat(svg).doesNotContain("<text").doesNotContain("<tspan");
        }

        @Test
        void carry_a_charge_after_the_symbol()
        {
            String svg = SvgRenderer.render(charged(1), RenderStyle.builder().charges(true).build());

            assertThat(svg).contains(">+</tspan>");
        }

        @Test
        void write_how_big_a_charge_of_more_than_one_is()
        {
            String svg = SvgRenderer.render(charged(-2), RenderStyle.builder().charges(true).build());

            assertThat(svg).contains(">2-</tspan>");
        }

        @Test
        void leave_a_charge_out_where_the_style_says_to()
        {
            String svg = SvgRenderer.render(charged(1), RenderStyle.builder().charges(false).build());

            assertThat(svg).doesNotContain("tspan");
        }

        @Test
        void are_written_in_the_chosen_font()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"),
                RenderStyle.builder().fontFamily("Georgia").fontSize(24).build());

            assertThat(svg).contains("font-family=\"Georgia\"").contains("font-size=\"24\"");
        }

        @Test
        void escape_anything_xml_would_read_as_markup()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"),
                RenderStyle.builder().fontFamily("Smith & Sons \"Serif\"").build());

            assertThat(svg).contains("Smith &amp; Sons &quot;Serif&quot;");
            assertThatIsWellFormed(svg);
        }
    }

    @Nested
    class Bonds
    {
        @Test
        void draw_one_line_for_a_single_bond()
        {
            assertThat(countOf(SvgRenderer.render(bonded(BondOrder.SINGLE, BondStyle.PLAIN)), "<line")).isEqualTo(1);
        }

        @Test
        void draw_two_lines_for_a_double_bond()
        {
            assertThat(countOf(SvgRenderer.render(bonded(BondOrder.DOUBLE, BondStyle.PLAIN)), "<line")).isEqualTo(2);
        }

        @Test
        void draw_three_lines_for_a_triple_bond()
        {
            assertThat(countOf(SvgRenderer.render(bonded(BondOrder.TRIPLE, BondStyle.PLAIN)), "<line")).isEqualTo(3);
        }

        @Test
        void draw_an_aromatic_bond_as_one_solid_line_beside_one_broken_one()
        {
            String svg = SvgRenderer.render(bonded(BondOrder.AROMATIC, BondStyle.PLAIN));

            assertThat(countOf(svg, "<line")).isEqualTo(2);
            assertThat(countOf(svg, "stroke-dasharray")).isEqualTo(1);
        }

        @Test
        void draw_a_bond_coming_out_of_the_page_as_a_solid_wedge()
        {
            String svg = SvgRenderer.render(bonded(BondOrder.SINGLE, BondStyle.WEDGE));

            assertThat(countOf(svg, "<polygon")).isEqualTo(1);
            assertThat(countOf(svg, "<line")).isZero();
        }

        @Test
        void narrow_a_wedge_towards_its_stereocentre()
        {
            String svg = SvgRenderer.render(bonded(BondOrder.SINGLE, BondStyle.WEDGE),
                RenderStyle.builder().margin(0).build());
            List<Point> corners = polygonOf(svg);

            // The first corner is the point of the wedge, which stands alone; the other two are the wide end.
            assertThat(corners).hasSize(3);
            assertThat(corners.get(1).distanceTo(corners.get(2))).isEqualTo(RenderStyle.defaults().wedgeWidth());
            assertThat(corners.getFirst().x()).isLessThan(corners.get(1).x());
        }

        @Test
        void draw_a_bond_going_into_the_page_as_a_ladder_of_marks()
        {
            String svg = SvgRenderer.render(bonded(BondOrder.SINGLE, BondStyle.HASH));

            assertThat(svg).doesNotContain("<polygon");
            assertThat(countOf(svg, "<line")).isGreaterThan(3);
        }

        @Test
        void are_drawn_in_the_chosen_colour_and_thickness()
        {
            String svg = SvgRenderer.render(bonded(BondOrder.SINGLE, BondStyle.PLAIN),
                RenderStyle.builder().bondColour(Colour.of("#abcdef")).bondWidth(5).build());

            assertThat(svg).contains("stroke=\"#abcdef\" stroke-width=\"5\"");
        }

        @Test
        void are_held_off_an_atom_so_as_not_to_run_under_its_symbol()
        {
            RenderStyle style = RenderStyle.builder().bondLength(50).margin(0).build();

            String svg = SvgRenderer.render(twoAtoms("C", "C"), style);

            // The line begins a symbol's clearance to the right of the atom, not on top of it.
            assertThat(lineStartOf(svg).x() - labelX(svg, "C")).isEqualTo(style.fontSize() / 2 + style.labelPadding());
        }
    }

    @Nested
    class Gradients
    {
        @Test
        void are_left_out_unless_they_were_asked_for()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"), RenderStyle.builder().bondColour(Colour.of("#abcdef")).build());

            assertThat(svg).doesNotContain("<defs").doesNotContain("linearGradient");
            assertThat(svg).contains("stroke=\"#abcdef\"");
        }

        @Test
        void paint_a_bond_between_the_colours_of_the_atoms_at_its_ends()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"), gradient());

            assertThat(svg).contains("<defs>").contains("<linearGradient");
            assertThat(svg).contains("stroke=\"url(#bond0)\"");
            assertThat(stopsOf(svg)).first().isEqualTo(ElementColours.of("C").hex());
            assertThat(stopsOf(svg)).last().isEqualTo(ElementColours.of("O").hex());
        }

        @Test
        void turn_quickly_at_each_end_and_sit_nearly_still_across_the_middle()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"), gradient());
            List<Double> offsets = offsetsOf(svg);

            assertThat(offsets).containsExactly(0.0, 0.25, 0.75, 1.0);

            List<String> stops = stopsOf(svg);
            Colour from = Colour.of(stops.get(1));
            Colour to = Colour.of(stops.get(2));
            Colour ends = ElementColours.of("C");

            // By a quarter of the way along, most of the change away from the end colour has happened...
            assertThat(Math.abs(from.red() - ends.red())).isGreaterThan(Math.abs(to.red() - from.red()));
            // ...and the middle half barely moves at all.
            assertThat(Math.abs(to.red() - from.red())).isLessThan(Math.abs(ends.red() - Colour.of(stops.getLast()).red()) / 4);
        }

        @Test
        void run_the_length_of_the_bond_they_paint()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"), gradient());
            String definition = svg.substring(svg.indexOf("<linearGradient"));

            assertThat(definition).contains("gradientUnits=\"userSpaceOnUse\"");
            assertThat(attribute(definition, "x1")).isLessThan(attribute(definition, "x2"));
            assertThat(attribute(definition, "y1")).isEqualTo(attribute(definition, "y2"));
        }

        @Test
        void are_not_written_where_both_ends_are_the_same_colour()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "C"), gradient());

            assertThat(svg).doesNotContain("linearGradient");
            assertThat(svg).contains("stroke=\"" + ElementColours.of("C").hex() + "\"");
        }

        @Test
        void are_named_apart_so_that_each_bond_keeps_its_own()
        {
            String svg = SvgRenderer.render(depict("OCN"), gradient());

            assertThat(countOf(svg, "<linearGradient")).isGreaterThan(1);
            assertThat(countOf(svg, "id=\"bond0\"")).isEqualTo(1);
            assertThat(countOf(svg, "id=\"bond1\"")).isEqualTo(1);
            assertThatIsWellFormed(svg);
        }

        @Test
        void fill_a_wedge_as_well_as_a_plain_line()
        {
            Depiction depiction = new Depiction(
                List.of(atom(0, "C", Point.ORIGIN), atom(1, "O", new Point(1, 0))),
                List.of(new BondPlacement(0, 1, BondOrder.SINGLE, BondStyle.WEDGE)));

            String svg = SvgRenderer.render(depiction, gradient());

            assertThat(svg).contains("<polygon").contains("fill=\"url(#bond0)\"");
        }

        @Test
        void leave_a_whole_molecule_well_formed()
        {
            assertThatIsWellFormed(SvgRenderer.render(depict("CC(=O)Cl"), gradient()));
        }

        /**
         * @return the default style, with gradient bonds turned on
         */
        private static RenderStyle gradient()
        {
            return RenderStyle.builder().bondGradient(true).build();
        }

        /**
         * @param svg a document
         * @return the colour of every stop of its first gradient, in order
         */
        private static List<String> stopsOf(String svg)
        {
            return valuesOf(firstGradient(svg), "stop-color=\"").stream().toList();
        }

        /**
         * @param svg a document
         * @return how far along its first gradient each stop sits, in order
         */
        private static List<Double> offsetsOf(String svg)
        {
            return valuesOf(firstGradient(svg), "offset=\"").stream().map(Double::parseDouble).toList();
        }

        /**
         * @param svg a document
         * @return the first gradient it defines
         */
        private static String firstGradient(String svg)
        {
            int from = svg.indexOf("<linearGradient");
            return svg.substring(from, svg.indexOf("</linearGradient>", from));
        }

        /**
         * @param text some mark-up
         * @param attribute an attribute, up to and including its opening quote
         * @return every value it takes, in order
         */
        private static List<String> valuesOf(String text, String attribute)
        {
            List<String> values = new ArrayList<>();
            for (int at = text.indexOf(attribute); at >= 0; at = text.indexOf(attribute, at + 1))
            {
                int from = at + attribute.length();
                values.add(text.substring(from, text.indexOf('"', from)));
            }
            return values;
        }
    }

    @Nested
    class Vertices
    {
        @Test
        void are_not_written_out()
        {
            String svg = SvgRenderer.render(vertexAnd("O"));

            assertThat(countOf(svg, "<text")).isEqualTo(1);
            assertThat(svg).contains(">O<").doesNotContain(">C<");
        }

        @Test
        void let_their_bonds_run_right_up_to_them()
        {
            RenderStyle style = RenderStyle.builder().bondLength(50).margin(0).build();

            String svg = SvgRenderer.render(vertexAnd("C"), style);

            // both ends are bare, so the line is the whole bond
            assertThat(svg).contains("<line");
            assertThat(lineLengthOf(svg)).isEqualTo(50);
        }

        @Test
        void are_still_drawn_as_balls_where_the_style_asks_for_balls()
        {
            String svg = SvgRenderer.render(vertexAnd("C"), RenderStyle.builder().atomShape(AtomShape.CIRCLE).build());

            assertThat(countOf(svg, "<circle")).isEqualTo(2);
        }

        @Test
        void close_a_single_bond_up_against_the_double_bond_it_meets()
        {
            // C=C-C, with the chain bending through the middle vertex
            Depiction chain = new Depiction(
                List.of(
                    atom(0, "C", Point.ORIGIN).asVertex(true),
                    atom(1, "C", new Point(1, 0)).asVertex(true),
                    atom(2, "C", new Point(1.5, Math.sqrt(3) / 2)).asVertex(true)),
                List.of(
                    new BondPlacement(0, 1, BondOrder.DOUBLE, BondStyle.PLAIN),
                    new BondPlacement(1, 2, BondOrder.SINGLE, BondStyle.PLAIN)));
            RenderStyle style = RenderStyle.builder().bondLength(50).margin(0).bondGap(6).build();

            String svg = SvgRenderer.render(chain, style);

            // the single carries on past the vertex until it has crossed both lines of the double
            double reach = (style.bondGap() / 2) / (Math.sqrt(3) / 2);
            assertThat(lastLineLengthOf(svg)).isCloseTo(50 + reach, within(0.01));
        }

        @Test
        void leave_two_single_bonds_meeting_at_a_point_alone()
        {
            Depiction chain = new Depiction(
                List.of(
                    atom(0, "C", Point.ORIGIN).asVertex(true),
                    atom(1, "C", new Point(1, 0)).asVertex(true),
                    atom(2, "C", new Point(1.5, Math.sqrt(3) / 2)).asVertex(true)),
                List.of(
                    new BondPlacement(0, 1, BondOrder.SINGLE, BondStyle.PLAIN),
                    new BondPlacement(1, 2, BondOrder.SINGLE, BondStyle.PLAIN)));

            String svg = SvgRenderer.render(chain, RenderStyle.builder().bondLength(50).margin(0).build());

            assertThat(lastLineLengthOf(svg)).isCloseTo(50, within(0.01));
        }

        /**
         * @param svg a document
         * @return how long the last line it holds is
         */
        private static double lastLineLengthOf(String svg)
        {
            String line = svg.substring(svg.lastIndexOf("<line"));
            return new Point(attribute(line, "x1"), attribute(line, "y1"))
                .distanceTo(new Point(attribute(line, "x2"), attribute(line, "y2")));
        }

        /**
         * @param second the element on the right, which is written out as usual
         * @return a bare vertex bonded to it
         */
        private static Depiction vertexAnd(String second)
        {
            return new Depiction(
                List.of(atom(0, "C", Point.ORIGIN).asVertex(true), atom(1, second, new Point(1, 0)).asVertex(second.equals("C"))),
                List.of(new BondPlacement(0, 1, BondOrder.SINGLE, BondStyle.PLAIN)));
        }

        /**
         * @param svg a document
         * @return how long the first line it holds is
         */
        private static double lineLengthOf(String svg)
        {
            String line = svg.substring(svg.indexOf("<line"));
            return new Point(attribute(line, "x1"), attribute(line, "y1"))
                .distanceTo(new Point(attribute(line, "x2"), attribute(line, "y2")));
        }
    }

    @Nested
    class Axes
    {
        @Test
        void put_an_atom_that_sits_higher_further_up_the_page()
        {
            Depiction depiction = new Depiction(
                List.of(atom(0, "C", new Point(0, 0)), atom(1, "O", new Point(0, 1))),
                List.of(new BondPlacement(0, 1, BondOrder.SINGLE, BondStyle.PLAIN)));

            String svg = SvgRenderer.render(depiction);

            assertThat(labelY(svg, "C")).isGreaterThan(labelY(svg, "O"));
        }

        @Test
        void keep_an_atom_that_sits_further_right_further_right_on_the_page()
        {
            String svg = SvgRenderer.render(twoAtoms("C", "O"));

            assertThat(labelX(svg, "C")).isLessThan(labelX(svg, "O"));
        }
    }

    @Nested
    class WholeMolecules
    {
        @Test
        void draw_every_atom_of_an_explicit_diagram()
        {
            String svg = SvgRenderer.render(depict("CCO"));

            // Three heavy atoms and the six hydrogens of ethanol.
            assertThat(countOf(svg, "<text")).isEqualTo(9);
            assertThatIsWellFormed(svg);
        }

        @Test
        void draw_a_stereocentre_with_a_bond_leaving_the_plane_of_the_page()
        {
            Depiction depiction = depict("N[C@@H](C)C(=O)O");
            String svg = SvgRenderer.render(depiction);

            assertThat(depiction.bonds()).anySatisfy(bond -> assertThat(bond.style().isStereo()).isTrue());
            assertThat(countOf(svg, "<polygon") + countOf(svg, "<line")).isGreaterThan(depiction.bonds().size());
            assertThatIsWellFormed(svg);
        }

        @Test
        void draw_a_double_bond_as_a_pair_of_lines()
        {
            String svg = SvgRenderer.render(depict("CC=O"));

            assertThatIsWellFormed(svg);
            // Five single bonds, to four hydrogens and between the carbons, and the carbonyl's two lines.
            assertThat(countOf(svg, "<line")).isEqualTo(7);
        }
    }

    @Nested
    class Metadata
    {
        @Test
        void is_absent_unless_asked_for()
        {
            String svg = SvgRenderer.render(depict("CCO"));

            assertThat(svg).doesNotContain("data-");
        }

        @Test
        void traces_each_atom_back_to_the_molecule()
        {
            String svg = SvgRenderer.render(depict("[13CH3]O"), RenderStyle.builder().metadata(true).build());

            assertThatIsWellFormed(svg);
            assertThat(svg).contains("data-atom=\"0\"").contains("data-element=\"C\"").contains("data-isotope=\"13\"");
            assertThat(svg).contains("data-atom=\"1\"").contains("data-element=\"O\"");
            assertThat(svg).contains("data-implicit=\"true\"");
        }

        @Test
        void traces_each_bond_back_to_the_molecule()
        {
            String svg = SvgRenderer.render(depict("C=CO"), RenderStyle.builder().metadata(true).build());

            assertThatIsWellFormed(svg);
            assertThat(svg)
                    .contains("data-bond=\"0\"")
                    .contains("data-order=\"double\"")
                    .contains("data-first-atom=\"0\"")
                    .contains("data-second-atom=\"1\"");
            assertThat(svg).contains("data-bond=\"1\"").contains("data-order=\"single\"");
        }

        @Test
        void leaves_out_the_source_of_a_made_up_hydrogen()
        {
            String svg = SvgRenderer.render(depict("C"), RenderStyle.builder().metadata(true).build());

            assertThat(svg).doesNotContain("data-bond=").doesNotContain("data-second-atom=");
            assertThat(svg).contains("data-first-atom=\"0\"");
            assertThat(countOf(svg, "class=\"bond\"")).isEqualTo(4);
        }

        @Test
        void numbers_each_disconnected_fragment()
        {
            String svg = SvgRenderer.render(depict("CC.O"), RenderStyle.builder().metadata(true).build());

            assertThat(svg).contains("data-atom=\"0\" data-placement=\"0\" data-fragment=\"0\"");
            assertThat(svg).contains("data-element=\"O\"");
            assertThat(svg).containsPattern("data-atom=\"2\" data-placement=\"\\d+\" data-fragment=\"1\"");
        }
    }

    /**
     * @param smiles a molecule
     * @return it laid out in the default way
     */
    private static Depiction depict(String smiles)
    {
        return MoleculeLayout.layout(new SmilesParser(smiles).parse()).depiction();
    }

    /**
     * @param first the element on the left
     * @param second the element on the right
     * @return the two of them, one bond apart, singly bonded
     */
    private static Depiction twoAtoms(String first, String second)
    {
        return new Depiction(
            List.of(atom(0, first, Point.ORIGIN), atom(1, second, new Point(1, 0))),
            List.of(new BondPlacement(0, 1, BondOrder.SINGLE, BondStyle.PLAIN)));
    }

    /**
     * @param order how many times the two atoms are bonded
     * @param style how the bond is drawn
     * @return two carbons bonded that way
     */
    private static Depiction bonded(BondOrder order, BondStyle style)
    {
        return new Depiction(
            List.of(atom(0, "C", Point.ORIGIN), atom(1, "C", new Point(1, 0))),
            List.of(new BondPlacement(0, 1, order, style)));
    }

    /**
     * @param mass a mass number
     * @return one carbon labelled with it
     */
    private static Depiction isotope(int mass)
    {
        return new Depiction(
            List.of(AtomPlacement.of(0, "C", OptionalInt.of(mass), 0, false, OptionalInt.of(0), Point.ORIGIN)),
            List.of());
    }

    /**
     * @param charge a net formal charge
     * @return one nitrogen carrying it
     */
    private static Depiction charged(int charge)
    {
        return new Depiction(
            List.of(AtomPlacement.of(0, "N", OptionalInt.empty(), charge, false, OptionalInt.of(0), Point.ORIGIN)),
            List.of());
    }

    /**
     * @param index the atom's index
     * @param element its element
     * @param position where it sits
     * @return a plain placement of it
     */
    private static AtomPlacement atom(int index, String element, Point position)
    {
        return AtomPlacement.of(index, element, OptionalInt.empty(), 0, false, OptionalInt.of(index), position);
    }

    /**
     * @param svg a document
     * @throws AssertionError if it is not well-formed XML
     */
    private static void assertThatIsWellFormed(String svg)
    {
        assertThatCode(() ->
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(svg)));
        }).doesNotThrowAnyException();
    }

    /**
     * @param svg a document
     * @return how many pixels wide it is
     */
    private static double widthOf(String svg)
    {
        return attribute(svg, "width");
    }

    /**
     * @param svg a document
     * @return how many pixels tall it is
     */
    private static double heightOf(String svg)
    {
        return attribute(svg, "height");
    }

    /**
     * @param svg a document
     * @param name an attribute's name
     * @return the first value that attribute takes, as a number
     */
    private static double attribute(String svg, String name)
    {
        int at = svg.indexOf(name + "=\"");
        int from = at + name.length() + 2;
        return Double.parseDouble(svg.substring(from, svg.indexOf('"', from)));
    }

    /**
     * @param svg a document
     * @return where the first line it holds begins
     */
    private static Point lineStartOf(String svg)
    {
        String line = svg.substring(svg.indexOf("<line"));
        return new Point(attribute(line, "x1"), attribute(line, "y1"));
    }

    /**
     * @param svg a document
     * @return the corners of the first polygon it holds
     */
    private static List<Point> polygonOf(String svg)
    {
        int at = svg.indexOf("points=\"") + "points=\"".length();
        String points = svg.substring(at, svg.indexOf('"', at));
        return List.of(points.split(" ")).stream()
            .map(corner -> corner.split(","))
            .map(pair -> new Point(Double.parseDouble(pair[0]), Double.parseDouble(pair[1])))
            .toList();
    }

    /**
     * @param svg a document
     * @param element a symbol written in it
     * @return how far across the page that symbol is written
     */
    private static double labelX(String svg, String element)
    {
        return attribute(textOf(svg, element), "x");
    }

    /**
     * @param svg a document
     * @param element a symbol written in it
     * @return how far down the page that symbol is written
     */
    private static double labelY(String svg, String element)
    {
        return attribute(textOf(svg, element), "y");
    }

    /**
     * @param svg a document
     * @param element a symbol written in it
     * @return the text element writing it
     */
    private static String textOf(String svg, String element)
    {
        int ends = svg.indexOf('>' + element + "</text>");
        return svg.substring(svg.lastIndexOf("<text", ends));
    }

    /**
     * @param text some text
     * @param piece the piece to look for
     * @return how many times the piece appears in it
     */
    private static int countOf(String text, String piece)
    {
        int found = 0;
        for (int at = text.indexOf(piece); at >= 0; at = text.indexOf(piece, at + piece.length()))
        {
            found++;
        }
        return found;
    }
}
