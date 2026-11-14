import type { FindingInfo, ValidationReport } from "../api/types";
import { SmilesText } from "./SmilesText";


export class ValidationView {
  readonly element = document.createElement("section");
  private readonly summary = document.createElement("p");
  private readonly list = document.createElement("ul");

  constructor() {
    this.element.className = "validation";
    this.element.hidden = true;
    this.summary.className = "validation-summary";
    this.list.className = "validation-findings";
    this.element.append(this.summary, this.list);
  }

  /** Hides the list and forgets what it held. */
  clear(): void {
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
      this.list.append(ValidationView.row(finding, smiles));
    }
  }

  /**
   * @param finding one finding
   * @param smiles the SMILES its spans are positions in
   * @return the finding as a row of the list
   */
  private static row(finding: FindingInfo, smiles: string): HTMLElement {
    const row = document.createElement("li");
    row.className = `finding ${finding.severity}`;

    const label = document.createElement("span");
    label.className = "finding-severity";
    label.textContent = finding.severity;

    const message = document.createElement("span");
    message.className = "finding-message";
    message.textContent = finding.message;

    const rule = document.createElement("code");
    rule.className = "finding-rule";
    rule.textContent = finding.rule;
    rule.title = `${finding.category} · ${finding.rule}`;

    row.append(label, message, rule);
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
