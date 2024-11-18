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

    Copyright (c) 2024 Winfried Bruegmann
****************************************************************************************/
package gde.ui.dialog;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import gde.GDE;
import gde.messages.MessageIds;
import gde.messages.Messages;
import gde.ui.SWTResourceManager;

public class UpdateMessageBox extends Dialog {

	protected Boolean	result = false;
	protected Shell		shell;

	/**
	* Auto-generated main method to display this
	* org.eclipse.swt.widgets.Dialog inside a new Shell.
	*/
	public static void main(String[] args) {
		try {
			Display display = Display.getDefault();
			Shell shell = new Shell(display);
			UpdateMessageBox inst = new UpdateMessageBox(shell, SWT.NULL);
			inst.open();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create the dialog.
	 * @param parent
	 * @param style
	 */
	public UpdateMessageBox(Shell parent, int style) {
		super(parent, style);
		setText(Messages.getString(MessageIds.GDE_MSGI0052));
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	public boolean open() {
		createContents();
		shell.open();
		//shell.layout();
		shell.pack();
		shell.setLocation(500, 300);
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
		shell = new Shell(getParent(), SWT.BORDER | SWT.TITLE);
		//shell.setSize(400, 250);
		shell.setText(getText());
		RowLayout rowLayout = new RowLayout(SWT.VERTICAL);
		rowLayout.center = true;
		rowLayout.marginLeft = 20;
    rowLayout.marginTop = 20;
    rowLayout.marginRight = 20;
    rowLayout.marginBottom = 20;
		shell.setLayout(rowLayout);
		
		Text txttNewUpdateAvail = new Text(shell, SWT.CENTER);
		txttNewUpdateAvail.setLayoutData(new RowData(370, 30));
		txttNewUpdateAvail.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		txttNewUpdateAvail.setEditable(false);
		txttNewUpdateAvail.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		txttNewUpdateAvail.setText(Messages.getString(MessageIds.GDE_MSGI0072));
		
		Text txtDownloadProblemHint = new Text(shell, SWT.WRAP | SWT.CENTER | SWT.MULTI);
		txtDownloadProblemHint.setLayoutData(new RowData(370, 60));
		txtDownloadProblemHint.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		txtDownloadProblemHint.setEditable(false);
		txtDownloadProblemHint.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		txtDownloadProblemHint.setText(Messages.getString(MessageIds.GDE_MSGI0073));
		
		Text txtBetaVersionHint = new Text(shell, SWT.WRAP | SWT.CENTER | SWT.MULTI);
		txtBetaVersionHint.setLayoutData(new RowData(370, 70));
		txtBetaVersionHint.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		txtBetaVersionHint.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		txtBetaVersionHint.setText(Messages.getString(MessageIds.GDE_MSGI0074));
		txtBetaVersionHint.setEditable(false);
		
		Composite composite = new Composite(shell, SWT.NONE);
		composite.setLayoutData(new RowData(380, 50));
		FillLayout filllayout = new FillLayout(SWT.HORIZONTAL);
		filllayout.marginWidth = 25;
		filllayout.spacing = 40;
		composite.setLayout(filllayout);

		Link linkNews = new Link(composite, SWT.NONE);
		linkNews.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		linkNews.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		linkNews.setText("<a>" + Messages.getString(MessageIds.GDE_MSGI0075) + "</a>");
		linkNews.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Program.launch("https://savannah.nongnu.org/news/?group=dataexplorer");
			}
		});
		
		Link linkDonate = new Link(composite, SWT.NONE);
		linkDonate.setBackground(Display.getCurrent().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
		linkDonate.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		linkDonate.setText("<a>" + Messages.getString(MessageIds.GDE_MSGI0076) + "</a>");
		linkDonate.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				Program.launch("https://www.paypal.com/paypalme/WinfriedBruegmann");
			}
		});
		composite.layout();
		
		Composite composite2 = new Composite(shell, SWT.NONE);
		composite2.setLayoutData(new RowData(370, 40));
		FillLayout fLayout = new FillLayout(SWT.HORIZONTAL);
		fLayout.marginWidth = 20;
		fLayout.spacing = 50;
		composite2.setLayout(fLayout);
				
		Button btnNo = new Button(composite2, SWT.CENTER|SWT.PUSH);
		btnNo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		btnNo.setText(Messages.getString(MessageIds.GDE_MSGI0078));
		btnNo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				shell.dispose();
			}
		});
		
		Button btnYes = new Button(composite2, SWT.CENTER|SWT.PUSH);
		btnYes.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
		btnYes.setText(Messages.getString(MessageIds.GDE_MSGI0077));
		btnYes.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				result = true;
				shell.dispose();
			}
		});
		composite2.layout();
	}
}
