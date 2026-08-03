def build_pose(
    head_y=0.2, head_x=0.5,
    l_shoulder=(0.4, 0.3), r_shoulder=(0.6, 0.3),
    l_elbow=(0.35, 0.45), r_elbow=(0.65, 0.45),
    l_wrist=(0.3, 0.6), r_wrist=(0.7, 0.6),
    l_hip=(0.45, 0.6), r_hip=(0.55, 0.6),
    l_knee=(0.45, 0.75), r_knee=(0.55, 0.75),
    l_ankle=(0.45, 0.9), r_ankle=(0.55, 0.9)
):
    pts = [{"x": 0.5, "y": 0.5} for _ in range(33)]
    
    # Head & Face
    pts[0] = {"x": head_x, "y": head_y} # nose
    pts[1] = pts[2] = pts[3] = {"x": head_x - 0.015, "y": head_y - 0.02} # L eye
    pts[4] = pts[5] = pts[6] = {"x": head_x + 0.015, "y": head_y - 0.02} # R eye
    pts[7] = {"x": head_x - 0.035, "y": head_y} # L ear
    pts[8] = {"x": head_x + 0.035, "y": head_y} # R ear
    pts[9] = {"x": head_x - 0.01, "y": head_y + 0.02} # mouth L
    pts[10] = {"x": head_x + 0.01, "y": head_y + 0.02} # mouth R

    # Torso
    pts[11] = {"x": l_shoulder[0], "y": l_shoulder[1]}
    pts[12] = {"x": r_shoulder[0], "y": r_shoulder[1]}
    pts[23] = {"x": l_hip[0], "y": l_hip[1]}
    pts[24] = {"x": r_hip[0], "y": r_hip[1]}

    # Arms
    pts[13] = {"x": l_elbow[0], "y": l_elbow[1]}
    pts[14] = {"x": r_elbow[0], "y": r_elbow[1]}
    pts[15] = {"x": l_wrist[0], "y": l_wrist[1]}
    pts[16] = {"x": r_wrist[0], "y": r_wrist[1]}
    
    # Hands (grouped near wrists)
    for i in [17, 19, 21]: pts[i] = {"x": l_wrist[0], "y": l_wrist[1] + 0.02}
    for i in [18, 20, 22]: pts[i] = {"x": r_wrist[0], "y": r_wrist[1] + 0.02}

    # Legs
    pts[25] = {"x": l_knee[0], "y": l_knee[1]}
    pts[26] = {"x": r_knee[0], "y": r_knee[1]}
    pts[27] = {"x": l_ankle[0], "y": l_ankle[1]}
    pts[28] = {"x": r_ankle[0], "y": r_ankle[1]}
    
    # Feet (grouped near ankles)
    for i in [29, 31]: pts[i] = {"x": l_ankle[0], "y": l_ankle[1] + 0.025}
    for i in [30, 32]: pts[i] = {"x": r_ankle[0], "y": r_ankle[1] + 0.025}

    return pts

POSE_LIBRARY = {
    1: build_pose(), # Casual Standing
    2: build_pose(   # Victory / Arms Raised
        l_elbow=(0.3, 0.2), r_elbow=(0.7, 0.2),
        l_wrist=(0.2, 0.1), r_wrist=(0.8, 0.1)
    ),
    3: build_pose(   # Hands on Hips
        l_elbow=(0.3, 0.45), r_elbow=(0.7, 0.45),
        l_wrist=(0.42, 0.6), r_wrist=(0.58, 0.6),
        l_hip=(0.45, 0.62), r_hip=(0.55, 0.62)
    ),
    4: build_pose(   # Sitting Cross-Legged
        head_y=0.45,
        l_shoulder=(0.4, 0.55), r_shoulder=(0.6, 0.55),
        l_hip=(0.45, 0.75), r_hip=(0.55, 0.75),
        l_knee=(0.3, 0.8), r_knee=(0.7, 0.8),
        l_ankle=(0.5, 0.85), r_ankle=(0.5, 0.85),
        l_elbow=(0.35, 0.65), r_elbow=(0.65, 0.65),
        l_wrist=(0.45, 0.75), r_wrist=(0.55, 0.75)
    ),
    5: build_pose(   # Dynamic Running
        l_elbow=(0.3, 0.4), r_elbow=(0.75, 0.45),
        l_wrist=(0.4, 0.3), r_wrist=(0.85, 0.5),
        l_knee=(0.45, 0.65), r_knee=(0.6, 0.75),
        l_ankle=(0.45, 0.85), r_ankle=(0.65, 0.7)
    ),
    6: build_pose(   # Looking Back (Profile shift)
        head_x=0.4,
        l_shoulder=(0.45, 0.3), r_shoulder=(0.55, 0.3),
        l_elbow=(0.5, 0.45), r_elbow=(0.6, 0.45),
        l_wrist=(0.5, 0.6), r_wrist=(0.55, 0.6),
        l_hip=(0.48, 0.6), r_hip=(0.52, 0.6),
        l_knee=(0.48, 0.75), r_knee=(0.55, 0.75),
        l_ankle=(0.48, 0.9), r_ankle=(0.55, 0.9)
    ),
    7: build_pose(   # Relaxed Leaning Wall
        head_x=0.45, head_y=0.22,
        l_shoulder=(0.35, 0.32), r_shoulder=(0.55, 0.3),
        l_elbow=(0.35, 0.5), r_elbow=(0.65, 0.45),
        l_wrist=(0.42, 0.65), r_wrist=(0.6, 0.6),
        l_hip=(0.42, 0.6), r_hip=(0.55, 0.6),
        l_knee=(0.42, 0.75), r_knee=(0.6, 0.72),
        l_ankle=(0.42, 0.9), r_ankle=(0.5, 0.9)
    ),
    8: build_pose(   # Sitting on Chair
        head_y=0.3,
        l_shoulder=(0.4, 0.4), r_shoulder=(0.6, 0.4),
        l_hip=(0.45, 0.6), r_hip=(0.55, 0.6),
        l_knee=(0.45, 0.6), r_knee=(0.7, 0.65),
        l_ankle=(0.45, 0.85), r_ankle=(0.7, 0.85),
        l_elbow=(0.35, 0.5), r_elbow=(0.65, 0.5),
        l_wrist=(0.45, 0.6), r_wrist=(0.65, 0.6)
    )
}

POSE_DESCRIPTIONS = """
1: Casual Standing (Default)
2: Victory / Arms Raised in air
3: Hands on Hips (Confident)
4: Sitting Cross-Legged on ground
5: Dynamic Action / Running / Mid-air
6: Looking Back / Profile stance
7: Relaxed Leaning against a wall/edge
8: Sitting on a chair/bench
"""
