import type {Models} from "../models/models";
import {useObservable} from "../models/observable";
import type {Vec3} from "../../api/types/structure";
import {NumberField} from "../controls/NumberField";

/** Where the camera is and how it looks. */
export function CameraPane({ models }: { models: Models }) {
  const { camera } = models;
  const now = useObservable(camera);
  if (!now) {
    return <p className="panel-empty">There is no camera yet.</p>;
  }

  const row = (name: string, label: string, values: Vec3, unit: string, commit: (next: Vec3) => void) => (
    <>
      <dt>{name}</dt>
      <dd className="position">
        {(["x", "y", "z"] as const).map((axis) => (
          <NumberField key={axis} label={`${axis.toUpperCase()} ${label}, in ångströms`} value={values[axis]} onCommit={(value) => commit({ ...values, [axis]: value })} />
        ))}
        <span className="unit">{unit}</span>
      </dd>
    </>
  );
  const single = (name: string, label: string, value: number, unit: string, digits: number, commit: (next: number) => void) => (
    <>
      <dt>{name}</dt>
      <dd className="position">
        <NumberField label={label} value={value} digits={digits} onCommit={commit} />
        <span className="unit">{unit}</span>
      </dd>
    </>
  );

  return (
    <>
      <h3 className="pane-title">Camera</h3>
      <dl className="properties">
        {row("Position", "position of the camera", now.position, "Å", (position) => camera.edit({ position }))}
        {row("Looking at", "of the point the camera looks at", now.target, "Å", (target) => camera.edit({ target }))}
        {single("Yaw", "Yaw of the camera round the point it looks at, in degrees", now.yaw, "°", 2, (yaw) => camera.edit({ yaw }))}
        {single("Pitch", "Pitch of the camera above the point it looks at, in degrees", now.pitch, "°", 2, (pitch) => camera.edit({ pitch }))}
        {single("Distance", "Distance of the camera from the point it looks at, in ångströms", now.distance, "Å", 3, (distance) => camera.edit({ distance }))}
        {single("Field of view", "Vertical field of view of the camera, in degrees", now.fov, "°", 1, (fov) => camera.edit({ fov }))}
        {single("Roll", "Roll of the camera about the way it looks, in degrees, counterclockwise positive", now.roll, "°", 2, (roll) => camera.edit({ roll }))}
      </dl>
    </>
  );
}
