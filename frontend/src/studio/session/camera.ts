import {useCallback, useEffect, useRef} from "react";
import type {SessionCamera} from "./messages";
import type {SessionLink} from "./useSocket";

export const CAMERA_SAVE_MS = 500;

/**
 * Keeps the camera with the molecule, once it has been left alone for a while.
 *
 * @returns what to call with the camera each time it moves
 */
export function useCameraSave({ socketRef, held, adopt }: SessionLink): (camera: SessionCamera) => void {
  const cameraTimer = useRef<number | null>(null);

  // nothing is sent for a camera that was waiting when the page went
  useEffect(
    () => () => {
      if (cameraTimer.current !== null) {
        clearTimeout(cameraTimer.current);
      }
    },
    [],
  );

  return useCallback(
    (camera: SessionCamera) => {
      if (cameraTimer.current !== null) {
        clearTimeout(cameraTimer.current);
      }
      cameraTimer.current = window.setTimeout(() => {
        cameraTimer.current = null;
        const socket = socketRef.current;
        const base = held.current;
        if (!socket || !base) {
          return;
        }

        socket
          .setCamera(base.session_id, camera)
          .then((reply) => {
            const now = held.current;
            if (!now || now.session_id !== base.session_id) {
              return;
            }
            if (now.saved_molecule_id !== reply.saved_molecule_id) {
              // the view has just stored the molecule, which is what a reload should open
              adopt({ ...now, camera, saved_molecule_id: reply.saved_molecule_id });
            } else {
              // the screen is already as it was left, so nothing needs to draw again
              held.current = { ...now, camera };
            }
          })
          .catch((cause: unknown) => {
            // the view is as the user left it, and the next change tries again
            console.warn("The camera could not be saved:", cause);
          });
      }, CAMERA_SAVE_MS);
    },
    [adopt, held, socketRef],
  );
}
