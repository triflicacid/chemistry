package com.ruben.chemistry.cli;

import com.ruben.chemistry.render.svg.SvgWriter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts finished SVG documents side by side in one.
 */
final class SvgPanels
{
    private static final String ROOT_PREFIX = "<svg xmlns=\"http://www.w3.org/2000/svg\"";

    private static final Pattern SIZE = Pattern.compile("^<svg [^>]*?width=\"([^\"]+)\" height=\"([^\"]+)\"");

    private SvgPanels()
    {
    }

    /**
     * @param documents SVG document contents
     * @return them in a row
     */
    static String row(List<String> documents)
    {
        if (documents.size() == 1)
        {
            return documents.getFirst();
        }

        StringBuilder body = new StringBuilder();
        double x = 0;
        double height = 0;
        for (String document : documents)
        {
            Matcher size = SIZE.matcher(document);
            if (!size.find() || !document.startsWith(ROOT_PREFIX))
            {
                throw new IllegalStateException("Unrecognised SVG document header");
            }
            double width = Double.parseDouble(size.group(1));
            height = Math.max(height, Double.parseDouble(size.group(2)));

            body.append("  <svg x=\"").append(SvgWriter.number(x)).append("\" y=\"0\"")
                .append(document.substring(ROOT_PREFIX.length()));
            x += width;
        }

        return ROOT_PREFIX + " width=\"" + SvgWriter.number(x) + "\" height=\"" + SvgWriter.number(height)
            + "\" viewBox=\"0 0 " + SvgWriter.number(x) + " " + SvgWriter.number(height) + "\">\n" + body + "</svg>\n";
    }
}
