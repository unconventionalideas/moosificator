package org.sexyideas.moosificator;

import jjil.core.Rect;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

/**
 * @author spencer.firestone
 */
public class MooseImage {

    private BufferedImage image;
    private float headLeftOffset;
    private float headTopOffset;
    private float magnifyingFactor;
    private float mooseProportionRatio;

    public MooseImage(BufferedImage image, float headLeftOffset, float headTopOffset, float headHeight) {
        this.image = image;
        this.headLeftOffset = headLeftOffset;
        this.headTopOffset = headTopOffset;
        this.magnifyingFactor = (float) image.getHeight() / headHeight;
        this.mooseProportionRatio = (float) image.getWidth() / (float) image.getHeight();
    }

    public void drawImage(Graphics g, Rect rectangle) {
        float effectiveHeight = rectangle.getHeight() * this.magnifyingFactor;
        float effectiveWidth = effectiveHeight * this.mooseProportionRatio;
        float effectiveTop = rectangle.getTop() - this.headTopOffset * effectiveHeight / this.image.getHeight();
        float effectiveLeft = rectangle.getLeft() - this.headLeftOffset * effectiveWidth / this.image.getWidth();
        g.drawImage(this.image, (int) effectiveLeft, (int) effectiveTop, (int) effectiveWidth, (int) effectiveHeight, null);
    }
}
