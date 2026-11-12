/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 *
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package iped.app.ui;

import java.awt.Dimension;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import iped.utils.DisplayTimeZone;

/** A compact selector next to Help and the checkbox in IPED's top bar. */
public final class DisplayTimeZoneSelector extends JComboBox<String> {
    private static final long serialVersionUID = 1L;
    private final String systemOption;

    public DisplayTimeZoneSelector() {
        systemOption = "Sistema (" + ZoneId.systemDefault().getId() + ")";
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(zones);
        zones.remove("UTC");

        addItem(systemOption);
        addItem("UTC");
        for (String zone : zones) {
            addItem(zone);
        }
        setMaximumRowCount(18);
        setToolTipText("Zona horaria para mostrar las fechas; no modifica los datos de la evidencia");
        setMaximumSize(new Dimension(260, 30));
        setPreferredSize(new Dimension(220, 26));
        refreshSelection();

        addActionListener(e -> {
            String selected = (String) getSelectedItem();
            if (selected == null) {
                return;
            }
            try {
                if (systemOption.equals(selected)) {
                    DisplayTimeZone.useSystemDefault();
                } else {
                    DisplayTimeZone.setZoneId(selected);
                }
            } catch (RuntimeException ex) {
                refreshSelection();
                JOptionPane.showMessageDialog(this, ex.getMessage(),
                        "Error guardando zona horaria", JOptionPane.ERROR_MESSAGE);
            }
        });

        // Synchronize toolbar if the user changes timezone in Options or Timeline.
        DisplayTimeZone.addListener(zone -> SwingUtilities.invokeLater(this::refreshSelection));
    }

    private void refreshSelection() {
        String selected = DisplayTimeZone.isSystemDefault()
                ? systemOption : DisplayTimeZone.getZoneId().getId();
        if (!selected.equals(getSelectedItem())) {
            setSelectedItem(selected);
        }
    }
}
