package com.surins;

/** Screen anchors and display sizing, independent of Minecraft rendering. */
final class HudLayoutMath {
    private HudLayoutMath() {}

    static float scaleCompensation(double guiScale) {
        // A modest 15% boost at 3x; retain the original appearance at 4x and above.
        return 1.0f + 0.15f * (float) Math.max(0.0, Math.min(1.0, 4.0 - guiScale));
    }

    static float nearestAnchor(float position, float elementSize, int screenSize) {
        float center = position + elementSize / 2.0f;
        return center < screenSize * 0.25f ? 0.0f : center > screenSize * 0.75f ? 1.0f : 0.5f;
    }

    static float offset(float position, float elementSize, int screenSize, float anchor) {
        return position - anchor * (screenSize - elementSize);
    }

    static float resolve(float offset, float elementSize, int screenSize, float anchor) {
        return clamp(anchor * (screenSize - elementSize) + offset, elementSize, screenSize);
    }

    static float clamp(float position, float elementSize, int screenSize) {
        return Math.max(0.0f, Math.min(Math.max(0.0f, screenSize - elementSize), position));
    }

    /** Six warning slots, Warden last; zero widths mean hidden or manually positioned. */
    static float[] warningRow(int screenWidth, float[] widths, boolean centerWarden, float clearance) {
        float[] positions = new float[widths.length];
        int warden = widths.length - 1;
        if (centerWarden && widths[warden] > 0) {
            float center = screenWidth / 2.0f;
            positions[warden] = center - widths[warden] / 2.0f;
            float left = center - Math.max(clearance, widths[warden] / 2.0f) - 4;
            float right = center + Math.max(clearance, widths[warden] / 2.0f) + 4;
            float leftUsed = 0, rightUsed = 0;
            for (int i = 0; i < warden; i++) {
                if (widths[i] <= 0) continue;
                if (leftUsed <= rightUsed) {
                    left -= widths[i];
                    positions[i] = left;
                    left -= 4;
                    leftUsed += widths[i] + 4;
                } else {
                    positions[i] = right;
                    right += widths[i] + 4;
                    rightUsed += widths[i] + 4;
                }
            }
        } else {
            float total = 0;
            for (float size : widths) if (size > 0) total += size + 4;
            if (total > 0) total -= 4;
            float x = (screenWidth - total) / 2.0f;
            for (int i = 0; i < widths.length; i++) {
                positions[i] = x;
                if (widths[i] > 0) x += widths[i] + 4;
            }
        }
        return positions;
    }
}
