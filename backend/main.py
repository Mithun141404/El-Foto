"""
Context Camera Backend — FastAPI server for AI-powered scene analysis.
Uses Gemini Vision (multimodal) to analyze real camera frames directly and
generate 33-point skeletal pose coordinates based on what it actually sees.
No labels, no keyword maps — pure visual understanding.
"""

import json
import os
import re

from dotenv import load_dotenv
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
import google.generativeai as genai
from pydantic import BaseModel

load_dotenv()

# ---------------------------------------------------------------------------
# App setup
# ---------------------------------------------------------------------------
app = FastAPI(title="Context Camera API", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Gemini Vision client
# ---------------------------------------------------------------------------
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
if not GEMINI_API_KEY:
    raise RuntimeError("GEMINI_API_KEY environment variable is not set")

genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel("gemini-flash-latest")


# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------

class Keypoint(BaseModel):
    x: float
    y: float


class PoseResponse(BaseModel):
    scene_name: str
    pose_name: str
    keypoints: list[Keypoint]  # exactly 33 points


# ---------------------------------------------------------------------------
# Vision prompt — one shot: scene analysis + pose generation
# ---------------------------------------------------------------------------

VISION_PROMPT = """You are an expert photography scene analyst and professional pose director.

Carefully examine this camera frame. Identify the exact scene, environment,
lighting conditions, time of day, and overall atmosphere.

Then generate ONE natural, creative, aesthetically pleasing pose that a person
would look stunning doing in a photo taken in this exact setting.

You MUST return EXACTLY 33 keypoints in the BlazePose format.
Each keypoint is a normalized coordinate: x is 0.0 (left) → 1.0 (right),
y is 0.0 (top) → 1.0 (bottom), representing where each body part should be
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
- The pose must look natural and fitting for the exact scene visible in the image.
- The person should be roughly centered in the frame.
- All coordinates must be strictly between 0.0 and 1.0.
- Head should typically be in the upper portion (y: 0.10–0.25).
- Feet should typically be in the lower portion (y: 0.85–0.95).
- The pose should be front-facing or three-quarter angle.
- Make the pose expressive and interesting — not just a stiff stance.
- scene_name MUST be a vivid, evocative 3–8 word phrase capturing the exact mood,
  lighting, and environment (e.g. "sun-drenched golden beach at sunset",
  "cozy dimly-lit espresso café", "misty pine forest at dawn",
  "bustling neon-lit urban rooftop"). NEVER use bland labels like
  "Outdoor", "Indoor", "Nature", or "Urban".

Return ONLY a valid JSON object — no markdown, no extra text:
{{
  "scene_name": "vivid descriptive scene name here",
  "pose_name": "short creative pose name",
  "keypoints": [
    {{"x": 0.5, "y": 0.15}},
    ... (33 total keypoints in order)
  ]
}}"""


# ---------------------------------------------------------------------------
# Endpoint — fully AI-powered vision analysis
# ---------------------------------------------------------------------------

@app.post("/analyze-and-pose", response_model=PoseResponse)
async def analyze_and_pose(image: UploadFile = File(...)):
    """
    Accept a raw camera frame (JPEG/PNG), analyze it with Gemini Vision,
    and return a vivid scene description + 33-point BlazePose keypoints.
    No hardcoded labels or keyword maps — pure AI visual understanding.
    """
    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(status_code=400, detail="Empty image upload")

    mime_type = image.content_type or "image/jpeg"

    try:
        print(f"--- Gemini Vision analyzing image ({len(image_bytes):,} bytes | {mime_type}) ---")

        response = model.generate_content(
            [
                {"mime_type": mime_type, "data": image_bytes},
                VISION_PROMPT,
            ],
            generation_config={"response_mime_type": "application/json"},
        )
        raw = response.text.strip()
        print(f"Response received (length: {len(raw)})")

        # Strip markdown fences if present
        if raw.startswith("```"):
            raw = re.sub(r"^```(?:json)?\s*", "", raw)
            raw = re.sub(r"\s*```$", "", raw)

        data = json.loads(raw)
        scene_name = data.get("scene_name", "Beautiful Scene")
        pose_name = data.get("pose_name", "Natural Pose")
        keypoints_raw = data.get("keypoints", [])

        if len(keypoints_raw) != 33:
            raise ValueError(f"Expected 33 keypoints, got {len(keypoints_raw)}")

        keypoints = [
            Keypoint(
                x=max(0.0, min(1.0, float(kp["x"]))),
                y=max(0.0, min(1.0, float(kp["y"]))),
            )
            for kp in keypoints_raw
        ]

        print(f"✓ Scene: '{scene_name}' | Pose: '{pose_name}'")

        return PoseResponse(
            scene_name=scene_name,
            pose_name=pose_name,
            keypoints=keypoints,
        )

    except json.JSONDecodeError:
        print(f"!!! JSON parse error. Raw snippet: {raw[:300]}")
        raise HTTPException(status_code=500, detail="Failed to parse AI response as JSON")
    except Exception as e:
        print(f"!!! Error: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))


# ---------------------------------------------------------------------------
# Health check
# ---------------------------------------------------------------------------

@app.get("/health")
async def health():
    return {"status": "ok", "version": "2.0.0", "recognition": "gemini-vision"}
