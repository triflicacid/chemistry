package com.ruben.chemistry.quantum.basis;

import com.ruben.chemistry.util.CachedValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * A basis set for a range of elements.
 */
public final class BasisLibrary
{
    private static final String CEP_31G_RESOURCE = "/basis/sbkjc-vdz.gbs";

    private final Map<String, ElementBasis> elements;

    private BasisLibrary(Map<String, ElementBasis> elements)
    {
        this.elements = Map.copyOf(elements);
    }

    /**
     * @param text a basis set in Gaussian94 format
     * @return a library holding every element in {@code text}
     * @throws IllegalArgumentException if the text is malformed
     */
    public static BasisLibrary parse(String text)
    {
        return new BasisLibrary(BasisSetParser.parse(text));
    }

    /**
     * @return CEP-31G valence basis and effective core potentials
     * @throws UncheckedIOException if the bundled file is missing or unreadable
     */
    public static BasisLibrary cep31g()
    {
        return Cep31g.INSTANCE.get();
    }

    /**
     * @param symbol an element symbol
     * @return whether the library has a basis for the element
     */
    public boolean supports(String symbol)
    {
        return elements.containsKey(symbol);
    }

    /**
     * @return the symbols of every supported element in alphabetical order
     */
    public Set<String> symbols()
    {
        return new TreeSet<>(elements.keySet());
    }

    /**
     * @param symbol an element symbol in standard capitalisation
     * @return the element's basis and core potential
     * @throws IllegalArgumentException if the element is not supported
     */
    public ElementBasis element(String symbol)
    {
        ElementBasis basis = elements.get(symbol);
        if (basis == null)
        {
            throw new IllegalArgumentException("no basis for element " + symbol);
        }
        return basis;
    }

    private static final class Cep31g
    {
        private static final Supplier<BasisLibrary> INSTANCE = new CachedValue<>(Cep31g::load);

        private static BasisLibrary load()
        {
            try (InputStream stream = BasisLibrary.class.getResourceAsStream(CEP_31G_RESOURCE))
            {
                if (stream == null)
                {
                    throw new IOException("missing resource " + CEP_31G_RESOURCE);
                }
                return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            catch (IOException exception)
            {
                throw new UncheckedIOException("cannot load " + CEP_31G_RESOURCE, exception);
            }
        }
    }
}
