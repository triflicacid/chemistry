import { ChemistryApi } from "./api/ChemistryApi";
import { App } from "./ui/App";
import "./style.css";

const root = document.querySelector<HTMLElement>("#app");
if (!root) {
  throw new Error("Missing #app element");
}

new App(new ChemistryApi()).mount(root);
