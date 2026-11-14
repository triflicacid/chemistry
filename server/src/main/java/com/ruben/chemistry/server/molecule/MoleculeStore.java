package com.ruben.chemistry.server.molecule;



import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MoleculeStore
{
    /** How many molecules are kept by default. */
    public static final int DEFAULT_CAPACITY = 1_000;

    private final Map<String, StoredMolecule> molecules;

    public MoleculeStore()
    {
        this(DEFAULT_CAPACITY);
    }

    /**
     * @param capacity how many molecules to keep
     * @throws IllegalArgumentException if {@code capacity} is not positive
     */
    public MoleculeStore(int capacity)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("capacity must be positive, but was " + capacity);
        }
        this.molecules = new LinkedHashMap<>(16, 0.75f, true)
        {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, StoredMolecule> eldest)
            {
                return size() > capacity;
            }
        };
    }

    /**
     * @param parsed a molecule read from SMILES
     * @return the stored molecule
     */
    public StoredMolecule add(ParsedMolecule parsed)
    {
        StoredMolecule stored = new StoredMolecule(UUID.randomUUID().toString(), parsed);
        synchronized (molecules)
        {
            molecules.put(stored.id(), stored);
        }
        return stored;
    }

    /**
     * @param id a molecule's ID
     * @return the molecule, if it is (still) stored
     */
    public Optional<StoredMolecule> find(String id)
    {
        synchronized (molecules)
        {
            return Optional.ofNullable(molecules.get(id));
        }
    }
}
