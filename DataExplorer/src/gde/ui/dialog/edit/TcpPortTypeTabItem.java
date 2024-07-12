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
import gde.device.RespondType;
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
	Label													tcpPortDescriptionLabel, timeOutDescriptionLabel;
	Label													tcpHostAddressLabel, portNumberLabel, respondLabel, requestLabel, timeOutLabel;
	Text													tcpHostAddressText, portNumberText, requestText;
	CCombo												respondCombo;
	Button												requestButton, timeOutButton;
	Label													ReadTimeoutLabel, ReadStableIndexLabel, _WTOCharDelayTimeLabel, _WTOExtraDelayTimeLabel;
	Text													ReadTimeoutText, ReadStableIndexText, _WTOCharDelayTimeText, _WTOExtraDelayTimeText;

	String												tcpHostAddress			= GDE.STRING_EMPTY;
	String												portNumber					= GDE.STRING_EMPTY;
	RespondType										respondType					= RespondType.fromValue("CSV");
	String												request							= GDE.STRING_EMPTY;
	boolean												isUseRequest				= false;
	boolean												useTimeOut					= false;
	int														ReadTimeout					= 0;
	int														ReadStableIndex			= 0;
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
					this.tcpPortDescriptionLabel = new Label(this.tcpPortComposite, SWT.CENTER | SWT.WRAP);
					this.tcpPortDescriptionLabel.setText(Messages.getString(MessageIds.GDE_MSGT0976));
					this.tcpPortDescriptionLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.tcpPortDescriptionLabel.setBounds(12, 6, 602, 56);
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
					this.respondCombo.setItems(RespondType.valuesAsStingArray());
					this.respondCombo.setBounds(160, 201, 100, 20);
					this.respondCombo.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent evt) {
							log.log(java.util.logging.Level.FINEST, "flowControlCombo.widgetSelected, event=" + evt); //$NON-NLS-1$
							if (TcpPortTypeTabItem.this.deviceConfig != null) {
								TcpPortTypeTabItem.this.deviceConfig.setTcpRespondType(RespondType.fromValue(RespondType.valuesAsStingArray()[TcpPortTypeTabItem.this.respondCombo.getSelectionIndex()]));
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
										TcpPortTypeTabItem.this.request = "51";
										TcpPortTypeTabItem.this.requestText.setText(TcpPortTypeTabItem.this.request);
										byte[] request = StringHelper.byteString2ByteArray(TcpPortTypeTabItem.this.requestText.getText());
										TcpPortTypeTabItem.this.deviceConfig.setTcpRequest(request);									
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
										TcpPortTypeTabItem.this.deviceConfig.setReadTimeOut(TcpPortTypeTabItem.this.ReadTimeout = TcpPortTypeTabItem.this.deviceConfig.getReadTimeOut());
										TcpPortTypeTabItem.this.deviceConfig.setReadStableIndex(TcpPortTypeTabItem.this.ReadStableIndex = TcpPortTypeTabItem.this.deviceConfig.getReadStableIndex());
										TcpPortTypeTabItem.this.deviceConfig.setWriteCharDelayTime(TcpPortTypeTabItem.this.WTOCharDelayTime = TcpPortTypeTabItem.this.deviceConfig.getWriteCharDelayTime());
										TcpPortTypeTabItem.this.deviceConfig.setWriteDelayTime(TcpPortTypeTabItem.this.WTOExtraDelayTime = TcpPortTypeTabItem.this.deviceConfig.getWriteDelayTime());
										TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
									}
									else {
										TcpPortTypeTabItem.this.ReadTimeout = 0;
										TcpPortTypeTabItem.this.ReadStableIndex = 0;
										TcpPortTypeTabItem.this.WTOCharDelayTime = 0;
										TcpPortTypeTabItem.this.WTOExtraDelayTime = 0;
									}
								}
								else {
									if (TcpPortTypeTabItem.this.deviceConfig != null) {
										TcpPortTypeTabItem.this.deviceConfig.removeSerialPortTimeOut();
									}
									TcpPortTypeTabItem.this.ReadTimeout = 0;
									TcpPortTypeTabItem.this.ReadStableIndex = 0;
									TcpPortTypeTabItem.this.WTOCharDelayTime = 0;
									TcpPortTypeTabItem.this.WTOExtraDelayTime = 0;
								}
								TcpPortTypeTabItem.this.enableTimeout();
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
								TcpPortTypeTabItem.this.ReadTimeout = TcpPortTypeTabItem.this.ReadTimeoutText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(TcpPortTypeTabItem.this.ReadTimeoutText.getText());
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									TcpPortTypeTabItem.this.deviceConfig.setReadTimeOut(TcpPortTypeTabItem.this.ReadTimeout);
									TcpPortTypeTabItem.this.propsEditor.enableSaveButton(true);
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
								TcpPortTypeTabItem.this.ReadStableIndex = TcpPortTypeTabItem.this.ReadStableIndexText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(TcpPortTypeTabItem.this.ReadStableIndexText.getText());
								if (TcpPortTypeTabItem.this.deviceConfig != null) {
									TcpPortTypeTabItem.this.deviceConfig.setReadStableIndex(TcpPortTypeTabItem.this.ReadStableIndex);
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
								TcpPortTypeTabItem.this.WTOCharDelayTime = TcpPortTypeTabItem.this._WTOCharDelayTimeText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(TcpPortTypeTabItem.this._WTOCharDelayTimeText.getText());
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
								TcpPortTypeTabItem.this.WTOExtraDelayTime = TcpPortTypeTabItem.this._WTOExtraDelayTimeText.getText().equals(GDE.STRING_EMPTY) ? 0 : Integer.parseInt(TcpPortTypeTabItem.this._WTOExtraDelayTimeText.getText());
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
		this.tcpPortDescriptionLabel.setMenu(this.popupMenu);
		this.portNumberLabel.setMenu(this.popupMenu);

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
		
		if (deviceConfig.getTcpPortType() == null)
			deviceConfig.createTcpPort();

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
		this.ReadTimeout = deviceConfig.getReadTimeOut();
		this.ReadStableIndex = deviceConfig.getReadStableIndex();
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
		
		TcpPortTypeTabItem.this.ReadTimeoutText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.ReadTimeout);
		TcpPortTypeTabItem.this.ReadStableIndexText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.ReadStableIndex);
		TcpPortTypeTabItem.this._WTOCharDelayTimeText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.WTOCharDelayTime);
		TcpPortTypeTabItem.this._WTOExtraDelayTimeText.setText(GDE.STRING_EMPTY + TcpPortTypeTabItem.this.WTOExtraDelayTime);

		TcpPortTypeTabItem.this.timeOutButton.setSelection(TcpPortTypeTabItem.this.useTimeOut);
		enableTimeout();
	}

	private void enableTimeout() {
		if (TcpPortTypeTabItem.this.timeOutButton.getSelection()) {
			TcpPortTypeTabItem.this.ReadTimeoutLabel.setEnabled(true);
			TcpPortTypeTabItem.this.ReadTimeoutText.setEnabled(true);
			TcpPortTypeTabItem.this.ReadStableIndexLabel.setEnabled(true);
			TcpPortTypeTabItem.this.ReadStableIndexText.setEnabled(true);
			TcpPortTypeTabItem.this._WTOCharDelayTimeLabel.setEnabled(true);
			TcpPortTypeTabItem.this._WTOCharDelayTimeText.setEnabled(true);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeLabel.setEnabled(true);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeText.setEnabled(true);
		}
		else {
			TcpPortTypeTabItem.this.ReadTimeoutLabel.setEnabled(false);
			TcpPortTypeTabItem.this.ReadTimeoutText.setEnabled(false);
			TcpPortTypeTabItem.this.ReadStableIndexLabel.setEnabled(false);
			TcpPortTypeTabItem.this.ReadStableIndexText.setEnabled(false);
			TcpPortTypeTabItem.this._WTOCharDelayTimeLabel.setEnabled(false);
			TcpPortTypeTabItem.this._WTOCharDelayTimeText.setEnabled(false);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeLabel.setEnabled(false);
			TcpPortTypeTabItem.this._WTOExtraDelayTimeText.setEnabled(false);
		}
	}
}
