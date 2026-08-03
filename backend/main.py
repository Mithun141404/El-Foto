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
from google import genai
from google.genai import types
from pydantic import BaseModel

from poses import POSE_LIBRARY, POSE_DESCRIPTIONS

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

client = genai.Client(api_key=GEMINI_API_KEY)


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

VISION_PROMPT = f"""You are an expert photography scene analyst and professional pose director.

Carefully examine this camera frame. Identify the exact scene, environment,
lighting conditions, time of day, and overall atmosphere.

Then select the MOST APPROPRIATE pose from the following library for a person
in this exact setting. The pose should fit naturally with the environment.

Pose Library:
{POSE_DESCRIPTIONS}

Rules:
- scene_name MUST be a vivid, evocative 3–8 word phrase capturing the exact mood,
  lighting, and environment (e.g. "sun-drenched golden beach at sunset",
  "cozy dimly-lit espresso café", "misty pine forest at dawn",
  "bustling neon-lit urban rooftop"). NEVER use bland labels like
  "Outdoor", "Indoor", "Nature", or "Urban".
- pose_name should be a highly descriptive, catchy name for the pose you selected (e.g. "Relaxed Golden Hour Lean", "Urban Action Sprint").
- pose_id MUST be the integer ID (1-8) of the pose you selected from the library above.
- person_scale MUST be a float from 0.2 to 1.2 indicating how large the person should be in the frame (e.g., 0.3 for a distant landscape shot, 1.0 for a standard portrait).
- person_center_x and person_center_y MUST be float coordinates (0.0 to 1.0) for where the person should be placed in the scene (e.g. place them on a rock, or center frame).

Return ONLY a valid JSON object — no markdown, no extra text:
{{
  "scene_name": "vivid descriptive scene name here",
  "pose_name": "short creative pose name",
  "pose_id": 1,
  "person_scale": 1.0,
  "person_center_x": 0.5,
  "person_center_y": 0.6
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

        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=[
                types.Part.from_bytes(data=image_bytes, mime_type=mime_type),
                VISION_PROMPT,
            ],
            config=types.GenerateContentConfig(
                response_mime_type="application/json"
            ),
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
        pose_id = data.get("pose_id", 1)
        scale = float(data.get("person_scale", 1.0))
        cx = float(data.get("person_center_x", 0.5))
        cy = float(data.get("person_center_y", 0.6))

        keypoints_raw = POSE_LIBRARY.get(pose_id, POSE_LIBRARY[1])
        
        # Apply scaling and translation
        # Base poses are centered roughly at x=0.5, y=0.55
        transformed_raw = []
        for kp in keypoints_raw:
            dx = kp["x"] - 0.5
            dy = kp["y"] - 0.55
            transformed_raw.append({
                "x": cx + (dx * scale),
                "y": cy + (dy * scale)
            })

        keypoints = [
            Keypoint(
                x=max(0.0, min(1.0, float(kp["x"]))),
                y=max(0.0, min(1.0, float(kp["y"]))),
            )
            for kp in transformed_raw
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
