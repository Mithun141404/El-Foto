"""
Context Camera Backend — FastAPI server for pose generation.
Uses Gemini Flash to generate 33-point skeletal pose coordinates
based on detected scene context.
"""

import json
import os
import re
from typing import Optional

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import google.generativeai as genai
from pydantic import BaseModel

load_dotenv()

# ---------------------------------------------------------------------------
# App setup
# ---------------------------------------------------------------------------
app = FastAPI(title="Context Camera API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Gemini client
# ---------------------------------------------------------------------------
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    raise RuntimeError("GEMINI_API_KEY environment variable is not set")

genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel("gemini-flash-latest")

# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------

class PoseRequest(BaseModel):
    scene: str  # e.g. "Graduation", "Cafe", "Beach"


class Keypoint(BaseModel):
    x: float
    y: float


class PoseResponse(BaseModel):
    scene: str
    pose_name: str
    keypoints: list[Keypoint]  # exactly 33 points


# ---------------------------------------------------------------------------
# Prompt template
# ---------------------------------------------------------------------------

POSE_PROMPT = """You are a professional photography pose director and spatial rigger.

Given the scene/environment "{scene}", generate ONE natural, aesthetically pleasing pose
that a person would look great doing in a photo taken in that setting.

You MUST return EXACTLY 33 keypoints in the BlazePose format, as a JSON object.
Each keypoint is a normalized coordinate where x is 0.0 (left) to 1.0 (right)
and y is 0.0 (top) to 1.0 (bottom), representing where that body part should be
positioned in the camera frame.

The 33 keypoints IN ORDER are:
0: nose
1: left_eye_inner, 2: left_eye, 3: left_eye_outer
4: right_eye_inner, 5: right_eye, 6: right_eye_outer
7: left_ear, 8: right_ear
9: mouth_left, 10: mouth_right
11: left_shoulder, 12: right_shoulder
13: left_elbow, 14: right_elbow
15: left_wrist, 16: right_wrist
17: left_pinky, 18: right_pinky
19: left_index, 20: right_index
21: left_thumb, 22: right_thumb
23: left_hip, 24: right_hip
25: left_knee, 26: right_knee
27: left_ankle, 28: right_ankle
29: left_heel, 30: right_heel
31: left_foot_index, 32: right_foot_index

Rules:
- The pose must look natural and appropriate for the "{scene}" setting.
- The person should be roughly centered in the frame.
- All coordinates must be between 0.0 and 1.0.
- Head should typically be in the upper portion (y: 0.1-0.25).
- Feet should typically be in the lower portion (y: 0.85-0.95).
- The pose should be front-facing or three-quarter angle.
- Make the pose interesting, not just standing straight.

Return ONLY a valid JSON object in this exact format, no other text:
{{
  "pose_name": "a short descriptive name for the pose",
  "keypoints": [
    {{"x": 0.5, "y": 0.15}},
    ... (33 total keypoints in order)
  ]
}}"""


# ---------------------------------------------------------------------------
# Endpoint
# ---------------------------------------------------------------------------

@app.post("/generate-pose", response_model=PoseResponse)
async def generate_pose(request: PoseRequest):
    """Generate a 33-point skeletal pose for a given scene."""
    scene = request.scene.strip()
    if not scene:
        raise HTTPException(status_code=400, detail="Scene cannot be empty")

    prompt = POSE_PROMPT.format(scene=scene)

    try:
        print(f"--- Generating pose for scene: {scene} ---")
        response = model.generate_content(
            prompt,
            generation_config={"response_mime_type": "application/json"}
        )
        raw = response.text.strip()
        print(f"Gemini response received (length: {len(raw)})")
        
        # Strip markdown code fences if present
        if raw.startswith("```"):
            raw = re.sub(r"^```(?:json)?\s*", "", raw)
            raw = re.sub(r"\s*```$", "", raw)

        data = json.loads(raw)
        pose_name = data.get("pose_name", f"{scene} Pose")
        keypoints_raw = data.get("keypoints", [])

        if len(keypoints_raw) != 33:
            raise ValueError(f"LLM returned {len(keypoints_raw)} keypoints instead of 33")

        keypoints = []
        for kp in keypoints_raw:
            x = max(0.0, min(1.0, float(kp["x"])))
            y = max(0.0, min(1.0, float(kp["y"])))
            keypoints.append(Keypoint(x=x, y=y))

        return PoseResponse(
            scene=scene,
            pose_name=pose_name,
            keypoints=keypoints,
        )

    except json.JSONDecodeError:
        print(f"!!! JSON Parse Error. Raw: {raw}")
        raise HTTPException(status_code=500, detail="Failed to parse LLM response as JSON")
    except Exception as e:
        print(f"!!! Error generating pose: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok"}
