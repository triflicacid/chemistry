package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.space.ElementData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads basis sets written in the Gaussian94 format that the Basis Set Exchange produces.
 */
public final class BasisSetParser
{
    private static final String SHELL_LETTERS = "SPDFGHI";
    private static final String CHANNEL_LETTERS = "spdfghi";

    private BasisSetParser()
    {
    }

    /**
     * @param text the contents of a basis-set file
     * @return every element in the file
     * @throws IllegalArgumentException if the text is malformed; the message gives the line number
     */
    public static Map<String, ElementBasis> parse(String text)
    {
        List<Line> lines = significantLines(text);
        Map<String, List<Shell>> shells = new LinkedHashMap<>();
        Map<String, EffectiveCorePotential> potentials = new LinkedHashMap<>();
        int i = 0;
        while (i < lines.size())
        {
            Line line = lines.get(i);
            // each element starts with a line of its symbol and 0, then holds either its shells, closed by a line of
            // ****, or its core potential, which runs on until the next element
            if (line.text().equals("****"))
            {
                i++;
                continue;
            }
            if (!isElementHeader(line))
            {
                throw line.error("expected an element header, found '" + line.text() + "'");
            }
            String symbol = symbol(line);
            i++;
            if (i < lines.size() && lines.get(i).tokens()[0].toUpperCase().endsWith("-ECP"))
            {
                int[] next = {i};
                potentials.put(symbol, parsePotential(lines, next, symbol));
                i = next[0];
            }
            else
            {
                int[] next = {i};
                shells.put(symbol, parseShells(lines, next));
                i = next[0];
            }
        }

        Map<String, ElementBasis> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Shell>> entry : shells.entrySet())
        {
            String symbol = entry.getKey();
            result.put(symbol, new ElementBasis(symbol, ElementData.numberOf(symbol), entry.getValue(), potentials.get(symbol)));
        }
        for (String symbol : potentials.keySet())
        {
            if (!shells.containsKey(symbol))
            {
                throw new IllegalArgumentException("core potential for " + symbol + " has no shells");
            }
        }
        return result;
    }

    private static List<Shell> parseShells(List<Line> lines, int[] position)
    {
        List<Shell> shells = new ArrayList<>();
        int i = position[0];
        while (i < lines.size() && !lines.get(i).text().equals("****"))
        {
            // a shell line is its type, its number of primitives and a scale factor, and is followed by one line per primitive
            Line header = lines.get(i);
            String[] tokens = header.tokens();
            if (tokens.length != 3)
            {
                throw header.error("expected a shell line, found '" + header.text() + "'");
            }
            String type = tokens[0].toUpperCase();

            // SP is a combined shell: each primitive line has an exponent and two coefficients, one for the s function
            // and one for the p function that share it
            boolean combined = type.equals("SP");
            if (!combined && (type.length() != 1 || SHELL_LETTERS.indexOf(type.charAt(0)) < 0))
            {
                throw header.error("unknown shell type '" + tokens[0] + "'");
            }
            int count = integer(header, tokens[1]);
            if (count < 1)
            {
                throw header.error("a shell needs at least one primitive");
            }
            // the scale factor multiplies every coefficient, and the files this reads always write 1
            if (number(header, tokens[2]) != 1.0)
            {
                throw header.error("shell scale factors other than 1 are not supported");
            }
            if (i + count >= lines.size())
            {
                throw header.error("shell has fewer than " + count + " primitive lines");
            }

            List<GaussianPrimitive> first = new ArrayList<>();
            List<GaussianPrimitive> second = new ArrayList<>();
            for (int k = 1; k <= count; k++)
            {
                Line row = lines.get(i + k);
                String[] fields = row.tokens();
                if (fields.length != (combined ? 3 : 2))
                {
                    throw row.error("expected " + (combined ? 3 : 2) + " numbers, found '" + row.text() + "'");
                }
                double exponent = number(row, fields[0]);
                first.add(primitive(row, exponent, number(row, fields[1])));
                if (combined)
                {
                    second.add(primitive(row, exponent, number(row, fields[2])));
                }
            }
            if (combined)
            {
                shells.add(new Shell(0, first));
                shells.add(new Shell(1, second));
            }
            else
            {
                shells.add(new Shell(SHELL_LETTERS.indexOf(type.charAt(0)), first));
            }
            i += count + 1;
        }
        if (i >= lines.size())
        {
            throw new IllegalArgumentException("element block is missing its **** terminator");
        }
        position[0] = i + 1;
        return shells;
    }

    private static EffectiveCorePotential parsePotential(List<Line> lines, int[] position, String symbol)
    {
        // the header is SYMBOL-ECP L N: L is the highest angular momentum the potential has a channel for and N is how many core electrons it replaces
        int i = position[0];
        Line header = lines.get(i);
        String[] tokens = header.tokens();
        if (tokens.length != 3 || !tokens[0].equalsIgnoreCase(symbol + "-ECP"))
        {
            throw header.error("expected '" + symbol + "-ECP L N', found '" + header.text() + "'");
        }
        int maxL = integer(header, tokens[1]);
        int core = integer(header, tokens[2]);
        if (maxL < 0 || core < 1)
        {
            throw header.error("invalid core potential size");
        }
        i++;

        // L + 1 channels follow: the local one first, then the semilocal ones for angular momentum 0 upwards.
        // A channel is a header (like "p potential"), a count of terms, and one "power exponent coefficient" line per term
        EcpChannel local = null;
        List<EcpChannel> projectors = new ArrayList<>();
        for (int channel = 0; channel <= maxL; channel++)
        {
            if (i + 1 >= lines.size())
            {
                throw header.error("core potential is missing channels");
            }
            Line title = lines.get(i);
            String[] words = title.tokens();
            if (words.length != 2 || !words[1].equals("potential"))
            {
                throw title.error("expected a channel header such as 'p potential', found '" + title.text() + "'");
            }
            int expectedLetter = channel == 0 ? maxL : channel - 1;
            int l = channelLetter(title, words[0], channel == 0, expectedLetter, maxL);
            int count = integer(lines.get(i + 1), lines.get(i + 1).text());
            if (count < 1 || i + 1 + count >= lines.size())
            {
                throw title.error("channel has a missing or invalid term count");
            }
            List<EcpTerm> terms = new ArrayList<>();
            for (int k = 0; k < count; k++)
            {
                Line row = lines.get(i + 2 + k);
                String[] fields = row.tokens();
                if (fields.length != 3)
                {
                    throw row.error("expected 'power exponent coefficient', found '" + row.text() + "'");
                }
                terms.add(term(row, integer(row, fields[0]), number(row, fields[1]), number(row, fields[2])));
            }
            EcpChannel parsed = new EcpChannel(l, terms);
            if (channel == 0)
            {
                local = parsed;
            }
            else
            {
                projectors.add(parsed);
            }
            i += 2 + count;
        }
        position[0] = i;
        return new EffectiveCorePotential(core, maxL, local, projectors);
    }

    /**
     * @return the angular momentum a channel header's letters name
     */
    private static int channelLetter(Line title, String letters, boolean local, int expected, int maxL)
    {
        String[] parts = letters.split("-");
        int first = parts[0].length() == 1 ? CHANNEL_LETTERS.indexOf(parts[0].charAt(0)) : -1;
        if (local)
        {
            if (parts.length != 1 || first != maxL)
            {
                throw title.error("expected the local channel '" + CHANNEL_LETTERS.charAt(maxL) + " potential', found '" + title.text() + "'");
            }
            return first;
        }
        int second = parts.length == 2 && parts[1].length() == 1 ? CHANNEL_LETTERS.indexOf(parts[1].charAt(0)) : -1;
        if (first != expected || second != maxL)
        {
            throw title.error("expected '" + CHANNEL_LETTERS.charAt(expected) + "-" + CHANNEL_LETTERS.charAt(maxL) + " potential', found '" + title.text() + "'");
        }
        return first;
    }

    private static GaussianPrimitive primitive(Line line, double exponent, double coefficient)
    {
        try
        {
            return new GaussianPrimitive(exponent, coefficient);
        }
        catch (IllegalArgumentException exception)
        {
            throw line.error(exception.getMessage());
        }
    }

    private static EcpTerm term(Line line, int power, double exponent, double coefficient)
    {
        try
        {
            return new EcpTerm(power, exponent, coefficient);
        }
        catch (IllegalArgumentException exception)
        {
            throw line.error(exception.getMessage());
        }
    }

    private static boolean isElementHeader(Line line)
    {
        String[] tokens = line.tokens();
        return tokens.length == 2 && tokens[1].equals("0") && tokens[0].matches("[A-Za-z]{1,2}");
    }

    private static String symbol(Line line)
    {
        String raw = line.tokens()[0];
        String symbol = raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();
        if (ElementData.numberOf(symbol) == 0)
        {
            throw line.error("'" + raw + "' is not an element symbol");
        }
        return symbol;
    }

    private static int integer(Line line, String token)
    {
        try
        {
            return Integer.parseInt(token.trim());
        }
        catch (NumberFormatException exception)
        {
            throw line.error("'" + token + "' is not an integer");
        }
    }

    private static double number(Line line, String token)
    {
        try
        {
            double value = Double.parseDouble(token.replace('D', 'E').replace('d', 'e'));
            if (!Double.isFinite(value))
            {
                throw new NumberFormatException();
            }
            return value;
        }
        catch (NumberFormatException exception)
        {
            throw line.error("'" + token + "' is not a finite number");
        }
    }

    private static List<Line> significantLines(String text)
    {
        List<Line> result = new ArrayList<>();
        int number = 0;
        for (String raw : text.lines().toList())
        {
            number++;
            String trimmed = raw.trim();

            // blank lines and lines starting with ! are comments
            if (!trimmed.isEmpty() && !trimmed.startsWith("!"))
            {
                result.add(new Line(number, trimmed));
            }
        }
        return result;
    }

    /**
     * @param number the line's number in the file, from 1
     * @param text the line with surrounding whitespace removed
     */
    private record Line(int number, String text)
    {
        String[] tokens()
        {
            return text.split("\\s+");
        }

        IllegalArgumentException error(String message)
        {
            return new IllegalArgumentException("line " + number + ": " + message);
        }
    }
}
