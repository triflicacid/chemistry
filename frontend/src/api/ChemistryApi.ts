import { HttpClient } from "./http";

/** The server's endpoints, one method each. JSON keys are snake_case. */
export class ChemistryApi {
  constructor(private readonly http: HttpClient = new HttpClient()) {}

  /** Resolves if the server is up. */
  async health(): Promise<void> {
    await this.http.send("GET", "/api/health");
  }

  /** Draws a SMILES string; resolves to an SVG document. */
  render(smiles: string): Promise<string> {
    return this.http.text("POST", "/api/render", {
      json: { smiles },
      accept: "image/svg+xml",
    });
  }
}
