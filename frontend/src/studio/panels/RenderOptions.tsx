import type {Models} from "../models/models";
import {DEFAULT_BRIGHTNESS, MAX_BRIGHTNESS, MIN_BRIGHTNESS} from "../models/RenderModel";
import {useObservable} from "../models/observable";
import {Segmented} from "../controls/Segmented";
import {FINISH_OPTIONS} from "../layout/viewportOptions";

/**
 * How the molecule is drawn, in a section that folds down to its title at the foot of the Outliner. Closing it leaves the
 * settings as they are.
 */
export function RenderOptions({ models }: { models: Models }) {
  const { render } = models;
  const { finish, brightness, open } = useObservable(render);

  return (
    <section className="render-options" aria-label="Render options">
      <button type="button" className="render-header" aria-expanded={open} onClick={() => render.openTo(!open)}>
        <span className="twisty">{open ? "▾" : "▸"}</span>
        Render options
      </button>
      {open && (
        <dl className="properties render-body">
          <dt>Finish</dt>
          <dd>
            <Segmented label="Finish" value={finish} options={FINISH_OPTIONS} onChange={(next) => render.finishWith(next)} />
          </dd>
          <dt>Brightness</dt>
          <dd className="position">
            <input
              type="range"
              className="render-slider"
              min={Math.round(MIN_BRIGHTNESS * 100)}
              max={Math.round(MAX_BRIGHTNESS * 100)}
              step={5}
              value={Math.round(brightness * 100)}
              aria-label="Brightness of the scene, as a percentage of the usual lighting"
              title="How bright the scene is. Double-click to put it back to the usual lighting"
              onChange={(event) => render.brightnessTo(Number(event.target.value) / 100)}
              onDoubleClick={() => render.brightnessTo(DEFAULT_BRIGHTNESS)}
            />
            <span className="unit render-value">{Math.round(brightness * 100)}%</span>
          </dd>
        </dl>
      )}
    </section>
  );
}
