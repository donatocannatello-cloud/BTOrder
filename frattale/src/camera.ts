import { type Quat, type Vec3, qFromAxisAngle, qIdentity, qMul, qNormalize, qRotate, vAdd, vLength, vLerp, vScale } from "./quat";

export interface CameraInput {
  forward: number; // -1..1
  right: number; // -1..1
  up: number; // -1..1
  yawDelta: number; // radians this frame, from mouse
  pitchDelta: number; // radians this frame, from mouse
  roll: number; // -1..1, from Q/E
}

const BASE_SPEED = 2.6; // units / second
const BOOST_SPEED = 6.5;
const ROLL_SPEED = 1.6; // radians / second
const VELOCITY_EASE = 3.2; // higher = snappier inertia response
const BASE_FOV = (62 * Math.PI) / 180;
const MAX_FOV = (78 * Math.PI) / 180;

export class FlightCamera {
  position: Vec3 = [0, 0, 5.2];
  orientation: Quat = qIdentity();
  velocity: Vec3 = [0, 0, 0];
  fov = BASE_FOV;

  get forwardAxis(): Vec3 {
    return qRotate(this.orientation, [0, 0, -1]);
  }
  get rightAxis(): Vec3 {
    return qRotate(this.orientation, [1, 0, 0]);
  }
  get upAxis(): Vec3 {
    return qRotate(this.orientation, [0, 1, 0]);
  }

  update(dt: number, input: CameraInput, boost: boolean) {
    // Orientation: incremental rotation around the camera's *current* local
    // axes, so yaw/pitch/roll compose freely without gimbal lock.
    let dq = qIdentity();
    if (input.yawDelta !== 0) {
      dq = qMul(qFromAxisAngle(this.upAxis, -input.yawDelta), dq);
    }
    if (input.pitchDelta !== 0) {
      dq = qMul(qFromAxisAngle(this.rightAxis, -input.pitchDelta), dq);
    }
    if (input.roll !== 0) {
      dq = qMul(qFromAxisAngle(this.forwardAxis, -input.roll * ROLL_SPEED * dt), dq);
    }
    this.orientation = qNormalize(qMul(dq, this.orientation));

    // Movement: local axes (true 6DOF flight, no world "up" bias) with
    // exponential-smoothed velocity so motion never feels instantaneous.
    const speed = boost ? BOOST_SPEED : BASE_SPEED;
    const wish: Vec3 = vAdd(
      vAdd(vScale(this.forwardAxis, input.forward), vScale(this.rightAxis, input.right)),
      vScale(this.upAxis, input.up)
    );
    const wishLen = vLength(wish);
    const target: Vec3 = wishLen > 1e-5 ? vScale(wish, speed / wishLen) : [0, 0, 0];

    const ease = 1 - Math.exp(-VELOCITY_EASE * dt);
    this.velocity = vLerp(this.velocity, target, ease);
    this.position = vAdd(this.position, vScale(this.velocity, dt));

    // FOV widens slightly with speed, also eased.
    const speedRatio = Math.min(vLength(this.velocity) / BOOST_SPEED, 1);
    const targetFov = BASE_FOV + (MAX_FOV - BASE_FOV) * speedRatio;
    this.fov += (targetFov - this.fov) * (1 - Math.exp(-2.5 * dt));
  }
}
