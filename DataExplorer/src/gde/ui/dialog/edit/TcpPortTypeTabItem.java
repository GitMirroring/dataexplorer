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
    
    Copyright (c) 2008,2009,2010,2011,2012,2013,2014,2015,2016,2017,2018,2019,2020,2021,2022,2023,2024 Winfried Bruegmann
****************************************************************************************/
package gde.ui.dialog.edit;

import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
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
import gde.device.TcpRespondType;
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
public class TcpPortTypeTabItem extends CTabItem {
	final static Logger						log								= Logger.getLogger(ChannelTypeTabItem.class.getName());

	Composite											tcpPortComposite, timeOutComposite;
	Label													serialPortDescriptionLabel, timeOutDescriptionLabel;
	Label													tcpHostAddressLabel, portNumberLabel, respondLabel, requestLabel, timeOutLabel;
	Text													tcpHostAddressText, portNumberText, requestText;
	CCombo												respondCombo;
	Button												requestButton, timeOutButton;
	Label													_RTOCharDelayTimeLabel, _RTOExtraDelayTimeLabel, _WTOCharDelayTimeLabel, _WTOExtraDelayTimeLabel;
	Text													_RTOCharDelayTimeText, _RTOExtraDelayTimeText, _WTOCharDelayTimeText, _WTOExtraDelayTimeText;

	String												tcpHostAddress			= GDE.STRING_EMPTY;
	String												portNumber					= GDE.STRING_EMPTY;
	TcpRespondType								respondType					= TcpRespondType.fromValue("CSV");
	String												request							= GDE.STRING_EMPTY;
	boolean												isUseRequest				= false;
	boolean												useTimeOut					= false;
	int														RTOCharDelayTime		= 0;
	int														RTOExtraDelayTime		= 0;
	int														WTOCharDelayTime		= 0;
	int														WTOExtraDelayTime		= 0;
	DeviceConfiguration						deviceConfig;
	Menu													popupMenu;
	ContextMenu										contextMenu;

	final CTabFolder							tabFolder;
	final DevicePropertiesEditor	propsEditor;

	public TcpPortTypeTabItem(CTabFolder parent, int style, int index) {
		super(parent, style, index);
		this.tabFolder = parent;
		this.propsEditor = DevicePropertiesEditor.getInstance();
		log.log(java.util.logging.Level.FINE, "TcpPortTypeTabItem "); //$NON-NLS-1$
		initGUI();
	}

	private void initGUI() {
		try {
			SWTResourceManager.registerResourceUser(this);
			this.setText(Messages.getString(MessageIds.GDE_MSGT0975));
			this.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
			{
				this.tcpPortComposite = new Composite(this.tabFolder, SWT.NONE);
				this.tcpPortComposite.setLayout(null);
				this.setControl(this.tcpPortComposite);
				this.tcpPortComposite.addHelpListener(new HelpListener() {			
					public void helpRequested(HelpEvent evt) {
						log.log(Level.FINEST, "tcpPortComposite.helpRequested " + evt); //$NON-NLS-1$
						DataExplorer.getInstance().openHelpDialog("", "HelpInfo_A1.html#device_properties_serial_port"); //$NON-NLS-1$ //$NON-NLS-2$
					}
				});
				this.tcpPortComposite.addFocusListener(new FocusAdapter() {
					@Override
					public void focusLost(FocusEvent focusevent) {
						log.log(java.util.logging.Level.FINEST, "tcpPortComposite.focusLost, event=" + focusevent); //$NON-NLS-1$
						TcpPortTypeTabItem.this.enableContextmenu(false);
					}

					@Override
					public void focusGained(FocusEvent focusevent) {
						log.log(java.util.logging.Level.FINEST, "tcpPortComposite.focusGained, event=" + focusevent); //$NON-NLS-1$
						TcpPortTypeTabItem.this.enableContextmenu(true);
					}
				});
				{
					this.serialPortDescriptionLabel = new Label(this.tcpPortComposite, SWT.CENTER | SWT.WRAP);
					this.serialPortDescriptionLabel.setText(Messages.getString(MessageIds.GDE_MSGT0976));
					this.serialPortDescriptionLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.serialPortDescriptionLabel.setBounds(12, 6, 602, 56);
				}
				{
					this.tcpHostAddressLabel = new Label(this.tcpPortComposite, SWT.LEFT);
					this.tcpHostAddressLabel.setText(Messages.getString(MessageIds.GDE_MSGT0977));
					this.tcpHostAddressLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.tcpHostAddressLabel.setBounds(15, 74, 100, 20);
				}
				{
					this.tcpHostAddressText = new Text(this.tcpPortComposite, SWT.BORDER);
					this.tcpHostAddressText.setBounds(141, 76, 180, 20);
					this.tcpHostAddressText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.tcpHostAddressText.setEditable(true);
					this.tcpHostAddressText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "tcpHostAddressText.keyReleased, event=" + evt); //$NON-NLS-1$
							TcpPortTypeTabItem.this.portNumber = TcpPortTypeTabItem.this.tcpHostAddressText.getText();
							if (TcpPortTypeTabItem.this.deviceConfig != null) {
								TcpPortTypeTabItem.this.deviceConfig.setTcpHostAddress(TcpPortTypeTabItem.this.tcpHostAddressText.getText().trim());
								TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					this.portNumberLabel = new Label(this.tcpPortComposite, SWT.LEFT);
					this.portNumberLabel.setText(Messages.getString(MessageIds.GDE_MSGT0978));
					this.portNumberLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.portNumberLabel.setBounds(15, 104, 100, 20);
				}
				{
					this.portNumberText = new Text(this.tcpPortComposite, SWT.BORDER);
					this.portNumberText.setBounds(141, 106, 180, 20);
					this.portNumberText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.portNumberText.setEditable(true);
					this.portNumberText.addVerifyListener(new VerifyListener() {
						public void verifyText(VerifyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "portNumberText.verifyText, event=" + evt); //$NON-NLS-1$
							evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
						}
					});
					this.portNumberText.addKeyListener(new KeyAdapter() {
						@Override
						public void keyReleased(KeyEvent evt) {
							log.log(java.util.logging.Level.FINEST, "portNumberText.keyReleased, event=" + evt); //$NON-NLS-1$
							TcpPortTypeTabItem.this.portNumber = TcpPortTypeTabItem.this.portNumberText.getText();
							if (TcpPortTypeTabItem.this.deviceConfig != null) {
								TcpPortTypeTabItem.this.deviceConfig.setTcpPortNumber(TcpPortTypeTabItem.this.portNumberText.getText().trim());
								TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					this.respondLabel = new Label(this.tcpPortComposite, SWT.LEFT);
					this.respondLabel.setText("Respond Type");
					this.respondLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.respondLabel.setBounds(25, 200, 150, 20);
				}
				{
					this.respondCombo = new CCombo(this.tcpPortComposite, SWT.BORDER);
					this.respondCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.respondCombo.setItems(TcpRespondType.valuesAsStingArray());
					this.respondCombo.setBounds(160, 201, 100, 20);
					this.respondCombo.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent evt) {
							log.log(java.util.logging.Level.FINEST, "flowControlCombo.widgetSelected, event=" + evt); //$NON-NLS-1$
							if (TcpPortTypeTabItem.this.deviceConfig != null) {
								TcpPortTypeTabItem.this.deviceConfig.setTcpRespondType(TcpRespondType.fromValue(TcpRespondType.valuesAsStingArray()[TcpPortTypeTabItem.this.respondCombo.getSelectionIndex()]));
								TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
							}
						}
					});
				}
				{
					Group requestGroup = new Group(this.tcpPortComposite, SWT.BORDER);
					requestGroup.setText("Request (optional)");
					requestGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					requestGroup.setBounds(15, 230, 320, 120);

					{
						Label requestGroupLabel = new Label(requestGroup, SWT.CENTER | SWT.WRAP);
						requestGroupLabel.setText("Optional request, will be send as byte array, if required to request a respond");
						requestGroupLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						requestGroupLabel.setBounds(10, 10, 250, 40);
					}
					{
						this.requestButton = new Button(requestGroup, SWT.CHECK | SWT.BORDER);
						this.requestButton.setBounds(8, 62, 15, 15);
						this.requestButton.addSelectionListener(new SelectionAdapter() {
							@Override
							public void widgetSelected(SelectionEvent evt) {
								log.log(java.util.logging.Level.FINEST, "requestButton.widgetSelected, event=" + evt); //$NON-NLS-1$
								TcpPortTypeTabItem.this.isUseRequest = requestButton.getSelection();
								TcpPortTypeTabItem.this.requestLabel.setEnabled(isUseRequest);
								TcpPortTypeTabItem.this.requestText.setEnabled(isUseRequest);
								TcpPortTypeTabItem.this.requestText.setEditable(isUseRequest);

								if (TcpPortTypeTabItem.this.isUseRequest) {								
									if (TcpPortTypeTabItem.this.deviceConfig != null) {
										TcpPortTypeTabItem.this.deviceConfig.setTcpRequest( new byte[] {0x51});
										TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
								}
								else {
									if (TcpPortTypeTabItem.this.deviceConfig != null) {
										TcpPortTypeTabItem.this.deviceConfig.removeTcpRequest();
										TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
								}
								TcpPortTypeTabItem.this.enableTimeout();
							}
						});
					}
					{
						this.requestLabel = new Label(requestGroup, SWT.LEFT);
						this.requestLabel.setText("two char per byte");
						this.requestLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.requestLabel.setBounds(30, 60, 140, 20);
					}
					{
						this.requestText = new Text(requestGroup, SWT.BORDER);
						this.requestText.setBounds(160, 60, 100, 20);
						this.requestText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this.requestText.setEditable(true);
						this.requestText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "requestText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.HEXADECIMAL, evt.text);
							}
						});
						this.requestText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "requestText.keyReleased, event=" + evt); //$NON-NLS-1$
								TcpPortTypeTabItem.this.request = TcpPortTypeTabItem.this.requestText.getText();
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									byte[] request = StringHelper.byteString2ByteArray(TcpPortTypeTabItem.this.requestText.getText());
									TcpPortTypeTabItem.this.deviceConfig.setTcpRequest(request);
									TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
				}
				{
					this.timeOutComposite = new Composite(this.tcpPortComposite, SWT.BORDER);
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
								TcpPortTypeTabItem.this.useTimeOut = TcpPortTypeTabItem.this.timeOutButton.getSelection();
								if (TcpPortTypeTabItem.this.useTimeOut) {
									if (TcpPortTypeTabItem.this.deviceConfig != null) {
										TcpPortTypeTabItem.this.deviceConfig.setReadTimeOut(TcpPortTypeTabItem.this.RTOCharDelayTime = TcpPortTypeTabItem.this.deviceConfig.getReadTimeOut());
										TcpPortTypeTabItem.this.deviceConfig.setReadStableIndex(TcpPortTypeTabItem.this.RTOExtraDelayTime = TcpPortTypeTabItem.this.deviceConfig.getReadStableIndex());
										TcpPortTypeTabItem.this.deviceConfig.setWriteCharDelayTime(TcpPortTypeTabItem.this.WTOCharDelayTime = TcpPortTypeTabItem.this.deviceConfig.getWriteCharDelayTime());
										TcpPortTypeTabItem.this.deviceConfig.setWriteDelayTime(TcpPortTypeTabItem.this.WTOExtraDelayTime = TcpPortTypeTabItem.this.deviceConfig.getWriteDelayTime());
										TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
									else {
										TcpPortTypeTabItem.this.RTOCharDelayTime = 0;
										TcpPortTypeTabItem.this.RTOExtraDelayTime = 0;
										TcpPortTypeTabItem.this.WTOCharDelayTime = 0;
										TcpPortTypeTabItem.this.WTOExtraDelayTime = 0;
									}
								}
								else {
									if (TcpPortTypeTabItem.this.deviceConfig != null) {
										TcpPortTypeTabItem.this.deviceConfig.removeSerialPortTimeOut();
									}
									TcpPortTypeTabItem.this.RTOCharDelayTime = 0;
									TcpPortTypeTabItem.this.RTOExtraDelayTime = 0;
									TcpPortTypeTabItem.this.WTOCharDelayTime = 0;
									TcpPortTypeTabItem.this.WTOExtraDelayTime = 0;
								}
								TcpPortTypeTabItem.this.enableTimeout();
							}
						});
					}
					{
						this._RTOCharDelayTimeLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this._RTOCharDelayTimeLabel.setText(Messages.getString(MessageIds.GDE_MSGT0587));
						this._RTOCharDelayTimeLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._RTOCharDelayTimeLabel.setBounds(6, 100, 140, 20);
					}
					{
						this._RTOCharDelayTimeText = new Text(this.timeOutComposite, SWT.BORDER);
						this._RTOCharDelayTimeText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._RTOCharDelayTimeText.setBounds(162, 100, 70, 20);
						this._RTOCharDelayTimeText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_RTOCharDelayTimeText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
							}
						});
						this._RTOCharDelayTimeText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_RTOCharDelayTimeText.keyReleased, event=" + evt); //$NON-NLS-1$
								TcpPortTypeTabItem.this.RTOCharDelayTime = Integer.parseInt(TcpPortTypeTabItem.this._RTOCharDelayTimeText.getText());
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									TcpPortTypeTabItem.this.deviceConfig.setReadTimeOut(TcpPortTypeTabItem.this.RTOCharDelayTime);
									TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
								}
							}
						});
					}
					{
						this._RTOExtraDelayTimeLabel = new Label(this.timeOutComposite, SWT.RIGHT);
						this._RTOExtraDelayTimeLabel.setText(Messages.getString(MessageIds.GDE_MSGT0588));
						this._RTOExtraDelayTimeLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._RTOExtraDelayTimeLabel.setBounds(6, 130, 140, 20);
					}
					{
						this._RTOExtraDelayTimeText = new Text(this.timeOutComposite, SWT.BORDER);
						this._RTOExtraDelayTimeText.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
						this._RTOExtraDelayTimeText.setBounds(162, 130, 70, 20);
						this._RTOExtraDelayTimeText.addVerifyListener(new VerifyListener() {
							public void verifyText(VerifyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_RTOExtraDelayTimeText.verifyText, event=" + evt); //$NON-NLS-1$
								evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
							}
						});
						this._RTOExtraDelayTimeText.addKeyListener(new KeyAdapter() {
							@Override
							public void keyReleased(KeyEvent evt) {
								log.log(java.util.logging.Level.FINEST, "_RTOExtraDelayTimeText.keyReleased, event=" + evt); //$NON-NLS-1$
								TcpPortTypeTabItem.this.RTOExtraDelayTime = Integer.parseInt(TcpPortTypeTabItem.this._RTOExtraDelayTimeText.getText());
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									TcpPortTypeTabItem.this.deviceConfig.setReadStableIndex(TcpPortTypeTabItem.this.RTOExtraDelayTime);
									TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
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
								TcpPortTypeTabItem.this.WTOCharDelayTime = Integer.parseInt(TcpPortTypeTabItem.this._WTOCharDelayTimeText.getText());
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									TcpPortTypeTabItem.this.deviceConfig.setWriteCharDelayTime(TcpPortTypeTabItem.this.WTOCharDelayTime);
									TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
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
								TcpPortTypeTabItem.this.WTOExtraDelayTime = Integer.parseInt(TcpPortTypeTabItem.this._WTOExtraDelayTimeText.getText());
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									TcpPortTypeTabItem.this.deviceConfig.setWriteDelayTime(TcpPortTypeTabItem.this.WTOExtraDelayTime);
									TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
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
		this.tcpPortComposite.setMenu(this.popupMenu);
		this.serialPortDescriptionLabel.setMenu(this.popupMenu);
		this.portNumberLabel.setMenu(this.popupMenu);

		this.timeOutComposite.setMenu(this.popupMenu);
		this.timeOutLabel.setMenu(this.popupMenu);
		this.timeOutButton.setMenu(this.popupMenu);
		this._RTOCharDelayTimeLabel.setMenu(this.popupMenu);
		this._RTOExtraDelayTimeLabel.setMenu(this.popupMenu);
		this._WTOCharDelayTimeLabel.setMenu(this.popupMenu);
		this._WTOExtraDelayTimeLabel.setMenu(this.popupMenu);
		this.timeOutDescriptionLabel.setMenu(this.popupMenu);
	}

	/**
	 * @param deviceConfig the deviceConfig to set
	 */
	public void setDeviceConfig(DeviceConfiguration deviceConfig) {
		this.deviceConfig = deviceConfig;

		this.tcpHostAddress = deviceConfig.getTcpPortType().getAddress();
		this.portNumber = deviceConfig.getTcpPortType().getPort();
		
		this.respondType = deviceConfig.getTcpPortType().getRespond();
		if (deviceConfig.getTcpPortType().getRequest() != null) {
			this.requestButton.setSelection(this.isUseRequest = true);
			StringBuilder sb = new StringBuilder();
			for (byte b : deviceConfig.getTcpPortType().getRequest())
				sb.append(String.format("%02X",b));		
			this.request = sb.toString();
		}
		else {
			this.requestButton.setSelection(this.isUseRequest = false);
		}			

		if (deviceConfig.getTcpPortType().getTimeOut() != null) {
			this.timeOutButton.setSelection(this.useTimeOut = true);
		}
		else {
			this.timeOutButton.setSelection(this.useTimeOut = false);
		}
		this.RTOCharDelayTime = deviceConfig.getReadTimeOut();
		this.RTOExtraDelayTime = deviceConfig.getReadStableIndex();
		this.WTOCharDelayTime = deviceConfig.getWriteCharDelayTime();
		this.WTOExtraDelayTime = deviceConfig.getWriteDelayTime();
		this.timeOutComposite.redraw();

		initialize();
	}

	/**
	 * search the index of a given string within the items of a combo box items
	 * @param useCombo
	 * @param searchString
	 * @return
	 */
	private int getSelectionIndex(CCombo useCombo, String searchString) {
		int searchIndex = 0;
		for (String item : useCombo.getItems()) {
			if (item.equals(searchString)) break;
			++searchIndex;
		}
		return searchIndex;
	}

	/**
	 * initialize widget states
	 */
	private void initialize() {
		TcpPortTypeTabItem.this.tcpHostAddressText.setText(TcpPortTypeTabItem.this.tcpHostAddress);
		TcpPortTypeTabItem.this.portNumberText.setText(TcpPortTypeTabItem.this.portNumber);
		
		TcpPortTypeTabItem.this.respondCombo.select(getSelectionIndex(TcpPortTypeTabItem.this.respondCombo, TcpPortTypeTabItem.this.respondType.toString()));

		this.requestText.setEnabled(isUseRequest);
		this.requestText.setEditable(isUseRequest);
		if (this.isUseRequest) {
			TcpPortTypeTabItem.this.requestButton.setSelection(true);
			TcpPortTypeTabItem.this.requestText.setText(TcpPortTypeTabItem.this.request);
		}
		
		TcpPortTypeTabItem.this._RTOCharDelayTimeText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.RTOCharDelayTime);
		TcpPortTypeTabItem.this._RTOExtraDelayTimeText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.RTOExtraDelayTime);
		TcpPortTypeTabItem.this._WTOCharDelayTimeText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.WTOCharDelayTime);
		TcpPortTypeTabItem.this._WTOExtraDelayTimeText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.WTOExtraDelayTime);

		TcpPortTypeTabItem.this.timeOutButton.setSelection(TcpPortTypeTabItem.this.useTimeOut);
		enableTimeout();
	}

	private void enableTimeout() {
		if (TcpPortTypeTabItem.this.timeOutButton.getSelection()) {
			TcpPortTypeTabItem.this._RTOCharDelayTimeLabel.setEnabled(true);
			TcpPortTypeTabItem.this._RTOCharDelayTimeText.setEnabled(true);
			TcpPortTypeTabItem.this._RTOExtraDelayTimeLabel.setEnabled(true);
			TcpPortTypeTabItem.this._RTOExtraDelayTimeText.setEnabled(true);
			TcpPortTypeTabItem.this._WTOCharDelayTimeLabel.setEnabled(true);
			TcpPortTypeTabItem.this._WTOCharDelayTimeText.setEnabled(true);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeLabel.setEnabled(true);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeText.setEnabled(true);
		}
		else {
			TcpPortTypeTabItem.this._RTOCharDelayTimeLabel.setEnabled(false);
			TcpPortTypeTabItem.this._RTOCharDelayTimeText.setEnabled(false);
			TcpPortTypeTabItem.this._RTOExtraDelayTimeLabel.setEnabled(false);
			TcpPortTypeTabItem.this._RTOExtraDelayTimeText.setEnabled(false);
			TcpPortTypeTabItem.this._WTOCharDelayTimeLabel.setEnabled(false);
			TcpPortTypeTabItem.this._WTOCharDelayTimeText.setEnabled(false);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeLabel.setEnabled(false);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeText.setEnabled(false);
		}
	}
}
