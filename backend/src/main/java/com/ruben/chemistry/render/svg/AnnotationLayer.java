package com.ruben.chemistry.render.svg;

import com.ruben.chemistry.render.Point;
import com.ruben.chemistry.render.annotate.AnnotationStyle;
import com.ruben.chemistry.render.annotate.Badge;
import com.ruben.chemistry.render.annotate.Halo;

import java.util.Objects;

/**
 * Draws an {@link AnnotationStyle} onto an SVG.
 */
public final class AnnotationLayer
{
    private final SvgWriter svg;
    private final AnnotationStyle style;

    /**
     * @param svg the document to draw onto
     * @param style how halos and badges look
     */
    public AnnotationLayer(SvgWriter svg, AnnotationStyle style)
    {
        this.svg = svg;
        this.style = style;
    }

    /**
     * Draws a halo behind an atom.
     *
     * @param centre where the atom is on the page
     * @param radius how far the atom reaches from its centre; the halo is this scaled by the halo's own scale
     * @param halo the halo to draw
     */
    public void atomHalo(Point centre, double radius, Halo halo)
    {
        svg.circle(centre, radius * halo.radiusScale())
            .fill(halo.colour()).opacity(style.haloOpacity());
    }

    /**
     * Draws a halo behind a bond.
     *
     * @param from where the bond starts on the page
     * @param to where it ends
     * @param width how thick the bond is; the halo is this scaled by the halo's own scale
     * @param halo the halo to draw
     */
    public void bondHalo(Point from, Point to, double width, Halo halo)
    {
        svg.line(from, to)
            .stroke(halo.colour()).strokeWidth(width * halo.radiusScale()).roundCaps().opacity(style.haloOpacity());
    }

    /**
     * Draws a badge centred on a point.
     *
     * @param at where the middle of the text goes on the page
     * @param badge the badge to draw
     */
    public void badge(Point at, Badge badge)
    {
        svg.text(at)
            .attribute("font-family", style.fontFamily())
            .attribute("font-size", style.badgeFontSize())
            .fill(badge.colour().orElse(style.badgeColour()))
            .stroke(style.badgeOutline())
            .strokeWidth(style.badgeFontSize() / 4)
            .attribute("paint-order", "stroke")
            .attribute("text-anchor", "middle")
            .attribute("dominant-baseline", "central")
            .content(badge.text());
    }
}
