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
package nu.zoom.jme.inspector.jmx;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import nu.zoom.jme.inspector.common.JMETerrainGridInspectorMBean;
import nu.zoom.jme.inspector.common.TerrainQuadInformation;

/**
 *
 * @author Johan Maasing
 */
public final class TerrainQuadRefresher {

    private final static int SLEEP_TIME = 500;
    private final Logger log = Logger.getLogger(getClass().getName());
    private final CopyOnWriteArraySet<TerrainQuadRefresherListener> listeners =
            new CopyOnWriteArraySet<TerrainQuadRefresherListener>();
    private final AtomicBoolean refresherShouldRun = new AtomicBoolean(false);
    private Thread refresherThread = null;
    private final JMETerrainGridInspectorMBean inspector;

    public TerrainQuadRefresher(
            final JMETerrainGridInspectorMBean inspector) {
        if (inspector == null) {
            throw new IllegalArgumentException("Inspector may not be null");
        }
        this.inspector = inspector;
    }

    public void addListener(final TerrainQuadRefresherListener listener) {
        if (listener != null) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(final TerrainQuadRefresherListener listener) {
        if (listener != null) {
            this.listeners.remove(listener);
        }
    }

    public void stop() {
        log.log(Level.FINE, "Terrain refresher signalled to stop.");
        this.refresherShouldRun.set(false);
    }

    public void start() {
        boolean wasSet = refresherShouldRun.compareAndSet(false, true);
        if (wasSet) {
            if (this.refresherThread == null) {

                this.refresherThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (refresherShouldRun.get()) {
                            try {
                                TerrainQuadInformation terrainQuadInformation =
                                        inspector.getTerrainQuadInformation();
                                if (terrainQuadInformation != null) {
                                    // TODO: check if we really need to refresh
                                    for (final TerrainQuadRefresherListener listener : listeners) {
                                        listener.newTerrainQuad(terrainQuadInformation);
                                    }
                                }
                                try {
                                    Thread.sleep(SLEEP_TIME);
                                } catch (InterruptedException ex) {
                                    log.log(Level.INFO, "Terrain refresher interrupted", ex);
                                    refresherShouldRun.set(false);
                                    Thread.currentThread().interrupt();
                                }
                            } catch (final Throwable e) {
                                log.log(Level.SEVERE, "Unable to get terrain information", e);
                                refresherShouldRun.set(false);
                            }
                        }
                        for (final TerrainQuadRefresherListener listener : listeners) {
                            listener.disconnected();
                        }
                    }
                }, "TerrainQuadRefresher thread");
                this.refresherThread.start();
            } else {
                log.log(Level.SEVERE, "Start called but refresher thread exists.");
            }
        } else {
            log.log(Level.WARNING, "Start called but start flag was already set, concurrency problems?");
        }
    }
}
