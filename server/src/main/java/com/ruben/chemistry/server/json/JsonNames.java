package com.ruben.chemistry.server.json;

public final class JsonNames
{
    private JsonNames()
    {
    }

    /**
     * @param name a camelCase Java name, e.g. {@code myValue} or {@code smilesURL}
     * @return it in snake_case, e.g. {@code my_value} or {@code smiles_url}
     */
    public static String of(String name)
    {
        StringBuilder out = new StringBuilder(name.length() + 4);

        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);

            if (Character.isUpperCase(c) && i > 0)
            {
                boolean upperCase = Character.isUpperCase(name.charAt(i - 1));
                boolean afterLower = !upperCase && name.charAt(i - 1) != '_';
                boolean endsAcronym = upperCase && i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));

                if (afterLower || endsAcronym)
                {
                    out.append('_');
                }
            }

            out.append(Character.toLowerCase(c));
        }

        return out.toString();
    }
}
