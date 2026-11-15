import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./studio.css";

const root = document.querySelector<HTMLElement>("#studio");
if (!root) {
  throw new Error("Missing #studio element");
}

createRoot(root).render(<App />);
