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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Timer;
import nu.zoom.jme.inspector.common.JMETerrainGridInspectorMBean;
import nu.zoom.jme.inspector.common.JMXNames;
import nu.zoom.jme.inspector.heightfield.HeightFieldVisualizerTopComponent;

/**
 *
 * @author Johan Maasing <johan@zoom.nu>
 */
public final class AppFinder extends DefaultComboBoxModel<ObjectName> {

    private final Logger log = Logger.getLogger(getClass().getName());
    private final MBeanServer server;
    private final Timer refreshTimer;
    private final AtomicBoolean backgroundOperationInProgress = new AtomicBoolean(false);
    private final HeightFieldVisualizerTopComponent owner;

    public AppFinder(
            final MBeanServer server,
            final HeightFieldVisualizerTopComponent owner) {
        this.server = server;
        this.owner = owner;
        this.refreshTimer = new Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refresh();
            }
        });
    }

    private void refresh() {
        if (!this.backgroundOperationInProgress.get()) {
            try {
                ObjectName searchname = new ObjectName(JMXNames.TERRAIN_INSPECTOR_OBJECTNAME);
                final Set<ObjectName> registeredNames = this.server.queryNames(searchname, null);
                log.log(Level.FINER, "Found {0} registered MBeans", new Object[]{registeredNames.size()});
                // Brute force, reload the entire list every time. Can be optimized :)
                this.removeAllElements();
                for (ObjectName objectName : registeredNames) {
                    this.addElement(objectName);
                }
            } catch (MalformedObjectNameException ex) {
                log.log(Level.SEVERE, "Unable to create MBean name", ex);
            }
        }
    }

    public void stopTimer() {
        this.refreshTimer.stop();
    }

    public void startTimer() {
        if (!this.refreshTimer.isRunning()) {
            this.refreshTimer.start();
        }
    }

    /**
     * Should be called on the event/swing thread
     *
     * @param serverURLString
     * @param remoteOperationProgressbar
     */
    public void attach(
            final String serverURLString) {
        final boolean couldStart = this.backgroundOperationInProgress.compareAndSet(false, true);
        if (couldStart) {
            Thread attachThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    owner.indicateBackgroundOperation(true);
                    try {
                        final JMXServiceURL serviceURL = new JMXServiceURL(serverURLString);
                        final JMXConnector connector =
                                JMXConnectorFactory.connect(serviceURL);
                        final MBeanServerConnection mBeanServerConnection =
                                connector.getMBeanServerConnection();
                        final ObjectName searchname =
                                new ObjectName(JMXNames.TERRAIN_INSPECTOR_OBJECTNAME);
                        final JMETerrainGridInspectorMBean terrainGridInspector =
                                MBeanServerInvocationHandler.newProxyInstance(
                                mBeanServerConnection,
                                searchname,
                                JMETerrainGridInspectorMBean.class,
                                false);
                        attached(terrainGridInspector);
                    } catch (Exception ex) {
                        attachementError(ex);
                        log.log(Level.INFO, "Unable to connect to {0}", new Object[]{serverURLString});
                    }
                }
            }, "JMX Attach thread");
            attachThread.setDaemon(true);
            attachThread.start();
        }
    }

    /**
     * Can be called on the background thread
     */
    private void attached(JMETerrainGridInspectorMBean terrainGridInspector) {
        boolean couldReset = this.backgroundOperationInProgress.compareAndSet(true, false);
        if (!couldReset) {
            log.severe("Concurrency problem: Unable to reset background operation flag");
        }
        owner.indicateBackgroundOperation(!couldReset);
        owner.setTerrainGridInspector(terrainGridInspector);
    }

    private void attachementError(Exception ex) {
        boolean couldReset = this.backgroundOperationInProgress.compareAndSet(true, false);
        if (!couldReset) {
            log.severe("Concurrency problem: Unable to reset background operation flag");
        }
        owner.indicateBackgroundOperation(false);
        owner.indicateConnectionError(ex);
    }
}
