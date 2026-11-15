import type { FindingInfo, ValidationReport } from "../../api/types/validation";
import { SmilesText } from "./SmilesText";


export class ValidationView {
  readonly element = document.createElement("section");
  private readonly summary = document.createElement("p");
  private readonly list = document.createElement("ul");
  private readonly point: (finding: FindingInfo | null) => void;

  /** @param point hears which finding the reader is on, and null once they leave it */
  constructor(point: (finding: FindingInfo | null) => void = () => {}) {
    this.point = point;
    this.element.className = "validation";
    this.element.hidden = true;
    this.summary.className = "validation-summary";
    this.list.className = "validation-findings";
    this.element.append(this.summary, this.list);
  }

  /** Hides the list and forgets what it held. */
  clear(): void {
    this.point(null);
    this.element.hidden = true;
    this.summary.textContent = "";
    this.summary.className = "validation-summary";
    this.list.replaceChildren();
  }

  /** Shows that the check itself could not be run. */
  showError(message: string): void {
    this.clear();
    this.element.hidden = false;
    this.summary.className = "validation-summary error";
    this.summary.textContent = message;
  }

  /**
   * Shows a report.
   *
   * @param report what the checks found
   * @param smiles the SMILES the findings' spans are positions in
   */
  show(report: ValidationReport, smiles: string): void {
    this.clear();
    this.element.hidden = false;
    this.summary.className = `validation-summary ${ValidationView.worst(report)}`;
    this.summary.textContent = ValidationView.describe(report);
    for (const finding of report.findings) {
      this.list.append(this.row(finding, smiles));
    }
  }

  /**
   * @param finding one finding
   * @param smiles the SMILES its spans are positions in
   * @return the finding as a row of the list
   */
  private row(finding: FindingInfo, smiles: string): HTMLElement {
    const row = document.createElement("li");
    row.className = `finding ${finding.severity}`;
    // reachable by keyboard as well as pointer, since pointing at the molecule is how a finding is read
    if (finding.atoms.length > 0 || finding.bonds.length > 0) {
      row.tabIndex = 0;
      row.addEventListener("mouseenter", () => this.point(finding));
      row.addEventListener("focus", () => this.point(finding));
      row.addEventListener("mouseleave", () => this.point(null));
      row.addEventListener("blur", () => this.point(null));
    }

    const label = document.createElement("span");
    label.className = "finding-severity";
    label.textContent = finding.severity;

    const message = document.createElement("span");
    message.className = "finding-message";
    message.textContent = finding.message;

    const rule = document.createElement("code");
    rule.className = "finding-rule";
    rule.textContent = finding.rule;
    rule.title = `${finding.category}: ${finding.rule}`;

    row.append(label, message, rule);
    if (finding.hint) {
      const hint = document.createElement("span");
      hint.className = "finding-hint";
      hint.textContent = finding.hint;
      row.append(hint);
    }
    if (finding.spans.length > 0) {
      const text = new SmilesText(smiles, "smiles finding-smiles");
      text.highlight(finding.spans.map((span) => ({ span, colour: 0 })));
      row.append(text.element);
    }
    return row;
  }

  /**
   * @param report what the checks found
   * @return the severity to colour the summary by, which is the worst one found
   */
  private static worst(report: ValidationReport): string {
    if (report.errors > 0) {
      return "error";
    }
    if (report.warnings > 0) {
      return "warning";
    }
    return report.infos > 0 ? "info" : "clean";
  }

  /**
   * @param report what the checks found
   * @return the counts in words
   */
  private static describe(report: ValidationReport): string {
    if (report.findings.length === 0) {
      return "Nothing to report.";
    }
    const parts = [
      ValidationView.plural(report.errors, "error"),
      ValidationView.plural(report.warnings, "warning"),
      ValidationView.plural(report.infos, "note"),
    ].filter((part) => part !== null);
    return `${parts.join(", ")}.`;
  }

  /**
   * @param count how many there are
   * @param noun what they are
   * @return the count and noun, or null if there are none
   */
  private static plural(count: number, noun: string): string | null {
    if (count === 0) {
      return null;
    }
    return `${count} ${noun}${count === 1 ? "" : "s"}`;
  }
}
