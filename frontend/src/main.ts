import { health } from "./api";

const app = document.querySelector<HTMLDivElement>("#app");
if (!app) {
  throw new Error("Missing #app element");
}

const heading = document.createElement("h1");
heading.textContent = "Hello, world";

const button = document.createElement("button");
button.textContent = "Health check";
button.addEventListener("click", async () => {
  try {
    console.log(await health());
  } catch (error) {
    console.error("Health check failed", error);
  }
});

app.append(heading, button);
