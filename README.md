# Chemistry

## Command line

Draw a SMILES string as an SVG, in 2D or in 3D:

```
./gradlew :backend:render2d --args="CCO --out=ethanol.svg"
./gradlew :backend:render3d --args="CCO --out=ethanol3d.svg --style=spacefill --yaw=60"
```

Draw a SMILES string with the matches of a SMARTS pattern highlighted, in 2D or in 3D:

```
./gradlew :backend:match --args="CC(=O)Oc1ccccc1C(=O)O C(=O)O --out=aspirin.svg"
./gradlew :backend:match3d --args="CC(=O)Oc1ccccc1C(=O)O C(=O)O --out=aspirin3d.svg"
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

It serves two separate pages from the one dev server:

| Page | URL | Source | What it is |
| --- | --- | --- | --- |
| Viewer | `/` | `src/viewer` | Paste or load SMILES and inspect molecules in 2D and 3D. Plain TypeScript. |
| Studio | `/studio/` | `src/studio` | The molecular editing workspace. React. |

They are built as separate bundles and share only `src/api`: the HTTP client and the
types the server's messages are described by. Everything else is private to its page.
