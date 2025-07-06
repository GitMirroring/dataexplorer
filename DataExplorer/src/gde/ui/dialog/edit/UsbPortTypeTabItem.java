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
    
    Copyright (c) 2024,2025 Winfried Bruegmann
****************************************************************************************/
package gde.ui.dialog.edit;

import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.HelpListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;

import gde.GDE;
import gde.device.DataTypes;
import gde.device.DeviceConfiguration;
import gde.log.Level;
import gde.messages.MessageIds;
import gde.messages.Messages;
import gde.ui.DataExplorer;
import gde.ui.SWTResourceManager;
import gde.utils.StringHelper;

/**
 * class defining a CTabItem with TCP PortType configuration data
 * @author Winfried Brügmann
 */
public class UsbPortTypeTabItem extends CTabItem {
	final static Logger						log								= Logger.getLogger(ChannelTypeTabItem.class.getName());

	Composite											usbPortComposite, timeOutComposite;
	Label													usbPortDescriptionLabel, timeOutDescriptionLabel;
	Label													vendorIDLabel, productIDLabel, productStringLabel, interfaceLabel, endPointInLable, endPointOutLable, timeOutLabel;
	Text													vendorIDText, productIDText, productStringText, interfaceText, endPointInText, endPointOutText;
	Button												productStringButton, timeOutButton;
	Label													ReadTimeoutLabel, ReadStableIndexLabel, _WTOCharDelayTimeLabel, _WTOExtraDelayTimeLabel;
	Text													ReadTimeoutText, ReadStableIndexText, _WTOCharDelayTimeText, _WTOExtraDelayTimeText;

	String												vendorID						= GDE.STRING_EMPTY;
	String												productID						= GDE.STRING_EMPTY;
	String												productString				= GDE.STRING_EMPTY;
	boolean												isUseProductString	= false;
	String												usbInterface				= GDE.STRING_EMPTY;
	String												usbEndPointIn				= GDE.STRING_EMPTY;
	String												usbEndPointOut			= GDE.STRING_EMPTY;
	boolean												isUseTimeOut				= false;
	int														ReadTimeout					= 0;
	int														ReadStableIndex			= 0;
	int														WTOCharDelayTime		= 0;
	int														WTOExtraDelayTime		= 0;
	DeviceConfiguration						deviceConfig;
	Menu													popupMenu;
	ContextMenu										contextMenu;

	final CTabFolder							tabFolder;
	final DevicePropertiesEditor	propsEditor;

	public UsbPortTypeTabItem(CTabFolder parent, int style, int index) {
		super(parent, style, index);
		this.tabFolder = parent;
		this.propsEditor = DevicePropertiesEditor.getInstance();
		log.log(java.util.logging.Level.FINE, "UsbPortTypeTabItem "); //$NON-NLS-1$
		initGUI();
	}

	private void initGUI() {
		try {
			SWTResourceManager.registerResourceUser(this);
			this.setText(Messages.getString(MessageIds.GDE_MSGT0979));
			this.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
			{
				this.usbPortComposite = new Composite(this.tabFolder, SWT.NONE);
				this.usbPortComposite.setLayout(null);
				this.setControl(this.usbPortComposite);
				this.usbPortComposite.addHelpListener(new HelpListener() {			
					public void helpRequested(HelpEvent evt) {
						log.log(Level.FINEST, "usbPortComposite.helpRequested " + evt); //$NON-NLS-1$
						DataExplorer.getInstance().openHelpDialog("", "HelpInfo_A1.html#device_properties_serial_port"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				});
				this.usbPortComposite.addFocusListener(new FocusAdapter() {
					@Override
					public void focusLost(FocusEvent focusevent) {
						log.log(java.util.logging.Level.FINEST, "usbPortComposite.focusLost, event=" + focusevent); //$NON-NLS-1$
						UsbPortTypeTabItem.this.enableContextmenu(false);
					}

					@Override
					public void focusGained(FocusEvent focusevent) {
						log.log(java.util.logging.Level.FINEST, "usbPortComposite.focusGained, event=" + focusevent); //$NON-NLS-1$
						UsbPortTypeTabItem.this.enableContextmenu(true);
					}
				});
				{
					this.usbPortDescriptionLabel = new Label(this.usbPortComposite, SWT.CENTER | SWT.WRAP);
					this.usbPortDescriptionLabel.setText(Messages.getString(MessageIds.GDE_MSGT0980));
					this.usbPortDescriptionLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.usbPortDescriptionLabel.setBounds(12, 6, 602, 56);
				}
				{
					this.vendorIDLabel = new Label(this.usbPortComposite, SWT.LEFT);
					this.vendorIDLabel.setText("Vendor ID");
					this.vendorIDLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.vendorIDLabel.setBounds(30, 74, 100, 20);
				}
				{
					this.vendorIDText = new Text(this.usbPortComposite, SWT.BORDER);
					this.vendorIDText.setBounds(150, 76, 60, 20);
					this.vendorIDText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.vendorIDText.setEditable(true);
					this.vendorIDText.addVerifyListener(new VerifyListener() {
						public void verifyText(VerifyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "vendorIDText.verifyText, event=" + evt); //$NON-NLS-1$
							evt.doit = StringHelper.verifyTypedInput(DataTypes.STRING, evt.text);
						}
					});
					this.vendorIDText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "vendorIDText.keyReleased, event=" + evt); //$NON-NLS-1$
							UsbPortTypeTabItem.this.productID = UsbPortTypeTabItem.this.vendorIDText.getText();
							if (UsbPortTypeTabItem.this.deviceConfig != null) {
								UsbPortTypeTabItem.this.deviceConfig.setUsbVendorId(UsbPortTypeTabItem.this.vendorIDText.getText().trim());
								UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					this.productIDLabel = new Label(this.usbPortComposite, SWT.LEFT);
					this.productIDLabel.setText("Product ID");
					this.productIDLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.productIDLabel.setBounds(30, 104, 100, 20);
				}
				{
					this.productIDText = new Text(this.usbPortComposite, SWT.BORDER);
					this.productIDText.setBounds(150, 106, 60, 20);
					this.productIDText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.productIDText.setEditable(true);
					this.productIDText.addVerifyListener(new VerifyListener() {
						public void verifyText(VerifyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "productIDText.verifyText, event=" + evt); //$NON-NLS-1$
							evt.doit = StringHelper.verifyTypedInput(DataTypes.STRING, evt.text);
						}
					});
					this.productIDText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "productIDText.keyReleased, event=" + evt); //$NON-NLS-1$
							UsbPortTypeTabItem.this.productID = UsbPortTypeTabItem.this.productIDText.getText();
							if (UsbPortTypeTabItem.this.deviceConfig != null) {
								UsbPortTypeTabItem.this.deviceConfig.setTcpPortNumber(UsbPortTypeTabItem.this.productIDText.getText().trim());
								UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					Group productStringGroup = new Group(this.usbPortComposite, SWT.BORDER);
					productStringGroup.setText("Product String (optional)");
					productStringGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					productStringGroup.setBounds(25, 134, 320, 70);

					{
						this.productStringButton = new Button(productStringGroup, SWT.CHECK | SWT.BORDER);
						this.productStringButton.setBounds(8, 10, 15, 15);
						this.productStringButton.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evt) {
								log.log(java.util.logging.Level.FINEST, "productStringButton.widgetSelected, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.isUseProductString = productStringButton.getSelection();
								UsbPortTypeTabItem.this.productStringLabel.setEnabled(isUseProductString);
								UsbPortTypeTabItem.this.productStringText.setEnabled(isUseProductString);
								UsbPortTypeTabItem.this.productStringText.setEditable(isUseProductString);

								if (UsbPortTypeTabItem.this.isUseProductString) {								
									if (UsbPortTypeTabItem.this.deviceConfig != null) {
										UsbPortTypeTabItem.this.productString = UsbPortTypeTabItem.this.deviceConfig.getName();
										UsbPortTypeTabItem.this.productStringText.setText(UsbPortTypeTabItem.this.productString);
										String productString = UsbPortTypeTabItem.this.productStringText.getText().trim();
										UsbPortTypeTabItem.this.deviceConfig.setUsbProductString(productString);									
										UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
								}
								else {
									if (UsbPortTypeTabItem.this.deviceConfig != null) {
										UsbPortTypeTabItem.this.deviceConfig.removeUsbProductString();
										UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
								}
								UsbPortTypeTabItem.this.enableTimeout();
							}
						});
					}
					{
						this.productStringLabel = new Label(productStringGroup, SWT.LEFT);
						this.productStringLabel.setText("Product String");
						this.productStringLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.productStringLabel.setBounds(30, 10, 90, 20);
					}
					{
						this.productStringText = new Text(productStringGroup, SWT.BORDER);
						this.productStringText.setBounds(120, 10, 180, 20);
						this.productStringText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.productStringText.setEditable(true);
						this.productStringText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "productStringText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.STRING, evt.text);
							}
						});
						this.productStringText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "productStringText.keyReleased, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.productString = UsbPortTypeTabItem.this.productStringText.getText();
								if (UsbPortTypeTabItem.this.deviceConfig != null) {
									String productString = UsbPortTypeTabItem.this.productStringText.getText().trim();
									UsbPortTypeTabItem.this.deviceConfig.setUsbProductString(productString);
									UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
				}
				{
					this.interfaceLabel = new Label(this.usbPortComposite, SWT.LEFT);
					this.interfaceLabel.setText("Interface");
					this.interfaceLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.interfaceLabel.setBounds(30, 210, 150, 20);
				}
				{
					this.interfaceText = new Text(this.usbPortComposite, SWT.BORDER);
					this.interfaceText.setBounds(150, 210, 60, 20);
					this.interfaceText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.interfaceText.setEditable(true);
					this.interfaceText.addVerifyListener(new VerifyListener() {
						public void verifyText(VerifyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "interfaceText.verifyText, event=" + evt); //$NON-NLS-1$
							evt.doit = StringHelper.verifyTypedInput(DataTypes.STRING, evt.text);
						}
					});
					this.interfaceText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "interfaceIdText.keyReleased, event=" + evt); //$NON-NLS-1$
							UsbPortTypeTabItem.this.usbInterface = UsbPortTypeTabItem.this.interfaceText.getText();
							if (UsbPortTypeTabItem.this.deviceConfig != null) {
								UsbPortTypeTabItem.this.deviceConfig.setUsbInterface(UsbPortTypeTabItem.this.interfaceText.getText().trim());
								UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					this.endPointInLable = new Label(this.usbPortComposite, SWT.LEFT);
					this.endPointInLable.setText("Endpoint In");
					this.endPointInLable.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.endPointInLable.setBounds(30, 250, 150, 20);
				}
				{
					this.endPointInText = new Text(this.usbPortComposite, SWT.BORDER);
					this.endPointInText.setBounds(150, 250, 60, 20);
					this.endPointInText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.endPointInText.setEditable(true);
					this.endPointInText.addVerifyListener(new VerifyListener() {
						public void verifyText(VerifyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "endPointInText.verifyText, event=" + evt); //$NON-NLS-1$
							evt.doit = StringHelper.verifyTypedInput(DataTypes.STRING, evt.text);
						}
					});
					this.endPointInText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "endPointInText.keyReleased, event=" + evt); //$NON-NLS-1$
							UsbPortTypeTabItem.this.usbEndPointIn = UsbPortTypeTabItem.this.endPointInText.getText().trim();
							if (UsbPortTypeTabItem.this.deviceConfig != null) {
								UsbPortTypeTabItem.this.deviceConfig.setUsbEndpointIn(UsbPortTypeTabItem.this.usbEndPointIn);
								UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					this.endPointOutLable = new Label(this.usbPortComposite, SWT.LEFT);
					this.endPointOutLable.setText("Endpoint Out");
					this.endPointOutLable.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.endPointOutLable.setBounds(30, 290, 150, 20);
				}
				{
					this.endPointOutText = new Text(this.usbPortComposite, SWT.BORDER);
					this.endPointOutText.setBounds(150, 290, 60, 20);
					this.endPointOutText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.endPointOutText.setEditable(true);
					this.endPointOutText.addVerifyListener(new VerifyListener() {
						public void verifyText(VerifyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "endPointOutText.verifyText, event=" + evt); //$NON-NLS-1$
							evt.doit = StringHelper.verifyTypedInput(DataTypes.STRING, evt.text);
						}
					});
					this.endPointOutText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "endPointOutText.keyReleased, event=" + evt); //$NON-NLS-1$
							UsbPortTypeTabItem.this.usbEndPointOut = UsbPortTypeTabItem.this.endPointOutText.getText().trim();
							if (UsbPortTypeTabItem.this.deviceConfig != null) {
								UsbPortTypeTabItem.this.deviceConfig.setUsbEndpointOut(UsbPortTypeTabItem.this.usbEndPointOut);
								UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					this.timeOutComposite = new Composite(this.usbPortComposite, SWT.BORDER);
					this.timeOutComposite.setLayout(null);
					this.timeOutComposite.setBounds(355, 80, 250, 220);
					{
						this.timeOutDescriptionLabel = new Label(this.timeOutComposite, SWT.WRAP);
						this.timeOutDescriptionLabel.setText(Messages.getString(MessageIds.GDE_MSGT0591));
						this.timeOutDescriptionLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.timeOutDescriptionLabel.setBounds(6, 3, 232, 69);
					}
					{
						this.timeOutLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this.timeOutLabel.setText(Messages.getString(MessageIds.GDE_MSGT0586));
						this.timeOutLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.timeOutLabel.setBounds(6, 70, 140, 20);
					}
					{
						this.timeOutButton = new Button(this.timeOutComposite, SWT.CHECK);
						this.timeOutButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.timeOutButton.setBounds(161, 70, 70, 20);
						this.timeOutButton.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evt) {
								log.log(java.util.logging.Level.FINEST, "timeOutButton.widgetSelected, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.isUseTimeOut = UsbPortTypeTabItem.this.timeOutButton.getSelection();
								if (UsbPortTypeTabItem.this.isUseTimeOut) {
									if (UsbPortTypeTabItem.this.deviceConfig != null) {
										UsbPortTypeTabItem.this.deviceConfig.setReadTimeOut(UsbPortTypeTabItem.this.ReadTimeout = UsbPortTypeTabItem.this.deviceConfig.getReadTimeOut());
										UsbPortTypeTabItem.this.deviceConfig.setReadStableIndex(UsbPortTypeTabItem.this.ReadStableIndex = UsbPortTypeTabItem.this.deviceConfig.getReadStableIndex());
										UsbPortTypeTabItem.this.deviceConfig.setWriteCharDelayTime(UsbPortTypeTabItem.this.WTOCharDelayTime = UsbPortTypeTabItem.this.deviceConfig.getWriteCharDelayTime());
										UsbPortTypeTabItem.this.deviceConfig.setWriteDelayTime(UsbPortTypeTabItem.this.WTOExtraDelayTime = UsbPortTypeTabItem.this.deviceConfig.getWriteDelayTime());
										UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
									else {
										UsbPortTypeTabItem.this.ReadTimeout = 0;
										UsbPortTypeTabItem.this.ReadStableIndex = 0;
										UsbPortTypeTabItem.this.WTOCharDelayTime = 0;
										UsbPortTypeTabItem.this.WTOExtraDelayTime = 0;
									}
								}
								else {
									if (UsbPortTypeTabItem.this.deviceConfig != null) {
										UsbPortTypeTabItem.this.deviceConfig.removeSerialPortTimeOut();
									}
									UsbPortTypeTabItem.this.ReadTimeout = 0;
									UsbPortTypeTabItem.this.ReadStableIndex = 0;
									UsbPortTypeTabItem.this.WTOCharDelayTime = 0;
									UsbPortTypeTabItem.this.WTOExtraDelayTime = 0;
								}
								UsbPortTypeTabItem.this.enableTimeout();
							}
						});
					}
					{
						this.ReadTimeoutLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this.ReadTimeoutLabel.setText(Messages.getString(MessageIds.GDE_MSGT0587));
						this.ReadTimeoutLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.ReadTimeoutLabel.setBounds(6, 100, 140, 20);
					}
					{
						this.ReadTimeoutText = new Text(this.timeOutComposite, SWT.BORDER);
						this.ReadTimeoutText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.ReadTimeoutText.setBounds(162, 100, 70, 20);
						this.ReadTimeoutText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "ReadTimeoutText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
							}
						});
						this.ReadTimeoutText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "ReadTimeoutText.keyReleased, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.ReadTimeout = UsbPortTypeTabItem.this.ReadTimeoutText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(UsbPortTypeTabItem.this.ReadTimeoutText.getText());
								if (UsbPortTypeTabItem.this.deviceConfig != null) {
									UsbPortTypeTabItem.this.deviceConfig.setReadTimeOut(UsbPortTypeTabItem.this.ReadTimeout);
									UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
					{
						this.ReadStableIndexLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this.ReadStableIndexLabel.setText(Messages.getString(MessageIds.GDE_MSGT0588));
						this.ReadStableIndexLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.ReadStableIndexLabel.setBounds(6, 130, 140, 20);
					}
					{
						this.ReadStableIndexText = new Text(this.timeOutComposite, SWT.BORDER);
						this.ReadStableIndexText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.ReadStableIndexText.setBounds(162, 130, 70, 20);
						this.ReadStableIndexText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "ReadStableIndexText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
							}
						});
						this.ReadStableIndexText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "ReadStableIndexText.keyReleased, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.ReadStableIndex = UsbPortTypeTabItem.this.ReadStableIndexText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(UsbPortTypeTabItem.this.ReadStableIndexText.getText());
								if (UsbPortTypeTabItem.this.deviceConfig != null) {
									UsbPortTypeTabItem.this.deviceConfig.setReadStableIndex(UsbPortTypeTabItem.this.ReadStableIndex);
									UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
					{
						this._WTOCharDelayTimeLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this._WTOCharDelayTimeLabel.setText(Messages.getString(MessageIds.GDE_MSGT0589));
						this._WTOCharDelayTimeLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._WTOCharDelayTimeLabel.setBounds(6, 160, 140, 20);
					}
					{
						this._WTOCharDelayTimeText = new Text(this.timeOutComposite, SWT.BORDER);
						this._WTOCharDelayTimeText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._WTOCharDelayTimeText.setBounds(162, 160, 70, 20);
						this._WTOCharDelayTimeText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_WRTOCharDelayTimeText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
							}
						});
						this._WTOCharDelayTimeText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_WRTOCharDelayTimeText.keyReleased, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.WTOCharDelayTime = UsbPortTypeTabItem.this._WTOCharDelayTimeText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(UsbPortTypeTabItem.this._WTOCharDelayTimeText.getText());
								if (UsbPortTypeTabItem.this.deviceConfig != null) {
									UsbPortTypeTabItem.this.deviceConfig.setWriteCharDelayTime(UsbPortTypeTabItem.this.WTOCharDelayTime);
									UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
					{
						this._WTOExtraDelayTimeLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this._WTOExtraDelayTimeLabel.setText(Messages.getString(MessageIds.GDE_MSGT0590));
						this._WTOExtraDelayTimeLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._WTOExtraDelayTimeLabel.setBounds(6, 190, 140, 20);
					}
					{
						this._WTOExtraDelayTimeText = new Text(this.timeOutComposite, SWT.BORDER);
						this._WTOExtraDelayTimeText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._WTOExtraDelayTimeText.setBounds(162, 190, 70, 20);
						this._WTOExtraDelayTimeText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_WTOExtraDelayTimeText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
							}
						});
						this._WTOExtraDelayTimeText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_WTOExtraDelayTimeText.keyReleased, event=" + evt); //$NON-NLS-1$
								UsbPortTypeTabItem.this.WTOExtraDelayTime = UsbPortTypeTabItem.this._WTOExtraDelayTimeText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(UsbPortTypeTabItem.this._WTOExtraDelayTimeText.getText());
								if (UsbPortTypeTabItem.this.deviceConfig != null) {
									UsbPortTypeTabItem.this.deviceConfig.setWriteDelayTime(UsbPortTypeTabItem.this.WTOExtraDelayTime);
									UsbPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
				}
			}
			initialize();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	void enableContextmenu(boolean enable) {
		if (enable && (this.popupMenu == null || this.contextMenu == null)) {
			this.popupMenu = new Menu(this.tabFolder.getShell(), SWT.POP_UP);
			//this.popupMenu = SWTResourceManager.getMenu("Contextmenu", this.tabFolder.getShell(), SWT.POP_UP);
			this.contextMenu = new ContextMenu(this.popupMenu, this.tabFolder);
			this.contextMenu.create();
		}
		else {
			this.popupMenu = null;
			this.contextMenu = null;
		}
		this.usbPortComposite.setMenu(this.popupMenu);
		this.usbPortDescriptionLabel.setMenu(this.popupMenu);
		this.productIDLabel.setMenu(this.popupMenu);

		this.timeOutComposite.setMenu(this.popupMenu);
		this.timeOutLabel.setMenu(this.popupMenu);
		this.timeOutButton.setMenu(this.popupMenu);
		this.ReadTimeoutLabel.setMenu(this.popupMenu);
		this.ReadStableIndexLabel.setMenu(this.popupMenu);
		this._WTOCharDelayTimeLabel.setMenu(this.popupMenu);
		this._WTOExtraDelayTimeLabel.setMenu(this.popupMenu);
		this.timeOutDescriptionLabel.setMenu(this.popupMenu);
	}

	/**
	 * @param deviceConfig the deviceConfig to set
	 */
	public void setDeviceConfig(DeviceConfiguration deviceConfig) {
		this.deviceConfig = deviceConfig;

		if (deviceConfig.getUsbPortType() == null)
			deviceConfig.createUsbPort();
			
		this.vendorID = deviceConfig.getUsbPortType().getVendorId();
		this.productID = deviceConfig.getUsbPortType().getProductId();
		
		if (deviceConfig.getUsbPortType().getProductString() != null) {
			this.productStringButton.setSelection(this.isUseProductString = true);
			this.productString =  deviceConfig.getUsbPortType().getProductString();
		}
		else {
			this.productStringButton.setSelection(this.isUseProductString = false);
		}			
		
		this.usbInterface = deviceConfig.getUsbPortType().getUsbInterface().getInterface().getValue();
		this.usbEndPointIn = deviceConfig.getUsbPortType().getUsbInterface().getEndPointIn();
		this.usbEndPointOut = deviceConfig.getUsbPortType().getUsbInterface().getEndPointOut();

		if (deviceConfig.getUsbPortType().getTimeOut() != null) {
			this.timeOutButton.setSelection(this.isUseTimeOut = true);
		}
		else {
			this.timeOutButton.setSelection(this.isUseTimeOut = false);
		}
		this.ReadTimeout = deviceConfig.getReadTimeOut();
		this.ReadStableIndex = deviceConfig.getReadStableIndex();
		this.WTOCharDelayTime = deviceConfig.getWriteCharDelayTime();
		this.WTOExtraDelayTime = deviceConfig.getWriteDelayTime();
		this.timeOutComposite.redraw();

		initialize();
	}

	/**
	 * initialize widget states
	 */
	private void initialize() {
		UsbPortTypeTabItem.this.vendorIDText.setText(UsbPortTypeTabItem.this.vendorID);
		UsbPortTypeTabItem.this.productIDText.setText(UsbPortTypeTabItem.this.productID);
		
		this.productStringText.setEnabled(isUseProductString);
		this.productStringText.setEditable(isUseProductString);
		if (this.isUseProductString) {
			UsbPortTypeTabItem.this.productStringButton.setSelection(true);
			UsbPortTypeTabItem.this.productStringText.setText(UsbPortTypeTabItem.this.productString);
		}
		
		UsbPortTypeTabItem.this.interfaceText.setText(UsbPortTypeTabItem.this.usbInterface);
		UsbPortTypeTabItem.this.endPointInText.setText(UsbPortTypeTabItem.this.usbEndPointIn);
		UsbPortTypeTabItem.this.endPointOutText.setText(UsbPortTypeTabItem.this.usbEndPointOut);

		UsbPortTypeTabItem.this.ReadTimeoutText.setText(GDE.STRING_EMPTY + UsbPortTypeTabItem.this.ReadTimeout);
		UsbPortTypeTabItem.this.ReadStableIndexText.setText(GDE.STRING_EMPTY + UsbPortTypeTabItem.this.ReadStableIndex);
		UsbPortTypeTabItem.this._WTOCharDelayTimeText.setText(GDE.STRING_EMPTY + UsbPortTypeTabItem.this.WTOCharDelayTime);
		UsbPortTypeTabItem.this._WTOExtraDelayTimeText.setText(GDE.STRING_EMPTY + UsbPortTypeTabItem.this.WTOExtraDelayTime);

		UsbPortTypeTabItem.this.timeOutButton.setSelection(UsbPortTypeTabItem.this.isUseTimeOut);
		enableTimeout();
	}

	private void enableTimeout() {
		if (UsbPortTypeTabItem.this.timeOutButton.getSelection()) {
			UsbPortTypeTabItem.this.ReadTimeoutLabel.setEnabled(true);
			UsbPortTypeTabItem.this.ReadTimeoutText.setEnabled(true);
			UsbPortTypeTabItem.this.ReadStableIndexLabel.setEnabled(true);
			UsbPortTypeTabItem.this.ReadStableIndexText.setEnabled(true);
			UsbPortTypeTabItem.this._WTOCharDelayTimeLabel.setEnabled(true);
			UsbPortTypeTabItem.this._WTOCharDelayTimeText.setEnabled(true);
			UsbPortTypeTabItem.this._WTOExtraDelayTimeLabel.setEnabled(true);
			UsbPortTypeTabItem.this._WTOExtraDelayTimeText.setEnabled(true);
		}
		else {
			UsbPortTypeTabItem.this.ReadTimeoutLabel.setEnabled(false);
			UsbPortTypeTabItem.this.ReadTimeoutText.setEnabled(false);
			UsbPortTypeTabItem.this.ReadStableIndexLabel.setEnabled(false);
			UsbPortTypeTabItem.this.ReadStableIndexText.setEnabled(false);
			UsbPortTypeTabItem.this._WTOCharDelayTimeLabel.setEnabled(false);
			UsbPortTypeTabItem.this._WTOCharDelayTimeText.setEnabled(false);
			UsbPortTypeTabItem.this._WTOExtraDelayTimeLabel.setEnabled(false);
			UsbPortTypeTabItem.this._WTOExtraDelayTimeText.setEnabled(false);
		}
	}
}
