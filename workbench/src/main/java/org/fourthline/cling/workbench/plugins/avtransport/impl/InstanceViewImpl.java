/*
 * Copyright (C) 2011 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.fourthline.cling.workbench.plugins.avtransport.impl;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.workbench.Workbench;
import org.fourthline.cling.workbench.plugins.avtransport.InstanceView;
import org.seamless.statemachine.StateMachineBuilder;
import org.seamless.swing.logging.LogMessage;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.util.logging.Level;

/**
 * @author Christian Bauer
 */
public class InstanceViewImpl extends JPanel implements InstanceView {

    final protected PlayerPanel playerPanel = new PlayerPanel();
    final protected ProgressPanel progressPanel = new ProgressPanel();
    final protected URIPanel uriPanel = new URIPanel();

    protected InstanceViewStateMachine viewStateMachine;
    protected int instanceId;
    protected Presenter presenter;

    @Override
    public void init(int instanceId) {

        this.instanceId = instanceId;

        this.viewStateMachine = StateMachineBuilder.build(
                InstanceViewStateMachine.class,
                NoMediaPresent.class,
                new Class[]{InstanceViewImpl.class},
                new Object[]{this}
        );

        playerPanel.getFwdButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                presenter.onSeekSelected(getInstanceId(), 15, true);
            }
        });

        playerPanel.getRewButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                presenter.onSeekSelected(getInstanceId(), 15, false);
            }
        });

        playerPanel.getPauseButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                // TODO: Should "Pause" when already paused send a "Play" action or another "Pause" action?
                presenter.onPauseSelected(getInstanceId());
            }
        });

        playerPanel.getStopButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                presenter.onStopSelected(getInstanceId());
            }
        });

        playerPanel.getPlayButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                presenter.onPlaySelected(getInstanceId());

            }
        });

        progressPanel.getPositionSlider().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                final JSlider source = (JSlider) e.getSource();
                if (source.getValueIsAdjusting()) return;
                PositionInfo positionInfo = getProgressPanel().getPositionInfo();
                if (positionInfo != null) {
                    int newValue = source.getValue();
                    double seekTargetSeconds = newValue * positionInfo.getTrackDurationSeconds() / 100;
                    final String targetTime =
                            ModelUtil.toTimeString(
                                    new Long(Math.round(seekTargetSeconds)).intValue()
                            );

                    presenter.onSeekSelected(getInstanceId(), targetTime);
                }
            }
        });

        uriPanel.getSetButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                // Some validation
                final String uri = uriPanel.getUriTextField().getText();
                if (uri == null || uri.length() == 0) return;
                try {
                    URI.create(uri);
                } catch (IllegalArgumentException ex) {
                    Workbench.log(new LogMessage(
                            Level.WARNING, "AVTransport ControlPoint", "Invalid URI, can't set on AVTransport: " + uri
                    ));
                }
                presenter.onSetAVTransportURISelected(getInstanceId(), uri);
            }
        });

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(playerPanel);
        add(progressPanel);
        add(uriPanel);
    }

    @Override
    public Component asUIComponent() {
        return this;
    }

    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }

    // All the other methods are called by the GENA subscriptions with synchronization, this
    // is called by other people as well, so we synchronize
    @Override
    synchronized public void setState(TransportState state) {
        Class<? extends InstanceViewState> newClientState = InstanceViewState.STATE_MAP.get(state);
        if (newClientState != null) {
            try {
                viewStateMachine.forceState(newClientState);
            } catch (Exception ex) {
                Workbench.log(Level.SEVERE, "Error switching client instance state: " + ex);
            }
        }
    }

    @Override
    public PositionInfo getProgress() {
        return progressPanel.getPositionInfo();
    }

    @Override
    public void setProgress(PositionInfo positionInfo) {
        progressPanel.setProgress(positionInfo);
    }

    @Override
    public void setCurrentTrackURI(String uri) {
        uriPanel.getUriTextField().setText(uri);
    }

    @Override
    public void setSelectionEnabled(boolean enabled) {
        playerPanel.setBorder(BorderFactory.createTitledBorder(enabled ? "" : "DISABLED"));
        playerPanel.setAllButtons(enabled);
        progressPanel.getPositionSlider().setEnabled(enabled);
    }

    @Override
    public void dispose() {
        // End everything we do (background polling)
        setState(TransportState.STOPPED);
    }

    public int getInstanceId() {
        return instanceId;
    }

    public PlayerPanel getPlayerPanel() {
        return playerPanel;
    }

    public ProgressPanel getProgressPanel() {
        return progressPanel;
    }

    public URIPanel getUriPanel() {
        return uriPanel;
    }

    public Presenter getPresenter() {
        return presenter;
    }
}