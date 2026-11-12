import { ApiError, HttpClient } from "./http";
import type { MoleculeId, OptionDescriptor, OptionValues } from "./types";

/** Carries the id of the molecule an SVG was drawn from. */
const MOLECULE_ID_HEADER = "X-Molecule-Id";

export interface SvgResponse {
  svg: string;
  moleculeId: MoleculeId;
}

export class ChemistryApi {
  constructor(private readonly http: HttpClient = new HttpClient()) {}

  /** Resolves if the server is up. */
  async health(): Promise<void> {
    await this.http.send("GET", "/api/health");
  }

  /** The options the user may set. */
  async options(): Promise<OptionDescriptor[]> {
    const body = await this.http.json<{ options: OptionDescriptor[] }>("GET", "/api/options");
    return body.options;
  }

  /** Stores a molecule. */
  async createMolecule(smiles: string): Promise<MoleculeId> {
    const body = await this.http.json<{ id: MoleculeId }>("POST", "/api/molecules", { json: { smiles } });
    return body.id;
  }

  /** Draws a stored molecule. */
  renderMolecule(id: MoleculeId, options: OptionValues): Promise<SvgResponse> {
    return this.svg("POST", `/api/molecules/${encodeURIComponent(id)}/render`, { json: { options } });
  }

  /** The latest SVG the server drew for a stored molecule. */
  moleculeSvg(id: MoleculeId): Promise<SvgResponse> {
    return this.svg("GET", `/api/molecules/${encodeURIComponent(id)}/svg`);
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
