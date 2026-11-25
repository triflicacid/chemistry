import {type RefObject, useEffect, useRef, useState} from "react";

/** How narrow a chart is allowed to get, in pixels. */
const LEAST = 200;

/**
 * Keeps track of how wide an element is, for a chart that is drawn in pixels.
 *
 * @param initial the width to use until the element has been measured
 * @returns a ref for the element, and its width
 */
export function useWidth<T extends HTMLElement>(initial: number): [RefObject<T | null>, number] {
  const box = useRef<T | null>(null);
  const [width, setWidth] = useState(initial);

  useEffect(() => {
    const element = box.current;
    if (!element || typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(([entry]) => entry && setWidth(Math.max(LEAST, entry.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return [box, width];
}
