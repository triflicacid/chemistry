package com.ruben.chemistry.notation;

import com.ruben.chemistry.core.Atom;
import com.ruben.chemistry.core.Bond;
import com.ruben.chemistry.util.Span;

import java.util.List;
import java.util.Map;

/**
 * One molecule as it was written, and where each of its atoms and bonds was written in it.
 *
 * @param smiles the notation
 * @param atomSpans where each atom is written in it, by atom reference
 * @param bondSpans where each bond is written in it, by bond reference, one span per end that wrote a symbol and an empty list for a bond written with none
 */
public record Notation(String smiles, Map<Atom, Span> atomSpans, Map<Bond, List<Span>> bondSpans)
{
    /**
     * @param bond a bond of the molecule this was written for
     * @return the symbols written for it, in the order the ends were written, each stripped of the
     *     ring-closure digits that may follow it and empty where that end wrote none
     */
    public List<String> symbolsOf(Bond bond)
    {
        return bondSpans.getOrDefault(bond, List.of()).stream()
            .map(this::symbolAt)
            .toList();
    }

    /**
     * @param span where one end of a bond is written
     * @return the bond symbol there, without the ring-closure digits that may follow it
     */
    private String symbolAt(Span span)
    {
        String written = smiles.substring(span.start(), span.end());
        int symbol = 0;
        while (symbol < written.length() && !Character.isDigit(written.charAt(symbol)) && written.charAt(symbol) != '%')
        {
            symbol++;
        }
        return written.substring(0, symbol);
    }
}
