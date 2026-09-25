/**************************************************************************************
  	This file is part of GNU DataExplorer.

    GNU DataExplorer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    DataExplorer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GNU DataExplorer.  If not, see <https://www.gnu.org/licenses/>.

    Copyright (c) 2026 Jyri Wennström, Oikumene Works
    refer to https://github.com/oikumene-works/tasi/tree/main/ta612c/software/dataexplorer-plugin
    Copyright (c) 2026 Winfried Bruegmann
****************************************************************************************/
package gde.device.tasi;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import gde.messages.Messages;

/**
 * Recording download dialog to prompt for the historical recording interval, which REC bytes do not establish. 
 * Intentionally starts blank so a guessed default cannot silently become a time axis. 
 * Explains timing and completeness limits before download.
 */
final class TA612CRecDialog {
    /**
     * Opens on the SWT thread and returns validated milliseconds, or null on
     * Cancel/window close. Invalid text leaves the dialog open. Its nested event
     * loop can change application state; callers must recheck the active device
     * and acquisition ownership after return before opening the transport.
     */
    static Long open(Shell parent) {
        Shell dialog = new Shell(parent, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(Messages.getString(MessageIds.GDE_MSGT4108));
        dialog.setLayout(new GridLayout(2, false));
        Label explanation = new Label(dialog, SWT.WRAP);
        explanation.setText(Messages.getString(MessageIds.GDE_MSGT4109));
        GridData wide = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        wide.widthHint = 540;
        explanation.setLayoutData(wide);
        new Label(dialog, SWT.NONE).setText(Messages.getString(MessageIds.GDE_MSGT4110));
        Text interval = new Text(dialog, SWT.BORDER);
        interval.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Label error = new Label(dialog, SWT.WRAP);
        GridData errorData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        errorData.widthHint = 540; errorData.heightHint = 44;
        error.setLayoutData(errorData);
        Button download = new Button(dialog, SWT.PUSH);
        download.setText(Messages.getString(MessageIds.GDE_MSGT4111));
        Button cancel = new Button(dialog, SWT.PUSH);
        cancel.setText(Messages.getString(MessageIds.GDE_MSGT4112));
        Long[] result = {null};
        download.addListener(SWT.Selection, e -> {
            try { result[0] = TA612CRecImport.parseIntervalMs(interval.getText()); dialog.dispose(); }
            catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); dialog.layout(); }
        });
        cancel.addListener(SWT.Selection, e -> dialog.dispose());
        dialog.setDefaultButton(download);
        dialog.pack(); 
        dialog.open(); 
        interval.setFocus();
        while (!dialog.isDisposed()) 
        	if (!dialog.getDisplay().readAndDispatch()) 
        		dialog.getDisplay().sleep();
        return result[0];
    }
}
