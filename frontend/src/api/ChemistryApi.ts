import { HttpClient } from "./http";
import type { OptionDescriptor, OptionValues } from "./types";

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

  /** Draws a SMILES string to an SVG. */
  render(smiles: string, options: OptionValues): Promise<string> {
    return this.http.text("POST", "/api/render", {
      json: { smiles, options },
      accept: "image/svg+xml",
    });
  }
}
