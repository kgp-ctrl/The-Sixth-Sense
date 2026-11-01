package com.surins;

import java.util.function.IntBinaryOperator;

/** Small, deterministic texture calculations, independent of the renderer. Colors are ARGB. */
final class WhisperRelicMath {
    static final int SIZE = 16, NEUTRAL = 0x9297A5;

    static int eyeColor(int width, int height, IntBinaryOperator pixel) {
        if (width < 64 || width % 64 != 0 || height < width / 2) return NEUTRAL;
        int unit = width / 64;
        int[] face = new int[64];
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
            int c = sample(pixel, unit, x+8, y+8);
            face[y*8+x] = c;
            if ((c >>> 24) >= 192) counts.merge(c & 0xFFFFFF, 1, Integer::sum);
        }
        int cheek = face[6*8+3], best = -1, white = -1;
        double score = 0;
        // Eye rows only, with equal horizontal offsets about the face's vertical centerline.
        for (int y=3;y<=5;y++) for (int x=1;x<=2;x++) {
            int a = face[y*8+x], b = face[y*8+7-x];
            if ((a >>> 24)<192 || (b >>> 24)<192 || distance(a,b)>.06) continue;
            int rgb = a & 0xFFFFFF, count = counts.getOrDefault(rgb, 0);
            if (count < 2 || count > 4 || distance(a,cheek)<.16) continue;
            int r=rgb>>16&255, g=rgb>>8&255, blue=rgb&255;
            int max=Math.max(r,Math.max(g,blue)), min=Math.min(r,Math.min(g,blue));
            if (min>=210 && max-min<=25) { white=rgb; continue; }
            if (max<35) continue;
            double candidate=distance(a,cheek)+(max-min)/255.0+(count==2?.35:0)-(Math.abs(y-4)*.08);
            if(candidate>score) {score=candidate;best=rgb;}
        }
        if(best!=-1)return best;
        if(white!=-1)return white;
        return NEUTRAL;
    }

    private static int sample(IntBinaryOperator pixel, int unit, int x, int y) {
        return pixel.applyAsInt(x * unit + unit / 2, y * unit + unit / 2);
    }

    private static double distance(int a, int b) {
        double r = ((a >> 16 & 255) - (b >> 16 & 255)) / 255.0;
        double g = ((a >> 8 & 255) - (b >> 8 & 255)) / 255.0;
        double blue = ((a & 255) - (b & 255)) / 255.0;
        return Math.sqrt((r*r + g*g + blue*blue) / 3);
    }

    // Hand-placed clusters at native 16x16 resolution; no high-resolution image is downsampled.
    private static final String[] ORB = {
        "0000011111100000", "0001122222211000", "0012333222222100", "0123663222222210",
        "0126532222222210", "1235322222222221", "1232222222222221", "1222222222222221",
        "1222222222222221", "1222222222222721", "1222222222227721", "0122222222777210",
        "0122222227722210", "0012222277222100", "0001122222211000", "0000011111100000"
    };
    private static final int[] PALETTE = {0, 0x050608, 0x0C0D12, 0x262833, 0x454957, 0x9196A5, 0xD9DFE7, 0x30243E};
    private static final double[] TINT = {0, .01, .02, .04, .06, .05, .02, .16};

    private static int material(int index, int accent) {
        if (index==0) return 0;
        int base=PALETTE[index]; double tint=TINT[index];
        int r=(int)((base>>16&255)*(1-tint)+(accent>>16&255)*tint);
        int g=(int)((base>>8&255)*(1-tint)+(accent>>8&255)*tint);
        int b=(int)((base&255)*(1-tint)+(accent&255)*tint);
        return 0xFF000000|r<<16|g<<8|b;
    }
    static int pixel(int x, int y, int accent) { return material(ORB[y].charAt(x)-'0', accent); }

    static int panelPixel(int x, int y) {
        int cx = Math.min(x, 15-x), cy = Math.min(y, 15-y);
        if (cx == 0 && cy == 0) return 0;
        // Square pixels and a restrained bevel, like a vanilla tooltip/item slot.
        if (cx == 0 || cy == 0) return 0xFF08090B;
        if (cy == 1) return y < 8 ? 0xFF55565C : 0xFF25262A;
        if (cx == 1) return x < 8 ? 0xFF3E4046 : 0xFF25262A;
        if (cx == 2 && cy == 2) return 0xFF757782;
        return y < 3 ? 0xFF17181C : 0xFF0D0E11;
    }

    // Matches only vanilla's filled pixels; the container/outline and empty half stay untouched.
    private static final String[] HEART = {
        "000000000", "002202200", "026322220", "053222220", "022222220",
        "002227200", "000277000", "000020000", "000000000"
    };
    static int heartPixel(int x, int y) { return heartPixel(x,y,NEUTRAL); }
    static int heartPixel(int x, int y, int accent) { return material(HEART[y].charAt(x)-'0',accent); }

    static int swapRedBlue(int c) { return c & 0xFF00FF00 | (c & 255) << 16 | c >>> 16 & 255; }
    private WhisperRelicMath() { }
}
