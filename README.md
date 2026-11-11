# Chemistry

## Command line

Draw a SMILES string as an SVG:

```
./gradlew :backend:depict --args="CCO --out=ethanol.svg"
```

Draw a SMILES string with the matches of a SMARTS pattern highlighted:

```
./gradlew :backend:match --args="CC(=O)Oc1ccccc1C(=O)O C(=O)O --out=aspirin.svg"
```

Draw every molecule in a `name,smiles` file into `out/`, with a light `index.html` gallery:

```
./gradlew :backend:gallery
./gradlew :backend:gallery --args="examples/molecules.csv --out-dir=out --style=circles"
```

## Web App

Start the server:

```
./gradlew :server:run
```

Start the front-end:

```
cd frontend
pnpm install
pnpm dev
```
