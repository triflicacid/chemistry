import { ApiError, HttpClient } from "./http";
import type { MatchData, MatchResult, MoleculeId, MoleculeMetadata, OptionDescriptor, OptionValues, ProjectionRequest, Scene, StoredMolecule, Structure3D } from "./types";

/** Carries the id of the molecule an SVG was drawn from. */
const MOLECULE_ID_HEADER = "X-Molecule-Id";

export interface SvgResponse {
  svg: string;
  moleculeId: MoleculeId;
}

export interface SceneResponse {
  scene: Scene;
  moleculeId: MoleculeId;
}

export class ChemistryApi {
  constructor(private readonly http: HttpClient = new HttpClient()) {}

  /** Resolves if the server is up. */
  async health(): Promise<void> {
    await this.http.send("GET", "/api/health");
  }

  /** The options the user may set for a 2D drawing. */
  options2d(): Promise<OptionDescriptor[]> {
    return this.optionsAt("/api/options/2d");
  }

  /** The options the user may set for a 3D view. */
  options3d(): Promise<OptionDescriptor[]> {
    return this.optionsAt("/api/options/3d");
  }

  /** The options the user may set for how matches of a SMARTS pattern are shown. */
  optionsMatch(): Promise<OptionDescriptor[]> {
    return this.optionsAt("/api/options/match");
  }

  /** Draws a stored molecule with the matches of a SMARTS pattern highlighted. */
  matches2d(id: MoleculeId, smarts: string, options: OptionValues, match: OptionValues): Promise<MatchResult> {
    return this.http.json<MatchResult>("POST", `/api/molecules/${encodeURIComponent(id)}/match/2d`, { json: { smarts, options, match } });
  }

  /** Finds where a SMARTS pattern matches a stored molecule, for a 3D view to highlight. */
  matches3d(id: MoleculeId, smarts: string, match: OptionValues): Promise<MatchData> {
    return this.http.json<MatchData>("POST", `/api/molecules/${encodeURIComponent(id)}/match/3d`, { json: { smarts, match } });
  }

  private async optionsAt(path: string): Promise<OptionDescriptor[]> {
    const body = await this.http.json<{ options: OptionDescriptor[] }>("GET", path);
    return body.options;
  }

  /** Generates a fresh 3D structure for a stored molecule; the server keeps it as the molecule's latest. */
  structure3d(id: MoleculeId, conformers: number): Promise<Structure3D> {
    return this.http.json<Structure3D>("POST", `/api/molecules/${encodeURIComponent(id)}/structure/3d?conformers=${conformers}`);
  }

  /** The latest 3D structure the server generated for a stored molecule; fails with `NOT_GENERATED` if there is none. */
  cachedStructure3d(id: MoleculeId): Promise<Structure3D> {
    return this.http.json<Structure3D>("GET", `/api/molecules/${encodeURIComponent(id)}/structure/3d`);
  }

  /** Stores a molecule. */
  createMolecule(smiles: string): Promise<StoredMolecule> {
    return this.http.json<StoredMolecule>("POST", "/api/molecules", { json: { smiles } });
  }

  /** Fetches a molecule's metadata. */
  metadata(id: MoleculeId): Promise<MoleculeMetadata> {
    return this.http.json<MoleculeMetadata>("GET", `/api/molecules/${encodeURIComponent(id)}/metadata`);
  }

  /** Draws a stored molecule. */
  renderMolecule(id: MoleculeId, options: OptionValues): Promise<SvgResponse> {
    return this.svg("POST", `/api/molecules/${encodeURIComponent(id)}/render/2d`, { json: { options } });
  }

  /** The latest SVG the server drew for a stored molecule. */
  moleculeSvg(id: MoleculeId): Promise<SvgResponse> {
    return this.svg("GET", `/api/molecules/${encodeURIComponent(id)}/svg/2d`);
  }

  /** Draws a stored molecule as a scene. */
  async structure2d(id: MoleculeId, options: OptionValues): Promise<SceneResponse> {
    return this.scene("POST", `/api/molecules/${encodeURIComponent(id)}/structure/2d`, { json: { options } });
  }

  /** The latest scene the server drew for a stored molecule; fails with `NOT_RENDERED` if there is none. */
  cachedStructure2d(id: MoleculeId): Promise<SceneResponse> {
    return this.scene("GET", `/api/molecules/${encodeURIComponent(id)}/structure/2d`);
  }

  /** Draws the stored molecule's latest 3D structure flat; the server keeps the drawing as the molecule's latest 2D one. */
  project2d(id: MoleculeId, request: ProjectionRequest): Promise<SceneResponse> {
    return this.scene("POST", `/api/molecules/${encodeURIComponent(id)}/projection/2d`, { json: request });
  }

  private async scene(method: string, path: string, options: { json?: unknown } = {}): Promise<SceneResponse> {
    const response = await this.http.send(method, path, { ...options, accept: "application/json" });
    const moleculeId = response.headers.get(MOLECULE_ID_HEADER);
    if (!moleculeId) {
      throw new ApiError(response.status, "MISSING_MOLECULE_ID", `The response had no ${MOLECULE_ID_HEADER} header`);
    }
    return { scene: (await response.json()) as Scene, moleculeId };
  }

  private async svg(method: string, path: string, options: { json?: unknown } = {}): Promise<SvgResponse> {
    const response = await this.http.send(method, path, { ...options, accept: "image/svg+xml" });
    const moleculeId = response.headers.get(MOLECULE_ID_HEADER);
    if (!moleculeId) {
      throw new ApiError(response.status, "MISSING_MOLECULE_ID", `The response had no ${MOLECULE_ID_HEADER} header`);
    }
    return { svg: await response.text(), moleculeId };
  }
}
