package game.visual;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;

import static game.config.GamePanelConstants.CRT_BLEED_BLUE_OP;
import static game.config.GamePanelConstants.CRT_BLEED_GREEN_OP;
import static game.config.GamePanelConstants.CRT_BLEED_INTENSITY;
import static game.config.GamePanelConstants.CRT_BLEED_OFFSET_X;
import static game.config.GamePanelConstants.CRT_BLEED_OFFSET_Y;
import static game.config.GamePanelConstants.CRT_BLEED_RED_OP;
import static game.config.GamePanelConstants.CRT_VERTICAL_CURVE_STRENGTH;
import static game.config.GamePanelConstants.CRT_WARP_STRENGTH;
import static game.config.GamePanelConstants.CRT_WARP_STRIP_PX;

/**
 * CRT-style warp, color bleed, brightness, and screen overlay for the main view.
 */
public final class CrtDisplay {
    private BufferedImage crtWarpBuffer;
    private BufferedImage crtOverlayBuffer;
    private BufferedImage crtMatteBuffer;
    private BufferedImage crtBleedBuffer;
    private BufferedImage crtBrightnessBuffer;
    private RescaleOp crtBrightnessOp;
    private float lastCrtBrightnessGain = -1f;
    private int[] crtRowXs;
    private int[] crtRowWidths;
    private int[] crtColTops;
    private int[] crtColBottoms;

    public void ensureGeometry(int targetWidth, int targetHeight) {
        boolean sizeChanged = crtRowXs == null
                || crtRowXs.length != targetHeight
                || crtColTops == null
                || crtColTops.length != targetWidth;
        if (!sizeChanged) {
            return;
        }

        crtRowXs = new int[targetHeight];
        crtRowWidths = new int[targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            double normalizedY = ((y + 0.5) / targetHeight) * 2.0 - 1.0;
            double curveFactor = 1.0 - (CRT_WARP_STRENGTH * (normalizedY * normalizedY));
            int rowWidth = Math.max(1, (int) Math.round(targetWidth * curveFactor));
            crtRowWidths[y] = rowWidth;
            crtRowXs[y] = (targetWidth - rowWidth) / 2;
        }

        crtColTops = new int[targetWidth];
        crtColBottoms = new int[targetWidth];
        for (int x = 0; x < targetWidth; x++) {
            double normalizedX = ((x + 0.5) / targetWidth) * 2.0 - 1.0;
            int verticalInset = (int) Math.round(targetHeight * CRT_VERTICAL_CURVE_STRENGTH * (normalizedX * normalizedX));
            crtColTops[x] = verticalInset;
            crtColBottoms[x] = targetHeight - verticalInset;
        }

        crtOverlayBuffer = null;
        crtMatteBuffer = null;
    }

    public void drawCurvedScreenImage(
            Graphics2D g2d,
            BufferedImage source,
            int targetWidth,
            int targetHeight,
            boolean crtBleedEnabled,
            float crtBrightnessGain
    ) {
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, targetWidth, targetHeight);

        BufferedImage warpSource = source;
        if (crtBleedEnabled) {
            warpSource = buildCrtBleedBuffer(source);
        }
        if (Math.abs(crtBrightnessGain - 1.0f) > 0.001f) {
            warpSource = applyCrtBrightness(warpSource, crtBrightnessGain);
        }

        if (crtWarpBuffer == null || crtWarpBuffer.getWidth() != targetWidth || crtWarpBuffer.getHeight() != targetHeight) {
            crtWarpBuffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D warpG = crtWarpBuffer.createGraphics();
        warpG.setComposite(AlphaComposite.Src);
        warpG.setColor(new Color(0, 0, 0, 0));
        warpG.fillRect(0, 0, targetWidth, targetHeight);

        for (int y = 0; y < targetHeight; y += CRT_WARP_STRIP_PX) {
            int stripHeight = Math.min(CRT_WARP_STRIP_PX, targetHeight - y);
            int rowWidth = crtRowWidths[y];
            int rowX = crtRowXs[y];

            int srcY0 = (int) ((y / (double) targetHeight) * warpSource.getHeight());
            int srcY1 = (int) (((y + stripHeight) / (double) targetHeight) * warpSource.getHeight());
            if (srcY1 <= srcY0) {
                srcY1 = Math.min(warpSource.getHeight(), srcY0 + 1);
            }

            warpG.drawImage(
                    warpSource,
                    rowX,
                    y,
                    rowX + rowWidth,
                    y + stripHeight,
                    0,
                    srcY0,
                    warpSource.getWidth(),
                    srcY1,
                    null
            );
        }
        warpG.dispose();

        for (int x = 0; x < targetWidth; x += CRT_WARP_STRIP_PX) {
            int stripWidth = Math.min(CRT_WARP_STRIP_PX, targetWidth - x);
            int destTop = crtColTops[x];
            int destBottom = crtColBottoms[x];
            if (destBottom <= destTop) {
                continue;
            }

            g2d.drawImage(
                    crtWarpBuffer,
                    x,
                    destTop,
                    x + stripWidth,
                    destBottom,
                    x,
                    0,
                    x + stripWidth,
                    targetHeight,
                    null
            );
        }
    }

    public void drawMatte(Graphics2D g2d, int targetWidth, int targetHeight) {
        if (crtMatteBuffer == null || crtMatteBuffer.getWidth() != targetWidth || crtMatteBuffer.getHeight() != targetHeight) {
            crtMatteBuffer = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D matteG = crtMatteBuffer.createGraphics();
            matteG.setComposite(AlphaComposite.Src);
            matteG.setColor(new Color(0, 0, 0, 0));
            matteG.fillRect(0, 0, targetWidth, targetHeight);
            matteG.setColor(Color.BLACK);
            for (int y = 0; y < targetHeight; y++) {
                int rowWidth = crtRowWidths[y];
                int rowX = crtRowXs[y];
                if (rowX > 0) {
                    matteG.drawLine(0, y, rowX, y);
                    matteG.drawLine(rowX + rowWidth, y, targetWidth, y);
                }
            }
            for (int x = 0; x < targetWidth; x++) {
                int verticalInset = crtColTops[x];
                if (verticalInset > 0) {
                    matteG.drawLine(x, 0, x, verticalInset);
                    matteG.drawLine(x, targetHeight - verticalInset, x, targetHeight);
                }
            }
            matteG.dispose();
        }
        g2d.drawImage(crtMatteBuffer, 0, 0, null);
    }

    public void applyOverlay(Graphics2D g2d, int width, int height, boolean crtScanlinesEnabled) {
        if (crtOverlayBuffer == null || crtOverlayBuffer.getWidth() != width || crtOverlayBuffer.getHeight() != height) {
            crtOverlayBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D overlayG = crtOverlayBuffer.createGraphics();
            overlayG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            overlayG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            overlayG.setComposite(AlphaComposite.Src);
            overlayG.setColor(new Color(0, 0, 0, 0));
            overlayG.fillRect(0, 0, width, height);
            drawCrtGlow(overlayG, width, height);
            drawCrtMask(overlayG, width, height);
            if (crtScanlinesEnabled) {
                drawCrtScanlines(overlayG, width, height);
            }
            drawCrtVignette(overlayG, width, height);
            overlayG.dispose();
        }
        g2d.drawImage(crtOverlayBuffer, 0, 0, null);
    }

    private BufferedImage buildCrtBleedBuffer(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (crtBleedBuffer == null || crtBleedBuffer.getWidth() != width || crtBleedBuffer.getHeight() != height) {
            crtBleedBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D bleedG = crtBleedBuffer.createGraphics();
        bleedG.setComposite(AlphaComposite.Src);
        bleedG.setColor(new Color(0, 0, 0, 0));
        bleedG.fillRect(0, 0, width, height);
        bleedG.drawImage(source, 0, 0, null);

        bleedG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CRT_BLEED_INTENSITY));
        bleedG.drawImage(source, CRT_BLEED_RED_OP, CRT_BLEED_OFFSET_X, 0);
        bleedG.drawImage(source, CRT_BLEED_GREEN_OP, 0, CRT_BLEED_OFFSET_Y);
        bleedG.drawImage(source, CRT_BLEED_BLUE_OP, -CRT_BLEED_OFFSET_X, 0);
        bleedG.dispose();

        return crtBleedBuffer;
    }

    private BufferedImage applyCrtBrightness(BufferedImage source, float crtBrightnessGain) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (crtBrightnessBuffer == null || crtBrightnessBuffer.getWidth() != width || crtBrightnessBuffer.getHeight() != height) {
            crtBrightnessBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }
        getCrtBrightnessOp(crtBrightnessGain).filter(source, crtBrightnessBuffer);
        return crtBrightnessBuffer;
    }

    private RescaleOp getCrtBrightnessOp(float crtBrightnessGain) {
        if (crtBrightnessOp == null || Math.abs(lastCrtBrightnessGain - crtBrightnessGain) > 0.001f) {
            lastCrtBrightnessGain = crtBrightnessGain;
            crtBrightnessOp = new RescaleOp(
                    new float[]{crtBrightnessGain, crtBrightnessGain, crtBrightnessGain, 1f},
                    new float[]{0f, 0f, 0f, 0f},
                    null
            );
        }
        return crtBrightnessOp;
    }

    private void drawCrtGlow(Graphics2D g2d, int width, int height) {
        Composite oldComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
        g2d.setColor(new Color(120, 220, 255, 255));
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(oldComposite);
    }

    private void drawCrtMask(Graphics2D g2d, int width, int height) {
        for (int x = 0; x < width; x += 3) {
            g2d.setColor(new Color(255, 70, 70, 16));
            g2d.drawLine(x, 0, x, height);
            if (x + 1 < width) {
                g2d.setColor(new Color(120, 255, 120, 14));
                g2d.drawLine(x + 1, 0, x + 1, height);
            }
            if (x + 2 < width) {
                g2d.setColor(new Color(110, 190, 255, 16));
                g2d.drawLine(x + 2, 0, x + 2, height);
            }
        }
    }

    private void drawCrtScanlines(Graphics2D g2d, int width, int height) {
        for (int y = 0; y < height; y += 3) {
            g2d.setColor(new Color(0, 6, 16, 78));
            g2d.fillRect(0, y, width, 1);
        }
        for (int y = 1; y < height; y += 6) {
            g2d.setColor(new Color(140, 220, 255, 18));
            g2d.drawLine(0, y, width, y);
        }
    }

    private void drawCrtVignette(Graphics2D g2d, int width, int height) {
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        float radius = Math.max(width, height) * 0.72f;
        RadialGradientPaint vignette = new RadialGradientPaint(
                centerX,
                centerY,
                radius,
                new float[]{0.0f, 0.72f, 1.0f},
                new Color[]{
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 35),
                        new Color(0, 0, 0, 130)
                }
        );
        g2d.setPaint(vignette);
        g2d.fillRect(0, 0, width, height);
        drawCrtCornerCurve(g2d, width, height);
    }

    private void drawCrtCornerCurve(Graphics2D g2d, int width, int height) {
        int arc = Math.max(36, Math.min(width, height) / 7);
        Area outer = new Area(new Rectangle(0, 0, width, height));
        Area inner = new Area(new RoundRectangle2D.Double(3, 3, width - 6.0, height - 6.0, arc, arc));
        outer.subtract(inner);
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fill(outer);

        g2d.setColor(new Color(170, 240, 255, 0));
        g2d.draw(new RoundRectangle2D.Double(2, 2, width - 5.0, height - 5.0, arc, arc));
    }
}
