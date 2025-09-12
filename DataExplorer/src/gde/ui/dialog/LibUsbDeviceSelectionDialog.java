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

    Copyright (c) 2020,2021,2022,2023,2024,2025 Winfried Bruegmann
****************************************************************************************/
package gde.ui.dialog;

import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.usb4java.Device;

import gde.messages.MessageIds;
import gde.messages.Messages;
import gde.ui.DataExplorer;

public class LibUsbDeviceSelectionDialog extends Dialog {

	Map<String, Device> 	usbDevices;
	protected Device		result;
	protected Shell		shell;

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public LibUsbDeviceSelectionDialog(Shell parent, int style) {
		super(parent, style);
		setText("Select USB port of device to be used");
	}

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public LibUsbDeviceSelectionDialog(Map<String, Device> fondUsbDevices) {
		super(DataExplorer.getInstance().getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		setText(Messages.getString(MessageIds.GDE_MSGW0049));
		this.usbDevices = fondUsbDevices;
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	public Object open() {
		createContents();
		shell.open();
		shell.layout();
		shell.setLocation(getParent().toDisplay(250, 150));
		Display display = getParent().getDisplay();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		return result;
	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		shell = new Shell(getParent(), SWT.DIALOG_TRIM);
		shell.setSize(350, 130);
		shell.setText(getText());
		RowLayout rowLayout = new RowLayout(SWT.VERTICAL);
		rowLayout.center = true;
		rowLayout.marginLeft = 10;
    rowLayout.marginTop = 10;
    rowLayout.marginRight = 10;
    rowLayout.marginBottom = 10;
		shell.setLayout(rowLayout);
		
		Combo combo = new Combo(shell, SWT.NONE);
		combo.setItems(usbDevices.keySet().toArray(new String[usbDevices.size()]));
		combo.select(0);
		combo.setLayoutData(new RowData(340, 30));
		
		new Composite(shell, SWT.NONE).setLayoutData(new RowData(340, 10));
		
		Button closeButton = new Button(shell, SWT.BORDER);
		closeButton.setText("OK");
		closeButton.setLayoutData(new RowData(80, 35));
		closeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (combo.getSelectionIndex() == -1) 
					result = usbDevices.get(combo.getItems()[0]);
				else
					result = usbDevices.get(combo.getItems()[combo.getSelectionIndex()]);
				shell.dispose();
			}
		});
		shell.pack();
	}
}
