/**
 * Whether a key press belongs to a text field.
 *
 * @param event the key press
 * @returns true if the press is being typed into a field
 */
export function isTyping(event: KeyboardEvent): boolean {
  const target = event.target as HTMLElement | null;
  return target?.matches("input, textarea, select, [contenteditable]") ?? false;
}
