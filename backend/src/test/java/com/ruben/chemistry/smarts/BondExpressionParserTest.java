package com.ruben.chemistry.smarts;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.core.BondDirection;
import com.ruben.chemistry.core.BondOrder;
import com.ruben.chemistry.core.Molecule;
import com.ruben.chemistry.notation.InvalidNotationException;
import com.ruben.chemistry.query.BondPredicate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BondExpressionParserTest
{
    private static BondPredicate parse(Token... tokens)
    {
        return result(tokens).predicate();
    }

    private static BondExpressionParser.Result result(Token... tokens)
    {
        return new BondExpressionParser(new ListCursor(List.of(tokens)), "source").parse();
    }

    private static Token bond(String symbol)
    {
        return new Token(TokenType.BOND, symbol, 0);
    }

    private static final Token AND = new Token(TokenType.AND, "&", 0);
    private static final Token OR = new Token(TokenType.OR, ",", 0);
    private static final Token SEMI = new Token(TokenType.SEMI, ";", 0);
    private static final Token NOT = new Token(TokenType.NOT, "!", 0);

    private static Molecule moleculeWith(Atom first, Atom second, BondOrder order)
    {
        return new Molecule.Builder().addAtoms(first, second).addBond(first, second, order).build();
    }

    @Nested
    class Primitives
    {
        @Test
        void order_symbols_match_exactly_that_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = moleculeWith(c, o, BondOrder.DOUBLE);
            Bond bond = molecule.bondBetween(c, o).orElseThrow();

            assertThat(parse(bond("=")).matches(molecule, bond)).isTrue();
            assertThat(parse(bond("-")).matches(molecule, bond)).isFalse();
        }

        @Test
        void tilde_matches_any_order()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = moleculeWith(c, o, BondOrder.TRIPLE);
            Bond bond = molecule.bondBetween(c, o).orElseThrow();

            assertThat(parse(bond("~")).matches(molecule, bond)).isTrue();
        }

        @Test
        void at_sign_matches_a_ring_bond()
        {
            // cyclopropane: C1CC1
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a, b, c)
                .addBond(a, b, BondOrder.SINGLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, a, BondOrder.SINGLE)
                .build();
            Bond ringBond = molecule.bondBetween(a, b).orElseThrow();

            assertThat(parse(bond("@")).matches(molecule, ringBond)).isTrue();
        }

        @Test
        void not_at_sign_matches_a_chain_bond()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule molecule = moleculeWith(c, o, BondOrder.SINGLE);
            Bond chainBond = molecule.bondBetween(c, o).orElseThrow();

            assertThat(parse(NOT, bond("@")).matches(molecule, chainBond)).isTrue();
        }

        @Test
        void a_directional_symbol_implies_a_single_bond()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule single = moleculeWith(c, o, BondOrder.SINGLE);
            Molecule double_ = moleculeWith(c, o, BondOrder.DOUBLE);

            assertThat(parse(bond("/")).matches(single, single.bondBetween(c, o).orElseThrow())).isTrue();
            assertThat(parse(bond("/")).matches(double_, double_.bondBetween(c, o).orElseThrow())).isFalse();
        }

        @Test
        void a_directional_symbol_is_reported_separately_from_the_predicate()
        {
            assertThat(result(bond("/")).direction()).contains(BondDirection.SLASH);
            assertThat(result(bond("\\")).direction()).contains(BondDirection.BACKSLASH);
            assertThat(result(bond("-")).direction()).isEmpty();
        }

        @Test
        void a_directional_symbol_combined_with_another_primitive_still_produces_a_working_predicate()
        {
            // cyclopropane: a single bond that's also a ring bond
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Molecule ring = new Molecule.Builder()
                .addAtoms(a, b, c)
                .addBond(a, b, BondOrder.SINGLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, a, BondOrder.SINGLE)
                .build();
            Bond ringBond = ring.bondBetween(a, b).orElseThrow();

            Atom x = Atom.organic("C");
            Atom y = Atom.organic("C");
            Molecule chain = moleculeWith(x, y, BondOrder.SINGLE);
            Bond chainBond = chain.bondBetween(x, y).orElseThrow();

            BondExpressionParser.Result directionalRingBond = result(bond("/"), bond("@"));

            assertThat(directionalRingBond.direction()).contains(BondDirection.SLASH);
            assertThat(directionalRingBond.predicate().matches(ring, ringBond)).isTrue();
            assertThat(directionalRingBond.predicate().matches(chain, chainBond)).isFalse();
        }
    }

    @Nested
    class LogicalCombination
    {
        @Test
        void or_requires_either_side()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule single = moleculeWith(c, o, BondOrder.SINGLE);
            Molecule double_ = moleculeWith(c, o, BondOrder.DOUBLE);
            Molecule triple = moleculeWith(c, o, BondOrder.TRIPLE);

            BondPredicate singleOrDouble = parse(bond("-"), OR, bond("="));

            assertThat(singleOrDouble.matches(single, single.bondBetween(c, o).orElseThrow())).isTrue();
            assertThat(singleOrDouble.matches(double_, double_.bondBetween(c, o).orElseThrow())).isTrue();
            assertThat(singleOrDouble.matches(triple, triple.bondBetween(c, o).orElseThrow())).isFalse();
        }

        @Test
        void and_requires_both_sides()
        {
            // cyclopropane: a ring bond that's also single
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a, b, c)
                .addBond(a, b, BondOrder.SINGLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, a, BondOrder.SINGLE)
                .build();
            Bond ringBond = molecule.bondBetween(a, b).orElseThrow();

            BondPredicate singleRingBond = parse(bond("-"), AND, bond("@"));

            assertThat(singleRingBond.matches(molecule, ringBond)).isTrue();
        }

        @Test
        void implicit_and_behaves_like_explicit_and()
        {
            Atom a = Atom.organic("C");
            Atom b = Atom.organic("C");
            Atom c = Atom.organic("C");
            Molecule molecule = new Molecule.Builder()
                .addAtoms(a, b, c)
                .addBond(a, b, BondOrder.SINGLE)
                .addBond(b, c, BondOrder.SINGLE)
                .addBond(c, a, BondOrder.SINGLE)
                .build();
            Bond ringBond = molecule.bondBetween(a, b).orElseThrow();

            BondPredicate singleRingBond = parse(bond("-"), bond("@"));

            assertThat(singleRingBond.matches(molecule, ringBond)).isTrue();
        }

        @Test
        void semi_also_combines_with_and_but_binds_looser_than_or()
        {
            // "-,=;@" means (single or double) and ring-bond
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule chain = moleculeWith(c, o, BondOrder.SINGLE); // single, but not a ring bond

            BondPredicate expression = parse(bond("-"), OR, bond("="), SEMI, bond("@"));

            assertThat(expression.matches(chain, chain.bondBetween(c, o).orElseThrow())).isFalse();
        }

        @Test
        void not_negates_the_single_following_primitive()
        {
            Atom c = Atom.organic("C");
            Atom o = Atom.organic("O");
            Molecule single = moleculeWith(c, o, BondOrder.SINGLE);
            Molecule double_ = moleculeWith(c, o, BondOrder.DOUBLE);

            BondPredicate notSingle = parse(NOT, bond("-"));

            assertThat(notSingle.matches(single, single.bondBetween(c, o).orElseThrow())).isFalse();
            assertThat(notSingle.matches(double_, double_.bondBetween(c, o).orElseThrow())).isTrue();
        }
    }

    @Nested
    class Errors
    {
        @Test
        void throws_for_an_empty_expression()
        {
            assertThatThrownBy(() -> new BondExpressionParser(new ListCursor(List.of()), "irrelevant").parse())
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("must not be empty");
        }

        @Test
        void throws_for_a_trailing_unconsumed_token()
        {
            assertThatThrownBy(() -> parse(bond("-"), bond("="), OR))
                .isInstanceOf(InvalidNotationException.class);
        }

        @Test
        void throws_for_not_with_nothing_following_it()
        {
            assertThatThrownBy(() -> parse(NOT))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Expected a bond primitive");
        }

        @Test
        void throws_for_a_token_that_is_not_a_bond_primitive()
        {
            assertThatThrownBy(() -> parse(new Token(TokenType.ELEMENT, "C", 0)))
                .isInstanceOf(InvalidNotationException.class)
                .hasMessageContaining("Unexpected token");
        }
    }
}
