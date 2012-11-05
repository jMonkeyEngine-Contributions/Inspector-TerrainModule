/*
 * Copyright (c) 2012, "Johan Maasing" <johan@zoom.nu>
 * All rights reserved.

 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package nu.zoom.jme.inspector.heightfield;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.beans.*;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JComponent;
import nu.zoom.jme.inspector.common.TerrainQuadInformation;
import nu.zoom.jme.inspector.jmx.TerrainQuadRefresherListener;

/**
 * GUI element to draw a float[] as a grey scale image. Please set the image
 * size property before drawing a float array.
 *
 * @author Johan Maasing <johan@zoom.nu>
 */
public class HeightFieldImageBean extends JComponent implements Serializable, TerrainQuadRefresherListener {

    private final Logger log = Logger.getLogger(getClass().getName());
    public static final String IMAGE_SIZE_PROPERTY = "sampleProperty";
    private int imageSizeProperty = 256;
    private PropertyChangeSupport propertySupport;
    private final BufferedImage image;
    private final int bkgrndTileSize = 10;
    private float range = 0f;
    public static final String RANGE_PROPERTY = "range";

    public HeightFieldImageBean() {
        propertySupport = new PropertyChangeSupport(this);
        this.setMinimumSize(new Dimension(48, 48));
        this.setPreferredSize(new Dimension(imageSizeProperty, imageSizeProperty));
        this.setMaximumSize(new Dimension(512, 512));
        this.image = new BufferedImage(imageSizeProperty, imageSizeProperty, BufferedImage.TYPE_INT_ARGB);
    }

    public int getImageSizeProperty() {
        return imageSizeProperty;
    }

    public void setImageSizeProperty(int value) {
        int oldValue = imageSizeProperty;
        imageSizeProperty = value;
        this.setPreferredSize(new Dimension(value, value));
        propertySupport.firePropertyChange(IMAGE_SIZE_PROPERTY, oldValue, imageSizeProperty);
    }

    public float getRange() {
        return range;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(listener);
    }

    @Override
    protected void paintComponent(Graphics grphcs) {
        super.paintComponent(grphcs);
        drawBackground(grphcs);
        grphcs.drawImage(this.image, 0, 0, null);
    }

    @Override
    public boolean isOpaque() {
        return true;
    }

    private void drawBackground(Graphics grphcs) {
        Rectangle clipBounds = grphcs.getClipBounds();
        int clipWidth = Math.min(clipBounds.width, this.imageSizeProperty);
        int clipHeight = Math.min(clipBounds.height, this.imageSizeProperty);
        grphcs.setClip(clipBounds.x, clipBounds.y, clipWidth, clipHeight);
        final int bkgrndTileHeight = (this.imageSizeProperty / bkgrndTileSize) + 1;
        final int bkgrndTileWidth = (this.imageSizeProperty / bkgrndTileSize) + 1;
        for (int y = 0; y < bkgrndTileHeight; y++) {
            boolean alternate = ((y % 2) == 0);

            for (int x = 0; x < bkgrndTileWidth; x++) {
                if (alternate) {
                    grphcs.setColor(Color.RED);
                } else {
                    grphcs.setColor(Color.YELLOW);
                }
                grphcs.fillRect(x * bkgrndTileSize, y * bkgrndTileSize, bkgrndTileSize, bkgrndTileSize);
                alternate = !alternate;
            }
        }
    }

    public void drawImage(float[] values) {
        if (values == null) {
            log.warning("drawImage called with null as values array");
        } else if (values.length != (this.imageSizeProperty * this.imageSizeProperty)) {
            log.log(
                    Level.WARNING,
                    "drawImage called with values array that does not match imageSize property: {0}",
                    this.imageSizeProperty);
        } else {
            float min = 1;
            float max = 1;
            for (int y = 0; y < this.imageSizeProperty; y++) {
                for (int x = 0; x < this.imageSizeProperty; x++) {
                    float value = values[x + y * this.imageSizeProperty];
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
            }
            this.range = max - min;

            Graphics2D g = this.image.createGraphics();
            for (int y = 0; y < this.imageSizeProperty; y++) {
                for (int x = 0; x < this.imageSizeProperty; x++) {
                    float value = values[x + y * this.imageSizeProperty];
                    float alpha = 1.0f;
                    if (Float.isInfinite(value)) {
                        log.fine("Values array contain infinite value");
                        alpha = 0.0f;
                        value = 1.0f;
                    } else if (Float.isNaN(max)) {
                        log.fine("Values array contain NaN value");
                        alpha = 0.0f;
                        value = 0.0f;
                    } else {
                        value -= min;
                    }
                    final float component = value / this.range;
                    g.setColor(new Color(component, component, component, alpha));
                    g.fillRect(x, y, 1, 1);
                }
            }
            repaint();
        }
    }

    @Override
    public void newTerrainQuad(TerrainQuadInformation terrainQuadInformation) {
        if (terrainQuadInformation != null) {
            final int size = terrainQuadInformation.getSize();
            if (getImageSizeProperty() != size) {
                setImageSizeProperty(size);
            }
            float[] heightmap = terrainQuadInformation.getHeightmap();
            if (heightmap != null) {
                drawImage(heightmap);
            }
        }
    }

    @Override
    public void disconnected() {
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                repaint();
            }
        });
    }
}
