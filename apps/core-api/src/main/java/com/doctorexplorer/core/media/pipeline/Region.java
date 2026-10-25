package com.doctorexplorer.core.media.pipeline;

/** Retângulo em pixels da imagem de saída. */
public record Region(int x, int y, int w, int h) {

    public Region clip(int width, int height) {
        int x0 = Math.max(0, x), y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w), y1 = Math.min(height, y + h);
        return new Region(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    public Region pad(int p) {
        return new Region(x - p, y - p, w + 2 * p, h + 2 * p);
    }
}
