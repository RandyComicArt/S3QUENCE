package game.util;

import game.config.GamePanelConstants;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Classpath and filesystem image loading for game assets.
 */
public final class GameImageLoader {
    private GameImageLoader() {
    }

    public static BufferedImage loadImage(Class<?> anchor, String fileName) {
        BufferedImage fromClasspath = loadFromClasspath(anchor, fileName);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return loadFromFiles(fileName);
    }

    public static Image loadAnimatedImage(Class<?> anchor, String fileName) {
        URL classpathUrl = anchor.getClassLoader().getResource("assets/" + fileName);
        if (classpathUrl != null) {
            return new ImageIcon(classpathUrl).getImage();
        }

        File[] candidates = {
                new File("src/assets/" + fileName),
                new File("assets/" + fileName)
        };
        for (File file : candidates) {
            if (file.isFile()) {
                return new ImageIcon(file.getAbsolutePath()).getImage();
            }
        }
        return null;
    }

    public static long loadGifDurationMillis(Class<?> anchor, String fileName) {
        URL classpathUrl = anchor.getClassLoader().getResource("assets/" + fileName);
        if (classpathUrl != null) {
            try (ImageInputStream stream = ImageIO.createImageInputStream(classpathUrl.openStream())) {
                long durationMs = readGifDurationMillis(stream);
                if (durationMs > 0L) {
                    return durationMs;
                }
            } catch (IOException ignored) {
                // Fall back to a fixed duration if GIF metadata is unavailable.
            }
        }

        File[] candidates = {
                new File("src/assets/" + fileName),
                new File("assets/" + fileName)
        };
        for (File file : candidates) {
            if (!file.isFile()) {
                continue;
            }
            try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
                long durationMs = readGifDurationMillis(stream);
                if (durationMs > 0L) {
                    return durationMs;
                }
            } catch (IOException ignored) {
                return GamePanelConstants.OPENING_STATIC_SEQUENCE_FALLBACK_MS;
            }
        }

        return GamePanelConstants.OPENING_STATIC_SEQUENCE_FALLBACK_MS;
    }

    private static long readGifDurationMillis(ImageInputStream stream) throws IOException {
        if (stream == null) {
            return 0L;
        }

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return 0L;
        }

        ImageReader reader = readers.next();
        try {
            reader.setInput(stream, false, false);
            int frameCount = reader.getNumImages(true);
            long durationMs = 0L;
            for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
                IIOMetadata metadata = reader.getImageMetadata(frameIndex);
                Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());
                durationMs += extractGifFrameDelayMillis(root);
            }
            return durationMs;
        } finally {
            reader.dispose();
        }
    }

    private static long extractGifFrameDelayMillis(Node metadataRoot) {
        if (metadataRoot == null) {
            return 0L;
        }

        Node graphicsControlExtension = findMetadataNode(metadataRoot, "GraphicControlExtension");
        if (graphicsControlExtension == null) {
            return 0L;
        }

        NamedNodeMap attributes = graphicsControlExtension.getAttributes();
        if (attributes == null) {
            return 0L;
        }

        Node delayNode = attributes.getNamedItem("delayTime");
        if (delayNode == null) {
            return 0L;
        }

        try {
            return Long.parseLong(delayNode.getNodeValue()) * 10L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Node findMetadataNode(Node node, String targetName) {
        if (node == null) {
            return null;
        }
        if (targetName.equals(node.getNodeName())) {
            return node;
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node match = findMetadataNode(child, targetName);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static BufferedImage loadFromClasspath(Class<?> anchor, String fileName) {
        URL url = anchor.getClassLoader().getResource("assets/" + fileName);
        if (url == null) {
            return null;
        }
        try {
            return toArgbImage(ImageIO.read(url));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static BufferedImage loadFromFiles(String fileName) {
        File[] candidates = {
                new File("src/assets/" + fileName),
                new File("assets/" + fileName)
        };

        for (File file : candidates) {
            if (!file.isFile()) {
                continue;
            }
            try {
                return toArgbImage(ImageIO.read(file));
            } catch (IOException ignored) {
                return null;
            }
        }
        return null;
    }

    public static BufferedImage toArgbImage(BufferedImage image) {
        if (image == null) {
            return null;
        }
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = converted.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();
        return converted;
    }

    public static BufferedImage tintSprite(BufferedImage sprite, Color tint) {
        BufferedImage tinted = new BufferedImage(
                sprite.getWidth(),
                sprite.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2d = tinted.createGraphics();
        g2d.drawImage(sprite, 0, 0, null);
        g2d.setComposite(AlphaComposite.SrcIn);
        g2d.setColor(tint);
        g2d.fillRect(0, 0, sprite.getWidth(), sprite.getHeight());
        g2d.dispose();
        return tinted;
    }
}
