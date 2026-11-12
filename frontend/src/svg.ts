/**
 * Ensure the input is SVG and remove any malicious tags.
 * @throws Error if the text is not an SVG document
 */
export function sanitiseSvg(text: string): SVGSVGElement {
  const document = new DOMParser().parseFromString(text, "image/svg+xml");
  const root = document.documentElement;

  if (document.querySelector("parsererror") || root.localName !== "svg") {
    throw new Error("The server did not return a valid SVG");
  }

  root.querySelectorAll("script, foreignObject").forEach((element) => element.remove());
  for (const element of [root, ...root.querySelectorAll("*")]) {
    for (const attribute of [...element.attributes]) {
      const href = attribute.name === "href" || attribute.name === "xlink:href";
      if (attribute.name.startsWith("on") || (href && attribute.value.trim().toLowerCase().startsWith("javascript:"))) {
        element.removeAttribute(attribute.name);
      }
    }
  }

  return document.importNode(root, true) as unknown as SVGSVGElement;
}
