/**************************************************************************************
  	This file is part of GNU DataExplorer.

    GNU DataExplorer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    GNU DataExplorer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with GNU DataExplorer.  If not, see <https://www.gnu.org/licenses/>.
    
    Copyright (c) 2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023 Winfried Bruegmann
****************************************************************************************/
package gde.device.estner;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.HelpListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import gde.GDE;
import gde.config.Settings;
import gde.data.Channels;
import gde.device.DeviceDialog;
import gde.exception.ApplicationConfigurationException;
import gde.exception.SerialPortException;
import gde.log.Level;
import gde.log.LogFormatter;
import gde.messages.Messages;
import gde.ui.SWTResourceManager;

/**
 * e-Station dialog implementation (902, BC6, BC610, BC8)
 * @author Winfried Brügmann
 */
public class AkkumatikDialog_ extends DeviceDialog {
	final static Logger						log									= Logger.getLogger(AkkumatikDialog_.class.getName());
	static final String						DEVICE_NAME					= "Akkumatik";
	
	static Handler										logHandler;
	static Logger											rootLogger;

	Button												closeButton;
	Button												stopCollectDataButton;
	Button												startCollectDataButton;

	Composite											boundsComposite;
	
	Group													configGroup;
	Composite											composite1;
	Composite											composite2;
	Composite											composite3;

	CLabel												inputPowerLowCutOffLabel;
	CLabel												capacityCutOffLabel;
	CLabel												safetyTimerLabel;
	CLabel												tempCutOffLabel;
	CLabel												waitTimeLabel;
	CLabel												cellTypeLabel;

	CLabel												inputLowPowerCutOffText;
	CLabel												capacityCutOffText;
	CLabel												safetyTimerText;
	CLabel												tempCutOffText;
	CLabel												waitTimeText;
	CLabel												cellTypeText;

	CLabel												inputLowPowerCutOffUnit;
	CLabel												capacityCutOffUnit;
	CLabel												safetyTimerUnit;
	CLabel												tempCutOffUnit;
	CLabel												waitTimeUnit;
	CLabel												cellTypeUnit;

	boolean												isConnectionWarned 	= false;
	String												inputLowPowerCutOff	= "?";				//$NON-NLS-1$
	String												capacityCutOff			= "?";				//$NON-NLS-1$
	String												safetyTimer					= "?";				//$NON-NLS-1$
	String												tempCutOff					= "?";				//$NON-NLS-1$
	String												waitTime						= "?";				//$NON-NLS-1$
	String												cellType						= "?";				//$NON-NLS-1$

	HashMap<String, String>				configData					= new HashMap<String, String>();
	GathererThread								dataGatherThread;
	Thread												updateConfigTread;

	final Akkumatik								device;						// get device specific things, get serial port, ...
	final AkkumatikSerialPort			serialPort;				// open/close port execute getData()....
	final Channels								channels;					// interaction with channels, source of all records
	final Settings								settings;					// application configuration settings

	/**
	 * default constructor initialize all variables required
	 * @param parent Shell
	 * @param useDevice device specific class implementation
	 */
	public AkkumatikDialog_(Shell parent, Akkumatik useDevice) {
		super(parent);
		this.serialPort = useDevice.getCommunicationPort();
		this.device = useDevice;
		this.channels = Channels.getInstance();
		this.settings = Settings.getInstance();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		initLogger();
		//Akkumatik device = new Akkumatik("c:\\Users\\Winfried\\AppData\\Roaming\\DataExplorer\\Devices\\Akkumatik.xml");
		Akkumatik device;
		try {
			device = new Akkumatik("/Users/brueg/Library/Application Support/DataExplorer/Devices/Akkumatik.xml");
			AkkumatikSerialPort serialPort = new AkkumatikSerialPort(device, null);
			new AkkumatikDialog_(new Shell().getShell(), device).open();
		}
		catch (FileNotFoundException | JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (SerialPortException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * default method where the default controls are defined, this needs to be overwritten by specific device dialog
	 */
	@Override
	public void open() {
		try {
			this.shellAlpha = Settings.getInstance().getDialogAlphaValue(); 
			this.isAlphaEnabled = Settings.getInstance().isDeviceDialogAlphaEnabled();

			AkkumatikDialog_.log.log(Level.FINE, "dialogShell.isDisposed() " + ((this.dialogShell == null) ? "null" : this.dialogShell.isDisposed())); //$NON-NLS-1$ //$NON-NLS-2$
			if (this.dialogShell == null || this.dialogShell.isDisposed()) {
				if (this.settings.isDeviceDialogsModal())
					this.dialogShell = new Shell(this.application.getShell(), SWT.DIALOG_TRIM | SWT.PRIMARY_MODAL);
				else if (this.settings.isDeviceDialogsOnTop())
					this.dialogShell = new Shell(this.application.getDisplay(), SWT.DIALOG_TRIM | SWT.ON_TOP);
				else
					this.dialogShell = new Shell(this.application.getDisplay(), SWT.DIALOG_TRIM);

				SWTResourceManager.registerResourceUser(this.dialogShell);
				if (this.isAlphaEnabled) this.dialogShell.setAlpha(254);
				this.dialogShell.setLayout(new FormLayout());
				this.dialogShell.setText(this.device.getName() + Messages.getString(gde.messages.MessageIds.GDE_MSGT0273));
				this.dialogShell.setImage(SWTResourceManager.getImage("gde/resource/ToolBoxHot.gif")); //$NON-NLS-1$
				this.dialogShell.layout();
				this.dialogShell.pack();
				this.dialogShell.setSize(350, 465);
				this.dialogShell.addFocusListener(new FocusAdapter() {
					@Override
					public void focusGained(FocusEvent evt) {
						AkkumatikDialog_.log.log(Level.FINER, "dialogShell.focusGained, event=" + evt); //$NON-NLS-1$
						// if port is already connected, do not read the data update will be done by gathere thread
//						if (!AkkumatikDialog_.this.isConnectionWarned && !AkkumatikDialog_.this.serialPort.isConnected()) {
//							AkkumatikDialog_.this.updateConfigTread = new Thread("updateConfig") {
//								@Override
//								public void run() {
//									try {
//										AkkumatikDialog_.this.configData = new HashMap<String, String>();
//										AkkumatikDialog_.this.serialPort.open();
//										AkkumatikDialog_.this.serialPort.wait4Bytes(2000);
//										AkkumatikDialog_.this.device.getConfigurationValues(AkkumatikDialog_.this.configData, AkkumatikDialog_.this.serialPort.getData());
//										getDialogShell().getDisplay().asyncExec(new Runnable() {
//											public void run() {
//												updateGlobalConfigData(AkkumatikDialog_.this.configData);
//											}
//										});
//									}
//									catch (Exception e) {
//										AkkumatikDialog_.this.isConnectionWarned = true;
//										AkkumatikDialog_.this.application.openMessageDialog(AkkumatikDialog_.this.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0024, new Object[] { e.getMessage() } ));
//									}
//									AkkumatikDialog_.this.serialPort.close();
//								}
//							};
//							try {
//								AkkumatikDialog_.this.updateConfigTread.start();
//							}
//							catch (RuntimeException e) {
//								log.log(Level.WARNING, e.getMessage(), e);
//							}
//						}
					}
				});
				this.dialogShell.addListener(SWT.Traverse, new Listener() {
		      public void handleEvent(Event event) {
		        switch (event.detail) {
		        case SWT.TRAVERSE_ESCAPE:
		        	AkkumatikDialog_.this.dialogShell.close();
		          event.detail = SWT.TRAVERSE_NONE;
		          event.doit = false;
		          break;
		        }
		      }
		    });
				this.dialogShell.addHelpListener(new HelpListener() {
					public void helpRequested(HelpEvent evt) {
						AkkumatikDialog_.log.log(Level.FINER, "dialogShell.helpRequested, event=" + evt); //$NON-NLS-1$
						AkkumatikDialog_.this.application.openHelpDialog(DEVICE_NAME, "HelpInfo.html"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				});
				this.dialogShell.addDisposeListener(new DisposeListener() {
					public void widgetDisposed(DisposeEvent evt) {
						log.log(java.util.logging.Level.FINEST, "dialogShell.widgetDisposed, event=" + evt); //$NON-NLS-1$
						AkkumatikDialog_.this.dispose();
					}
				});
				{
					this.boundsComposite = new Composite(this.dialogShell, SWT.NONE);
					FormData boundsCompositeLData = new FormData();
					boundsCompositeLData.left = new FormAttachment(0, 1000, 0);
					boundsCompositeLData.right = new FormAttachment(1000, 1000, 0);
					boundsCompositeLData.top = new FormAttachment(0, 1000, 0);
					boundsCompositeLData.bottom = new FormAttachment(1000, 1000, 0);
					this.boundsComposite.setLayoutData(boundsCompositeLData);
					this.boundsComposite.setLayout(new RowLayout(SWT.HORIZONTAL));
					this.boundsComposite.addPaintListener(new PaintListener() {
						public void paintControl(PaintEvent evt) {
							AkkumatikDialog_.log.log(Level.FINER, "boundsComposite.paintControl() " + evt); //$NON-NLS-1$
							if (AkkumatikDialog_.this.dataGatherThread != null && AkkumatikDialog_.this.dataGatherThread.isAlive()) {
								AkkumatikDialog_.this.startCollectDataButton.setEnabled(false);
								AkkumatikDialog_.this.stopCollectDataButton.setEnabled(true);
							}
							else {
								AkkumatikDialog_.this.startCollectDataButton.setEnabled(true);
								AkkumatikDialog_.this.stopCollectDataButton.setEnabled(false);
							}
						}
					});
					{
						
					}
					{
						FormData startCollectDataButtonLData = new FormData();
						startCollectDataButtonLData.height = 30;
						startCollectDataButtonLData.bottom = new FormAttachment(1000, 1000, -40);
						startCollectDataButtonLData.left = new FormAttachment(0, 1000, 12);
						startCollectDataButtonLData.right = new FormAttachment(1000, 1000, -180);
						this.startCollectDataButton = new Button(this.boundsComposite, SWT.PUSH | SWT.CENTER);
						this.startCollectDataButton.setLayoutData(startCollectDataButtonLData);
						this.startCollectDataButton.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0274));
						this.startCollectDataButton.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evt) {
								AkkumatikDialog_.log.log(Level.FINEST, "startCollectDataButton.widgetSelected, event=" + evt); //$NON-NLS-1$
//								if (!AkkumatikDialog_.this.serialPort.isConnected()) {
//									try {
//										Channel activChannel = Channels.getInstance().getActiveChannel();
//										if (activChannel != null) {
//											AkkumatikDialog_.this.dataGatherThread = new GathererThread(AkkumatikDialog_.this.application, AkkumatikDialog_.this.device, AkkumatikDialog_.this.serialPort, activChannel.getNumber(), AkkumatikDialog_.this);
//											try {
//												AkkumatikDialog_.this.dataGatherThread.start();
//											}
//											catch (RuntimeException e) {
//												log.log(Level.WARNING, e.getMessage(), e);
//											}
//											AkkumatikDialog_.this.boundsComposite.redraw();
//										}
//									}
//									catch (Exception e) {
//										if (AkkumatikDialog_.this.dataGatherThread != null && AkkumatikDialog_.this.dataGatherThread.isCollectDataStopped) {
//											AkkumatikDialog_.this.dataGatherThread.stopDataGatheringThread(false, e);
//										}
//										AkkumatikDialog_.this.boundsComposite.redraw();
//										AkkumatikDialog_.this.application.updateGraphicsWindow();
//										AkkumatikDialog_.this.application.openMessageDialog(AkkumatikDialog_.this.getDialogShell(), Messages.getString(gde.messages.MessageIds.GDE_MSGE0023, new Object[] { e.getClass().getSimpleName(), e.getMessage() }));
//									}
//								}
							}
					});
						this.startCollectDataButton.addMouseTrackListener(this.mouseTrackerEnterFadeOut);
					}
					{
						FormData stopColletDataButtonLData = new FormData();
						stopColletDataButtonLData.height = 30;
						stopColletDataButtonLData.bottom = new FormAttachment(1000, 1000, -40);
						stopColletDataButtonLData.left = new FormAttachment(0, 1000, 170);
						stopColletDataButtonLData.right = new FormAttachment(1000, 1000, -12);
						this.stopCollectDataButton = new Button(this.boundsComposite, SWT.PUSH | SWT.CENTER);
						this.stopCollectDataButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.stopCollectDataButton.setLayoutData(stopColletDataButtonLData);
						this.stopCollectDataButton.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0275));
						this.stopCollectDataButton.setEnabled(false);
						this.stopCollectDataButton.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evt) {
								AkkumatikDialog_.log.log(Level.FINEST, "stopColletDataButton.widgetSelected, event=" + evt); //$NON-NLS-1$
								if (AkkumatikDialog_.this.dataGatherThread != null && AkkumatikDialog_.this.serialPort.isConnected()) {
									AkkumatikDialog_.this.dataGatherThread.stopDataGatheringThread(false, null);
								}
								AkkumatikDialog_.this.boundsComposite.redraw();
							}
						});
						this.stopCollectDataButton.addMouseTrackListener(this.mouseTrackerEnterFadeOut);
					}
					{
						FormData closeButtonLData = new FormData();
						closeButtonLData.height = 30;
						closeButtonLData.bottom = new FormAttachment(1000, 1000, -12);
						closeButtonLData.left = new FormAttachment(0, 1000, 12);
						closeButtonLData.right = new FormAttachment(1000, 1000, -12);
						this.closeButton = new Button(this.boundsComposite, SWT.PUSH | SWT.CENTER);
						this.closeButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.closeButton.setLayoutData(closeButtonLData);
						this.closeButton.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0188));
						this.closeButton.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evt) {
								AkkumatikDialog_.log.log(Level.FINEST, "okButton.widgetSelected, event=" + evt); //$NON-NLS-1$
								close();
							}
						});
						this.closeButton.addMouseTrackListener(this.mouseTrackerEnterFadeOut);
					}
					this.boundsComposite.addMouseTrackListener(new MouseTrackAdapter() {
						@Override
						public void mouseEnter(MouseEvent evt) {
							AkkumatikDialog_.log.log(Level.FINE, "boundsComposite.mouseEnter, event=" + evt); //$NON-NLS-1$
							fadeOutAplhaBlending(evt, AkkumatikDialog_.this.boundsComposite.getSize(), 10, 10, 10, 15);
						}

						@Override
						public void mouseHover(MouseEvent evt) {
							AkkumatikDialog_.log.log(Level.FINEST, "boundsComposite.mouseHover, event=" + evt); //$NON-NLS-1$
						}

						@Override
						public void mouseExit(MouseEvent evt) {
							AkkumatikDialog_.log.log(Level.FINE, "boundsComposite.mouseExit, event=" + evt); //$NON-NLS-1$
							fadeInAlpaBlending(evt, AkkumatikDialog_.this.boundsComposite.getSize(), 10, 10, -10, 15);
						}
					});
				} // end boundsComposite
				this.dialogShell.setLocation(getParent().toDisplay(getParent().getSize().x / 2 - 175, 100));
				this.dialogShell.open();
			}
			else {
				this.dialogShell.setVisible(true);
				this.dialogShell.setActive();
			}
			Display display = this.dialogShell.getDisplay();
			while (!this.dialogShell.isDisposed()) {
				if (!display.readAndDispatch()) display.sleep();
			}
		}
		catch (Exception e) {
			AkkumatikDialog_.log.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	public void resetButtons() {
		if (!this.isDisposed()) {
			this.startCollectDataButton.setEnabled(true);
			this.stopCollectDataButton.setEnabled(false);
		}
	}

	/**
	 * update the global conguration data in dialog
	 */
	public void updateGlobalConfigData(HashMap<String, String> newConfigData) {
		this.configData = newConfigData;
		if (this.dialogShell != null && !this.dialogShell.isDisposed()) {
//			if (Thread.currentThread().getId() == this.application.getThreadId()) {
//				this.inputLowPowerCutOffText.setText(this.inputLowPowerCutOff = this.configData.get(eStation.CONFIG_IN_VOLTAGE_CUT_OFF)); //$NON-NLS-1$
//				this.capacityCutOffText.setText(this.capacityCutOff = this.configData.get(eStation.CONFIG_SET_CAPASITY)); //$NON-NLS-1$
//				this.safetyTimerText.setText(this.safetyTimer = this.configData.get(eStation.CONFIG_SAFETY_TIME)); //$NON-NLS-1$
//				this.tempCutOffText.setText(this.tempCutOff = this.configData.get(eStation.CONFIG_EXT_TEMP_CUT_OFF)); //$NON-NLS-1$
//				this.waitTimeText.setText(this.waitTime = this.configData.get(eStation.CONFIG_WAIT_TIME)); //$NON-NLS-1$
//				if (this.configData.get(eStation.CONFIG_BATTERY_TYPE) != null)
//					this.cellTypeText.setText(this.cellType = this.configData.get(eStation.CONFIG_BATTERY_TYPE));
//				this.configGroup.redraw();
//			}
//			else {
//				GDE.display.asyncExec(new Runnable() {
//					public void run() {
//						AkkumatikDialog_.this.inputLowPowerCutOffText.setText(AkkumatikDialog_.this.inputLowPowerCutOff = AkkumatikDialog_.this.configData.get(eStation.CONFIG_IN_VOLTAGE_CUT_OFF));
//						AkkumatikDialog_.this.capacityCutOffText.setText(AkkumatikDialog_.this.capacityCutOff = AkkumatikDialog_.this.configData.get(eStation.CONFIG_SET_CAPASITY));
//						AkkumatikDialog_.this.safetyTimerText.setText(AkkumatikDialog_.this.safetyTimer = AkkumatikDialog_.this.configData.get(eStation.CONFIG_SAFETY_TIME));
//						AkkumatikDialog_.this.tempCutOffText.setText(AkkumatikDialog_.this.tempCutOff = AkkumatikDialog_.this.configData.get(eStation.CONFIG_EXT_TEMP_CUT_OFF));
//						AkkumatikDialog_.this.waitTimeText.setText(AkkumatikDialog_.this.waitTime = AkkumatikDialog_.this.configData.get(eStation.CONFIG_WAIT_TIME));
//						if (AkkumatikDialog_.this.configData.get(eStation.CONFIG_BATTERY_TYPE) != null)
//							AkkumatikDialog_.this.cellTypeText.setText(AkkumatikDialog_.this.cellType = AkkumatikDialog_.this.configData.get(eStation.CONFIG_BATTERY_TYPE));
//						AkkumatikDialog_.this.configGroup.redraw();
//					}
//				});
//			}
		}
	}
	

	private static void initLogger() {
		AkkumatikDialog_.logHandler = new ConsoleHandler();
		AkkumatikDialog_.logHandler.setFormatter(new LogFormatter());
		AkkumatikDialog_.logHandler.setLevel(Level.INFO);
		AkkumatikDialog_.rootLogger = Logger.getLogger(GDE.STRING_EMPTY);
		// clean up all handlers from outside
		Handler[] handlers = AkkumatikDialog_.rootLogger.getHandlers();
		for (Handler handler : handlers) {
			AkkumatikDialog_.rootLogger.removeHandler(handler);
		}
		AkkumatikDialog_.rootLogger.setLevel(Level.ALL);
		AkkumatikDialog_.rootLogger.addHandler(AkkumatikDialog_.logHandler);
	}

}
