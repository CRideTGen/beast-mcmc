/*
 * PriorDialog.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.app.beauti.priorsPanel;

import dr.app.beauti.options.Parameter;
import dr.app.beauti.options.PartitionClockModel;
import dr.app.beauti.types.PriorType;
import dr.app.gui.chart.Axis;
import dr.app.gui.chart.JChart;
import dr.app.gui.chart.LinearAxis;
import dr.app.gui.chart.PDFPlot;
import dr.app.gui.components.RealNumberField;
import dr.app.util.OSType;
import dr.math.distributions.*;
import dr.util.NumberFormatter;
import jam.panels.OptionsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @author Walter Xie
 * @version $Id: PriorDialog.java,v 1.4 2006/09/05 13:29:34 rambaut Exp $
 */
public class PriorDialog {

    private final JFrame frame;

    private final Map<PriorType, PriorOptionsPanel> optionsPanels = new HashMap<PriorType, PriorOptionsPanel>();

    private final JComboBox priorCombo = new JComboBox();

    private JPanel contentPanel;

    private JLabel citationText;
    private JChart chart;
    private JPanel quantilePanel;
    private JTextArea quantileText;

    private Parameter parameter;

    public PriorDialog(JFrame frame) {
        this.frame = frame;

        optionsPanels.put(PriorType.UNIFORM_PRIOR, PriorOptionsPanel.UNIFORM);
        optionsPanels.put(PriorType.EXPONENTIAL_PRIOR, PriorOptionsPanel.EXPONENTIAL);
        optionsPanels.put(PriorType.LAPLACE_PRIOR, PriorOptionsPanel.LAPLACE);
        optionsPanels.put(PriorType.NORMAL_PRIOR, PriorOptionsPanel.NORMAL);
        optionsPanels.put(PriorType.LOGNORMAL_PRIOR, PriorOptionsPanel.LOG_NORMAL);
        optionsPanels.put(PriorType.GAMMA_PRIOR, PriorOptionsPanel.GAMMA);
        optionsPanels.put(PriorType.INVERSE_GAMMA_PRIOR, PriorOptionsPanel.INVERSE_GAMMA);
        optionsPanels.put(PriorType.BETA_PRIOR, PriorOptionsPanel.BETA);
        optionsPanels.put(PriorType.CMTC_RATE_REFERENCE_PRIOR, PriorOptionsPanel.CTMC_RATE_REFERENCE);
//        optionsPanels.put(PriorType.NORMAL_HPM_PRIOR, new NormalHPMOptionsPanel());
//        optionsPanels.put(PriorType.LOGNORMAL_HPM_PRIOR, new LognormalHPMOptionsPanel());
//        optionsPanels.put(PriorType.GMRF_PRIOR, new GMRFOptionsPanel());

        chart = new JChart(new LinearAxis(Axis.AT_MINOR_TICK, Axis.AT_MINOR_TICK),
                new LinearAxis(Axis.AT_ZERO, Axis.AT_DATA));

        JLabel quantileLabels = new JLabel();
        quantileLabels.setFont(quantileLabels.getFont().deriveFont(10.0f));
        quantileLabels.setOpaque(false);
        quantileLabels.setText("<html><p align=\"right\">Quantiles: 2.5%:<br>5%:<br>Median:<br>95%:<br>97.5%:</p></html>");

        quantileText = new JTextArea(0, 5);
        quantileText.setFont(quantileText.getFont().deriveFont(10.0f));
        quantileText.setOpaque(false);
        quantileText.setEditable(false);
        quantileLabels.setHorizontalAlignment(JLabel.LEFT);

        quantilePanel = new JPanel();
        quantilePanel.add(quantileLabels);
        quantilePanel.add(quantileText);

        citationText = new JLabel();
        citationText.setFont(quantileLabels.getFont().deriveFont(11.0f));
        citationText.setOpaque(false);
        citationText.setText(
              "<html>Approximate continuous time Markov chain rate <br>" +
                    "reference prior developed in Ferreira & Suchard (2008).<br>" +
                    "Use when explicit prior information is unavailable</html>");
    }

    public int showDialog(final Parameter parameter) {

        this.parameter = parameter;

        priorCombo.removeAllItems();
        for (PriorType priorType : PriorType.getPriorTypes(parameter)) {
            priorCombo.addItem(priorType);
        }

        if (parameter.priorType != null) {
            priorCombo.setSelectedItem(parameter.priorType);
        }

        contentPanel = new JPanel(new GridBagLayout());

//        setArguments(priorType); // move to inside setupComponents()
        setupComponents();

        JScrollPane scrollPane = new JScrollPane(contentPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setOpaque(false);

        JOptionPane optionPane = new JOptionPane(scrollPane,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION,
                null,
                null,
                null);
        optionPane.setBorder(new EmptyBorder(12, 12, 12, 12));

        final JDialog dialog = optionPane.createDialog(frame, "Prior for Parameter " + parameter.getName());

        priorCombo.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                setupComponents();
                dialog.validate();
                dialog.repaint();
                dialog.pack();
            }
        });

        for (PriorOptionsPanel optionsPanel : optionsPanels.values()) {
            optionsPanel.addListener(new PriorOptionsPanel.Listener() {
                public void optionsPanelChanged() {
                    setupChart();
                    dialog.validate();
                    dialog.repaint();
                }
            });
        }


        dialog.pack();
        if (OSType.isMac()) {
            dialog.setMinimumSize(new Dimension(dialog.getBounds().width, 300));
        } else {
            Toolkit tk = Toolkit.getDefaultToolkit();
            Dimension d = tk.getScreenSize();
            if (d.height < 700 && contentPanel.getHeight() > 450) {
                dialog.setSize(new java.awt.Dimension(contentPanel.getWidth() + 100, 550));
            } else {
                // setSize because optionsPanel is shrunk in dialog
                dialog.setSize(new java.awt.Dimension(contentPanel.getWidth() + 100, contentPanel.getHeight() + 100));
            }

//            System.out.println("panel width = " + panel.getWidth());
//            System.out.println("panel height = " + panel.getHeight());
        }

        dialog.setResizable(true);
        dialog.setVisible(true);

        int result = JOptionPane.CANCEL_OPTION;
        Integer value = (Integer) optionPane.getValue();
        if (value != null && value != -1) {
            result = value;
        }

        if (result == JOptionPane.OK_OPTION) {
            getArguments();
        }

        return result;
    }

    private void setArguments(PriorType priorType) {
        PriorOptionsPanel panel = optionsPanels.get(priorType);
        panel.setArguments(parameter, priorType);
    }

    private void getArguments() {
//        if (parameter.isNodeHeight || parameter.isStatistic) {
//            parameter.priorType = (PriorType) priorCombo.getSelectedItem();
//            if (parameter.priorType == PriorType.NONE_TREE_PRIOR || parameter.priorType == PriorType.NONE_STATISTIC) {
//                parameter.initial = Double.NaN;
//                return;
//            }
//        } else {
//            parameter.priorType = (PriorType) priorCombo.getSelectedItem();
//        }
//
//        if (!parameter.isStatistic && initialField.getValue() != null) parameter.initial = initialField.getValue();
//
//        if (parameter.priorType != PriorType.ONE_OVER_X_PRIOR)
//            optionsPanels.get(parameter.priorType).setParameterPrior(parameter);

        parameter.priorType = (PriorType) priorCombo.getSelectedItem();
        PriorOptionsPanel panel = optionsPanels.get(parameter.priorType);
        if (panel != null) {
            panel.getArguments(parameter, parameter.priorType);
        }

    }

    private void setupComponents() {
        contentPanel.removeAll();

        OptionsPanel optionsPanel = new OptionsPanel(12, (OSType.isMac() ? 6 : 24));

        JPanel panel1 = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel1.add(optionsPanel);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.PAGE_START;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        contentPanel.add(panel1, gbc);

        optionsPanel.addSpanningComponent(new JLabel("Select prior distribution for " + parameter.getName()));

        PriorType priorType;
        priorType = (PriorType) priorCombo.getSelectedItem();

        if (priorType == null) {
            priorType = parameter.priorType;
            priorCombo.setSelectedItem(priorType);
        }

        if (!parameter.isPriorFixed) {
            optionsPanel.addComponentWithLabel("Prior Distribution: ", priorCombo);
        } else {
            optionsPanel.addComponentWithLabel("Prior Distribution: ", new JLabel(priorType.toString()));
        }

//        if (parameter.getOptions() instanceof PartitionClockModel) {
//            PartitionClockModel pcm = (PartitionClockModel) parameter.getOptions();
//            initialField.setEnabled(!pcm.getClockModelGroup().isFixMean());
//        }

        PriorOptionsPanel panel3 = optionsPanels.get(priorType);

        if (panel3 != null) {
            optionsPanel.addSpanningComponent(panel3);
        }

        if (priorType == PriorType.CMTC_RATE_REFERENCE_PRIOR) {
            optionsPanel.addSpanningComponent(citationText);
        }

        if (priorType.isPlottable()) {
            optionsPanel.addSeparator();

            setupChart();
            chart.setPreferredSize(new Dimension(300, 200));
            chart.setFontSize(8);

            gbc.gridy = 1;
            gbc.weighty = 1.0;
            gbc.fill = GridBagConstraints.BOTH;

            contentPanel.add(chart, gbc);

            gbc.gridy = 2;
            gbc.weighty = 0.0;
            gbc.anchor = GridBagConstraints.PAGE_END;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            contentPanel.add(quantilePanel, gbc);

        }
        setArguments(priorType);
        contentPanel.repaint();
    }

    NumberFormatter formatter = new NumberFormatter(4);

    void setupChart() {
        chart.removeAllPlots();

        PriorType priorType = (PriorType) priorCombo.getSelectedItem();

        if (priorType == null) {
            priorType = parameter.priorType;
            priorCombo.setSelectedItem(priorType);
        }

        // ExponentialDistribution(1.0 / mean)
//        if (priorType == PriorType.EXPONENTIAL_PRIOR && parameter.mean == 0) parameter.mean = 1;

        double offset = 0.0;
        Distribution distribution = optionsPanels.get(priorType).getDistribution(parameter);

        chart.addPlot(new PDFPlot(distribution, offset));
        if (distribution != null) {
            quantileText.setText(formatter.format(distribution.quantile(0.025)) +
                    "\n" + formatter.format(distribution.quantile(0.05)) +
                    "\n" + formatter.format(distribution.quantile(0.5)) +
                    "\n" + formatter.format(distribution.quantile(0.95)) +
                    "\n" + formatter.format(distribution.quantile(0.975)));
        }

    }



}