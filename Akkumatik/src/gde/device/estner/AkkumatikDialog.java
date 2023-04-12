package gde.device.estner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.xml.sax.SAXException;

import gde.GDE;
import gde.config.Settings;
import gde.data.Channels;
import gde.device.DataTypes;
import gde.device.DeviceDialog;
import gde.log.Level;
import gde.log.LogFormatter;
import gde.messages.Messages;
import gde.ui.SWTResourceManager;
import gde.utils.StringHelper;

public class AkkumatikDialog extends DeviceDialog {
	final static Logger				log												= Logger.getLogger(AkkumatikDialog.class.getName());
	
	final static String AKKUMATIK_SETTINGS_XSD = "Akkumatik_Settings_V01.xsd";
	final static String	AKKUMATIK_CONFIGURATION_SUFFIX	= "/Akkumatik_Settings";																																																									//$NON-NLS-1$
	final static String	DEVICE_NAME					= "Akkumatik";
	final static String[] STOP_MODE = {Messages.getString(MessageIds.GDE_MSGT3464), "Gradient", "∆-Peak-1", "∆-Peak-2", "∆-Peak-3"};
	final static String[] STOP_MODE_NI = {Messages.getString(MessageIds.GDE_MSGT3464), "Gradient", "∆-Peak-1", "∆-Peak-2", "∆-Peak-3"};
	final static String[] STOP_MODE_PB = {Messages.getString(MessageIds.GDE_MSGT3464)};
	final static String[] STOP_MODE_LI = {Messages.getString(MessageIds.GDE_MSGT3464)};
	final static String[] CHARGE_CURRENT_TYPE = {"Auto", "Limit", "Fix"};
	final static String[] CHARGE_CURRENT_TYPE_NI = {"Auto", "Limit", "Fix"};
	final static String[] CHARGE_CURRENT_TYPE_PB = {"Fix"};
	final static String[] CHARGE_CURRENT_TYPE_LI = {"Fix"};
	
	final static byte[] A2Z = {0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A};
	final static byte[] OPEN_DIALOG = {0x02, 0x33, 0x30, 0x41, 0x03};
	final static byte[] CLOSE_DIALOG = {0x02, 0x33, 0x30, 0x41, 0x03};
	final static byte[] START_COM = {0x02, 0x34, 0x34, 0x42, 0x03};
	final static byte[] STOP_COM = {0x02, 0x34, 0x31, 0x47, 0x03};
	
	static Handler										logHandler;
	static Logger											rootLogger;

	final Akkumatik								device;						// get device specific things, get serial port, ...
	final AkkumatikSerialPort			serialPort;				// open/close port execute getData()....
	final Channels								channels;					// interaction with channels, source of all records
	final Settings								settings;					// application configuration settings
	
	AMSettings 		akkumatikSettings = null;
	List<Setting> akkuSettings = null;
	Setting 			actualAkkuSetting = null;

	CCombo programNameSelection;
	CCombo batteryTypeCombo, cellCountCombo, programCombo, cycleCountCombo, chargeModeCombo, chargeCurrentCombo;
	CCombo capacityCombo, chargeStopModeCombo, currentModeCombo, chargeAmountCombo, dischargeCurrentCombo;
	Button btnChannel1, btnChannel2, btnTransfer, btnStart, btnStop, removeEntry, editEntry;
	Label statusLabel;
	Group grpCharge, grpDischarge;
	Composite composite_3;
	int 	programSelectionIndex = 0;
	int		comboHeight						= GDE.IS_LINUX ? 22 : GDE.IS_MAC ? 20 : 18; //(int) (GDE.WIDGET_FONT_SIZE * (GDE.IS_LINUX ? 2.5 : 1.8));

	static boolean isDataAvailable = false;
	static byte[] data2Write = new byte[0];
	
	/**
	 * default constructor initialize all variables required
	 * @param parent Shell
	 * @param useDevice device specific class implementation
	 */
	public AkkumatikDialog(Shell parent, Akkumatik useDevice) {
		super(parent);
		setText("Akkumatik Dialog");
		this.device = useDevice;
		this.serialPort = this.device.getCommunicationPort();
		this.channels = Channels.getInstance();
		this.settings = Settings.getInstance();
		String basePath = Settings.getApplHomePath();
		this.akkumatikSettings = new ObjectFactory().createAMSettings();
		try {
			Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
					.newSchema(new StreamSource(AkkumatikDialog.class.getClassLoader().getResourceAsStream("resource/" + AkkumatikDialog.AKKUMATIK_SETTINGS_XSD))); //$NON-NLS-1$
			JAXBContext jc = JAXBContext.newInstance("gde.device.estner"); //$NON-NLS-1$
			AkkumatikDialog.log.log(Level.TIME, "XSD init time = " + StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - GDE.StartTime))); //$NON-NLS-1$ //$NON-NLS-2$
			long time = new Date().getTime();
			// read existing settings XML
			Unmarshaller unmarshaller = jc.createUnmarshaller();
			unmarshaller.setSchema(schema);
			this.akkumatikSettings = (AMSettings) unmarshaller.unmarshal(new File(basePath + AkkumatikDialog.AKKUMATIK_CONFIGURATION_SUFFIX
				 + GDE.FILE_ENDING_DOT_XML));
			AkkumatikDialog.log.log(Level.TIME, "read setup XML time = " + StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - time))); //$NON-NLS-1$ //$NON-NLS-2$
		}
		catch (SAXException | JAXBException e) {
			AkkumatikDialog.log.log(Level.SEVERE, e.getMessage(), e);
		}
		catch (IllegalArgumentException e) {
			this.akkumatikSettings = new ObjectFactory().createAMSettings();
			this.akkumatikSettings.setDialogSettings(new ObjectFactory().createDialogSettings());
			this.akkumatikSettings.getDialogSettings().setChannel1(new ObjectFactory().createActiveSetting());
			this.akkumatikSettings.getDialogSettings().getChannel1().setActiveSetting(0);
			this.akkumatikSettings.getDialogSettings().setChannel2(new ObjectFactory().createActiveSetting());
			this.akkumatikSettings.getDialogSettings().getChannel2().setActiveSetting(0);
			this.akkumatikSettings.getDialogSettings().setActiveChannel(1);
			this.akkumatikSettings.setAkkuSettings(new ObjectFactory().createAkkuSettings());
//	    <Setting Name="Test">
//      <SettingType>Akku</SettingType>
//      <Channel>1</Channel>
//      <AccuTyp>0</AccuTyp>
//      <CurrentMode>2</CurrentMode>
//      <Amount>0</Amount>
//      <Capacity>1000</Capacity>
//      <CellCount>34</CellCount>
//      <Program>2</Program>
//      <Cycle>0</Cycle>
//      <ChargeMode>0</ChargeMode>
//      <ChargeStopMode>0</ChargeStopMode>
//      <ChargeCurrent>100</ChargeCurrent>
//      <DisChargeCurrent>100</DisChargeCurrent>
			this.akkumatikSettings.getAkkuSettings().getSetting().add(createAkkuSetting("Initial", 1, 0, 2, 0, 1000, 34, 2, 0, 0, 0, 100, 100));
		
			this.akkumatikSettings.setParameterSettings(new ObjectFactory().createParameterSettings());
		}
	}

	/**
	 * @param name 							<Setting Name="Test">
	 * @param channel						<Channel>1</Channel>
	 * @param accuType					<AccuTyp>0</AccuTyp>
	 * @param currentMode				<CurrentMode>2</CurrentMode>
	 * @param amount						<Amount>0</Amount>
	 * @param capacity					<Capacity>1000</Capacity>
	 * @param cellCount					<CellCount>34</CellCount>
	 * @param program						<Program>2</Program>
	 * @param cycle							<Cycle>0</Cycle>
	 * @param chargeMode				<ChargeMode>0</ChargeMode>
	 * @param chargeStopMode		<ChargeStopMode>0</ChargeStopMode>
	 * @param chargeCurrent			<ChargeCurrent>100</ChargeCurrent>
	 * @param dischargeCurrent	<DisChargeCurrent>100</DisChargeCurrent>
	 * @return
	 */
	private Setting createAkkuSetting(String name, int channel, int accuType, int currentMode, int amount, int capacity, int cellCount, int program, int cycle, int chargeMode, int chargeStopMode,
			int chargeCurrent, int dischargeCurrent) {
		Setting akkuSetting = new ObjectFactory().createSetting();
		akkuSetting.setName(name);
		akkuSetting.setSettingType("Akku");
		akkuSetting.setChannel(channel);
		akkuSetting.setAccuTyp(accuType);
		akkuSetting.setCurrentMode(currentMode);
		akkuSetting.setAmount(amount);
		akkuSetting.setCapacity(capacity);
		akkuSetting.setCellCount(cellCount);
		akkuSetting.setProgram(program);
		akkuSetting.setCycle(cycle);
		akkuSetting.setChargeMode(chargeMode);
		akkuSetting.setChargeStopMode(chargeStopMode);
		akkuSetting.setChargeCurrent(chargeCurrent);
		akkuSetting.setDisChargeCurrent(dischargeCurrent);
		return akkuSetting;
	}

	/**
	 * Open the dialog.
	 * @return the result
	 */
	@Override	
	public void open() {
		//check if gatherer thread is running, if not start to enable writing data
		GathererThread dataGathererThread = this.device.getDataGathererThread();
		if (this.serialPort != null && dataGathererThread == null || dataGathererThread.isCollectDataStopped) {
			this.device.open_closeCommPort();
		}
		if (this.serialPort != null && this.serialPort.isConnected())
			AkkumatikDialog.setData2Write(AkkumatikDialog.OPEN_DIALOG);
		
		createContents();
		dialogShell.addMouseTrackListener(new MouseTrackAdapter() {
			@Override
			public void mouseEnter(MouseEvent evt) {
				AkkumatikDialog.log.log(Level.FINE, "boundsComposite.mouseEnter, event=" + evt); //$NON-NLS-1$
				fadeOutAplhaBlending(evt, dialogShell.getSize(), 10, 10, 10, 15);
			}

			@Override
			public void mouseHover(MouseEvent evt) {
				AkkumatikDialog.log.log(Level.FINEST, "boundsComposite.mouseHover, event=" + evt); //$NON-NLS-1$
			}

			@Override
			public void mouseExit(MouseEvent evt) {
				AkkumatikDialog.log.log(Level.FINE, "boundsComposite.mouseExit, event=" + evt); //$NON-NLS-1$
				fadeInAlpaBlending(evt, dialogShell.getSize(), 10, 10, -10, 15);
			}
		});
		dialogShell.open();
		dialogShell.layout();
		Display display = getParent().getDisplay();
		while (!dialogShell.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		
		if (this.serialPort != null && this.serialPort.isConnected())
			AkkumatikDialog.setData2Write(AkkumatikDialog.CLOSE_DIALOG);
		
		try {
			Long time = new Date().getTime();
			String basePath = Settings.getApplHomePath();
			JAXBContext jc = JAXBContext.newInstance("gde.device.estner"); //$NON-NLS-1$
			// store back manipulated XML
			Marshaller marshaller = jc.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.valueOf(true));
			marshaller.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, AkkumatikDialog.AKKUMATIK_SETTINGS_XSD);
			marshaller.marshal(akkumatikSettings, new FileOutputStream(basePath + AkkumatikDialog.AKKUMATIK_CONFIGURATION_SUFFIX + GDE.FILE_ENDING_DOT_XML));
			AkkumatikDialog.log.log(Level.TIME, "write setup XML time = " + StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - time))); //$NON-NLS-1$ //$NON-NLS-2$
		}
		catch (FileNotFoundException | JAXBException e) {
			AkkumatikDialog.log.log(Level.SEVERE, e.getMessage(), e);
		}

	}

	/**
	 * Create contents of the dialog.
	 */
	private void createContents() {
		this.shellAlpha = Settings.getInstance().getDialogAlphaValue();
		this.isAlphaEnabled = Settings.getInstance().isDeviceDialogAlphaEnabled();

		AkkumatikDialog.log.log(Level.FINE, "dialogShell.isDisposed() " + ((this.dialogShell == null) ? "null" : this.dialogShell.isDisposed())); //$NON-NLS-1$ //$NON-NLS-2$
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
			this.dialogShell.addDisposeListener(new DisposeListener() {
				public void widgetDisposed(DisposeEvent arg0) {
					if (!AkkumatikDialog.this.isDisposed())
						AkkumatikDialog.this.getDialogShell().dispose();
				}
			});
			this.dialogShell.addListener(SWT.Traverse, new Listener() {
				public void handleEvent(Event event) {
					switch (event.detail) {
					case SWT.TRAVERSE_ESCAPE:
						AkkumatikDialog.this.dialogShell.close();
						event.detail = SWT.TRAVERSE_NONE;
						event.doit = false;
						break;
					}
				}
			});
			this.dialogShell.setSize(390, GDE.IS_LINUX ? 460 : 490);
			this.dialogShell.setLayout(new FormLayout());
			this.dialogShell.setLocation(getParent().toDisplay(getParent().getSize().x / 2 - 175, 100));

			Composite composite = new Composite(dialogShell, SWT.NONE);
			composite.setLayout(new RowLayout(SWT.HORIZONTAL));
			FormData fd_composite = new FormData();
			fd_composite.bottom = new FormAttachment(0, GDE.IS_MAC ? 380 : 365);
			fd_composite.top = new FormAttachment(0, 10);
			fd_composite.right = new FormAttachment(0, 380);
			fd_composite.left = new FormAttachment(0, 10);
			composite.setLayoutData(fd_composite);

			Composite composite_1 = new Composite(composite, SWT.NONE);
			final GridLayout shellLayout = new GridLayout(4, false);
      shellLayout.verticalSpacing = 0; // Vertical spacing between cells
      shellLayout.horizontalSpacing = 0; // Horizontal spacing between cells
      shellLayout.marginWidth = 0; // Horizontal margin around the layout
			composite_1.setLayout(shellLayout);
			composite_1.setLayoutData(new RowData(365, SWT.DEFAULT));

			programNameSelection = new CCombo(composite_1, SWT.BORDER);
			programNameSelection.setEditable(false);
			programNameSelection.setBackground(this.application.COLOR_WHITE);
			programNameSelection.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting = akkuSettings.get(programNameSelection.getSelectionIndex());
					//update combo entries according selected battery type and output channel
					update(Akkumatik.ACCU_TYPES[akkuSettings.get(programNameSelection.getSelectionIndex()).getAccuTyp()], akkuSettings.get(programNameSelection.getSelectionIndex()).getChannel());
					akkumatikSettings.getDialogSettings().setActiveChannel(btnChannel1.getSelection() ? 1 : 2);
					switch (akkuSettings.get(programNameSelection.getSelectionIndex()).getChannel()) {
					case 1:
						akkumatikSettings.getDialogSettings().getChannel1().setActiveSetting(programNameSelection.getSelectionIndex());
						break;
					case 2:
						akkumatikSettings.getDialogSettings().getChannel2().setActiveSetting(programNameSelection.getSelectionIndex());
						break;
					}
				}
			});
			this.programNameSelection.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent evt) {
					if (evt.character == SWT.CR) {
						programNameSelection.setEditable(false);
						actualAkkuSetting.setName(programNameSelection.getText());
						//actualAkkuSetting = akkuSettings.get(programSelectionIndex);
						if (akkumatikSettings.getAkkuSettings() != null) {
							akkuSettings = akkumatikSettings.getAkkuSettings().setting;
							String[] programNames = new String[akkumatikSettings.getAkkuSettings().setting.size()];
							for (int i = 0; i < akkuSettings.size(); ++i) {
								programNames[i] = akkuSettings.get(i).getName();
							}
							programNameSelection.setItems(programNames);
							programNameSelection.select(getActiveChannelProgram());
						}
					}
				}
			});
			GridData gd_programSelection = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
			gd_programSelection.widthHint = 210;
			gd_programSelection.heightHint = GDE.IS_WINDOWS ? SWT.DEFAULT : comboHeight;
			programNameSelection.setLayoutData(gd_programSelection);
			programNameSelection.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			AkkumatikDialog.log.log(Level.OFF, String.format("this.akkumatikSettings.getAkkuSettings() != null -> %b", this.akkumatikSettings.getAkkuSettings() != null));
			if (this.akkumatikSettings.getAkkuSettings() != null) {
				akkuSettings = this.akkumatikSettings.getAkkuSettings().setting;
				String[] programNames = new String[this.akkumatikSettings.getAkkuSettings().setting.size()];
				for (int i = 0; i < akkuSettings.size(); ++i) {
					programNames[i] = akkuSettings.get(i).getName();
					AkkumatikDialog.log.log(Level.INFO, String.format("add prrogram name -> %s", akkuSettings.get(i).getName()));
				}
				programNameSelection.setItems(programNames);
				programNameSelection.select(getActiveChannelProgram());
			}
			actualAkkuSetting = akkuSettings.get(programNameSelection.getSelectionIndex());
			
			Button addEntry = new Button(composite_1, SWT.NONE);
			GridData gdButton = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
			gdButton.widthHint = 45;
			gdButton.heightHint = GDE.IS_WINDOWS ? SWT.DEFAULT : comboHeight;
			addEntry.setLayoutData(gdButton);
			addEntry.setText("+");
			addEntry.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.BOLD));
			addEntry.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					int number = akkumatikSettings.getAkkuSettings().setting.size();
					akkumatikSettings.getAkkuSettings().getSetting().add(createAkkuSetting("Initial_" + number, 1, 0, 2, 0, 1000, 34, 2, 0, 0, 0, 100, 100));
					akkuSettings = akkumatikSettings.getAkkuSettings().setting;
					String[] programNames = new String[akkumatikSettings.getAkkuSettings().setting.size()];
					for (int i = 0; i < akkuSettings.size(); ++i) {
						programNames[i] = akkuSettings.get(i).getName();
					}
					programNameSelection.setItems(programNames);
					akkumatikSettings.getDialogSettings().getChannel1().setActiveSetting(programNames.length - 1);
					programNameSelection.select(programNames.length - 1);
					actualAkkuSetting = akkuSettings.get(programNames.length - 1);
					removeEntry.setEnabled(akkumatikSettings.getAkkuSettings().setting.size() > 0);
					editEntry.setEnabled(akkumatikSettings.getAkkuSettings().setting.size() > 0);
				}
			});

			removeEntry = new Button(composite_1, SWT.NONE);
			removeEntry.setLayoutData(gdButton);
			removeEntry.setText("-");
			removeEntry.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.BOLD));
			removeEntry.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					programSelectionIndex = programNameSelection.getSelectionIndex();
					if (akkumatikSettings.getAkkuSettings().setting.size() > 0) {
						akkumatikSettings.getAkkuSettings().getSetting().remove(programSelectionIndex);
						String[] programNames = new String[akkumatikSettings.getAkkuSettings().setting.size()];
						for (int i = 0; i < akkuSettings.size(); ++i) {
							programNames[i] = akkuSettings.get(i).getName();
						}
						programNameSelection.setItems(programNames);
						akkumatikSettings.getDialogSettings().getChannel1().setActiveSetting(programNames.length - 1);
						//programNameSelection.select(programNames.length-1);
						if (programSelectionIndex > 0)
							programNameSelection.select(programSelectionIndex - 1);
						else
							programNameSelection.select(0);
					}
					actualAkkuSetting = akkuSettings.get(programNameSelection.getSelectionIndex());
					removeEntry.setEnabled(akkumatikSettings.getAkkuSettings().setting.size() > 0);
					editEntry.setEnabled(akkumatikSettings.getAkkuSettings().setting.size() > 0);
				}
			});
			removeEntry.setEnabled(akkumatikSettings.getAkkuSettings().setting.size() > 0);

			editEntry = new Button(composite_1, SWT.NONE);
			editEntry.setLayoutData(gdButton);
			editEntry.setText(Messages.getString(MessageIds.GDE_MSGT3450));
			editEntry.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			editEntry.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					programSelectionIndex = programNameSelection.getSelectionIndex();
					programNameSelection.setEditable(true);
				}
			});
			editEntry.setEnabled(akkumatikSettings.getAkkuSettings().setting.size() > 0);

			Group grpBattery = new Group(composite, SWT.NONE);
			grpBattery.setLayout(new RowLayout(SWT.HORIZONTAL));
			grpBattery.setLayoutData(new RowData(170, GDE.IS_LINUX ? 135 : 120));
			grpBattery.setText(Messages.getString(MessageIds.GDE_MSGT3451));
			grpBattery.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			
			Composite filler = new Composite(grpBattery, SWT.NONE);
			filler.setLayoutData(new RowData(100, 1));

			Label lblBatteryType = new Label(grpBattery, SWT.NONE);
			lblBatteryType.setLayoutData(new RowData(90, comboHeight));
			lblBatteryType.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblBatteryType.setText(Messages.getString(MessageIds.GDE_MSGT3452));

			batteryTypeCombo = new CCombo(grpBattery, SWT.BORDER);
			batteryTypeCombo.setEditable(false);
			batteryTypeCombo.setBackground(this.application.COLOR_WHITE);
			batteryTypeCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			batteryTypeCombo.setItems(Akkumatik.ACCU_TYPES);
			batteryTypeCombo.select(akkuSettings.get(getActiveChannelProgram()).getAccuTyp());
			batteryTypeCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					//update combo entries according selected battery type and output channel
					update(batteryTypeCombo.getText(), btnChannel1.getSelection() ? 1 : 2);
					actualAkkuSetting.setAccuTyp(batteryTypeCombo.getSelectionIndex());
					currentModeCombo.select(0); //NiXX -> auto, Pb, Li -> Fix
				}
			});
			batteryTypeCombo.setLayoutData(new RowData(70, comboHeight));
			
			filler = new Composite(grpBattery, SWT.NONE);
			filler.setLayoutData(new RowData(100, GDE.IS_MAC ? 6 : 4));

			Label lblCapacity = new Label(grpBattery, SWT.NONE);
			lblCapacity.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblCapacity.setLayoutData(new RowData(90, comboHeight));
			lblCapacity.setText(Messages.getString(MessageIds.GDE_MSGT3453));

			capacityCombo = new CCombo(grpBattery, SWT.BORDER);
			capacityCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			ArrayList<String> capa = new ArrayList<>();
			for (int i = 1; i < 101; ++i)
				capa.add("" + (100 * i));
			capacityCombo.setItems(capa.toArray(new String[0]));
			capacityCombo.select(akkuSettings.get(getActiveChannelProgram()).getCapacity()/100 - 1);
			capacityCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setCapacity(Integer.parseInt(capacityCombo.getText()));
				}
			});
			capacityCombo.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent evt) {
					if (evt.character == SWT.CR) {
						int value = Integer.parseInt(capacityCombo.getText());
						if (value > 65525) {
							value = 65525;
							capacityCombo.setText(""+value);
						}
						actualAkkuSetting.setCapacity(value);
					}
				}
			});
			capacityCombo.addVerifyListener(new VerifyListener() {
				@Override
				public void verifyText(VerifyEvent evt) {
					evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
				}
			});
			capacityCombo.setLayoutData(new RowData(70, comboHeight));

			filler = new Composite(grpBattery, SWT.NONE);
			filler.setLayoutData(new RowData(100, GDE.IS_MAC ? 6 : 4));
			
			Label lblNumberCells = new Label(grpBattery, SWT.NONE);
			lblNumberCells.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblNumberCells.setLayoutData(new RowData(90, comboHeight));
			lblNumberCells.setText(Messages.getString(MessageIds.GDE_MSGT3454));

			cellCountCombo = new CCombo(grpBattery, SWT.BORDER);
			batteryTypeCombo.setEditable(false);
			batteryTypeCombo.setBackground(this.application.COLOR_WHITE);
			cellCountCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			ArrayList<String> cells = new ArrayList<>();
			for (int i = 1; i < 35; ++i)
				cells.add("" + i);
			cellCountCombo.setItems(cells.toArray(new String[0]));
			cellCountCombo.select(akkuSettings.get(getActiveChannelProgram()).getCellCount()-1);
			cellCountCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setCellCount(cellCountCombo.getSelectionIndex()+1);
				}
			});
			cellCountCombo.setLayoutData(new RowData(70, comboHeight));

			Composite composite_2 = new Composite(composite, SWT.NONE);
			composite_2.setLayout(new RowLayout(SWT.HORIZONTAL));
			composite_2.setLayoutData(new RowData(178, GDE.IS_MAC ? 150 : 135));

			Group grpChannel = new Group(composite_2, SWT.NONE);
			grpChannel.setLayoutData(new RowData(165, GDE.IS_LINUX ? 30 : GDE.IS_MAC ? 25 : 20));
			grpChannel.setLayout(new RowLayout(SWT.HORIZONTAL));
			grpChannel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			grpChannel.setText(Messages.getString(MessageIds.GDE_MSGT3455));

			btnChannel1 = new Button(grpChannel, SWT.RADIO);
			btnChannel1.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			btnChannel1.setSelection(false);
			btnChannel1.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					update(batteryTypeCombo.getText(), btnChannel1.getSelection() ? 1 : 2);
					actualAkkuSetting.setChannel(btnChannel1.getSelection() ? 1 : 2);
				}
			});
			btnChannel1.setLayoutData(new RowData(75, comboHeight));
			btnChannel1.setText(Messages.getString(MessageIds.GDE_MSGT3455) + " 1");

			btnChannel2 = new Button(grpChannel, SWT.RADIO);
			btnChannel2.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			btnChannel2.setSelection(false);
			btnChannel2.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					update(batteryTypeCombo.getText(), btnChannel1.getSelection() ? 1 : 2);
					actualAkkuSetting.setChannel(btnChannel1.getSelection() ? 1 : 2);
				}
			});
			btnChannel2.setLayoutData(new RowData(75, comboHeight));
			btnChannel2.setText(Messages.getString(MessageIds.GDE_MSGT3455) + " 2");

			
			Group grpProgramm = new Group(composite_2, SWT.NONE);
			grpProgramm.setLayoutData(new RowData(165, GDE.IS_LINUX ? 68 : 58));
			grpProgramm.setLayout(new RowLayout(SWT.HORIZONTAL));
			grpProgramm.setText(Messages.getString(MessageIds.GDE_MSGT3456));
			grpProgramm.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));

			Label lblName = new Label(grpProgramm, SWT.NONE);
			lblName.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblName.setLayoutData(new RowData(60, comboHeight));
			lblName.setText("Name");

			programCombo = new CCombo(grpProgramm, SWT.BORDER);
			programCombo.setEditable(false);
			programCombo.setBackground(this.application.COLOR_WHITE);
			programCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			programCombo.setItems(Akkumatik.PROCESS_MODE);
			programCombo.select(akkuSettings.get(getActiveChannelProgram()).getProgram());
			programCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (programCombo.getSelectionIndex() > 1 && programCombo.getSelectionIndex() < 6)
						cycleCountCombo.setEnabled(true);
					else {
						cycleCountCombo.select(0);
						cycleCountCombo.setEnabled(false);
					}
					actualAkkuSetting.setProgram(findIndexByName(Akkumatik.PROCESS_MODE, programCombo.getText())); 
					updateChargeDischarge();
				}
			});
			programCombo.setLayoutData(new RowData(90, comboHeight));

			filler = new Composite(grpProgramm, SWT.NONE);
			filler.setLayoutData(new RowData(120, 2));

			Label lblCycleCount = new Label(grpProgramm, SWT.NONE);
			lblCycleCount.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblCycleCount.setLayoutData(new RowData(60, comboHeight));
			lblCycleCount.setText(Messages.getString(MessageIds.GDE_MSGT3457));

			cycleCountCombo = new CCombo(grpProgramm, SWT.BORDER);
			cycleCountCombo.setEditable(false);
			cycleCountCombo.setBackground(this.application.COLOR_WHITE);
			ArrayList<String> counts = new ArrayList<>();
			for (int i = 0; i < 10; ++i)
				counts.add("" + i);
			cycleCountCombo.setItems(counts.toArray(new String[0]));
			cycleCountCombo.select(akkuSettings.get(getActiveChannelProgram()).getCycle());
			cycleCountCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			cycleCountCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setCycle(cycleCountCombo.getSelectionIndex()); 
				}
			});
			cycleCountCombo.setLayoutData(new RowData(90, comboHeight));

			grpCharge = new Group(composite, SWT.NONE);
			grpCharge.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			grpCharge.setLayout(new RowLayout(SWT.HORIZONTAL));
			grpCharge.setLayoutData(new RowData(170, GDE.IS_LINUX ? 100 : 90));
			grpCharge.setText(Messages.getString(MessageIds.GDE_MSGT3400));

			Label lblChargeType = new Label(grpCharge, SWT.NONE);
			lblChargeType.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblChargeType.setLayoutData(new RowData(80, comboHeight));
			lblChargeType.setText(Messages.getString(MessageIds.GDE_MSGT3458));

			chargeModeCombo = new CCombo(grpCharge, SWT.BORDER);
			chargeModeCombo.setEditable(false);
			chargeModeCombo.setBackground(this.application.COLOR_WHITE);
			chargeModeCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE);
			chargeModeCombo.select(akkuSettings.get(getActiveChannelProgram()).getChargeMode());
			chargeModeCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setProgram(findIndexByName(Akkumatik.CHARGE_MODE, chargeModeCombo.getText()));
				}
			});
			chargeModeCombo.setLayoutData(new RowData(80, comboHeight));

			filler = new Composite(grpCharge, SWT.NONE);
			filler.setLayoutData(new RowData(120, GDE.IS_MAC ? 4 : 2));

			Label lblChargeCurrent = new Label(grpCharge, SWT.NONE);
			lblChargeCurrent.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblChargeCurrent.setLayoutData(new RowData(80, comboHeight));
			lblChargeCurrent.setText(Messages.getString(MessageIds.GDE_MSGT3459));

			chargeCurrentCombo = new CCombo(grpCharge, SWT.BORDER);
			chargeCurrentCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			ArrayList<String> chargeCurrentList = new ArrayList<>();
			for (int i = 1; i < 201; ++i)
				chargeCurrentList.add("" + (50 * i));
			chargeCurrentCombo.setItems(chargeCurrentList.toArray(new String[0]));
			chargeCurrentCombo.select(akkuSettings.get(getActiveChannelProgram()).getChargeCurrent()/50 - 1);
			chargeCurrentCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setChargeCurrent(Integer.parseInt(chargeCurrentCombo.getText())); 
				}
			});
			chargeCurrentCombo.setLayoutData(new RowData(80, comboHeight));
			chargeCurrentCombo.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent evt) {
					if (evt.character == SWT.CR) {
						int value = Integer.parseInt(capacityCombo.getText());
						if (value > 9999) {
							value = 9999;
							chargeCurrentCombo.setText(""+ value);
						}
						value = value > 9999 ? 9999 : value;
						actualAkkuSetting.setChargeCurrent(value); 
					}
				}
			});
			chargeCurrentCombo.addVerifyListener(new VerifyListener() {
				@Override
				public void verifyText(VerifyEvent evt) {
					evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
				}
			});

			filler = new Composite(grpCharge, SWT.NONE);
			filler.setLayoutData(new RowData(120, GDE.IS_MAC ? 4 : 2));

			Label lblStopMode = new Label(grpCharge, SWT.NONE);
			lblStopMode.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblStopMode.setLayoutData(new RowData(80, comboHeight));
			lblStopMode.setText("Stop Mode");

			chargeStopModeCombo = new CCombo(grpCharge, SWT.BORDER);
			chargeStopModeCombo.setEditable(false);
			chargeStopModeCombo.setBackground(this.application.COLOR_WHITE);
			chargeStopModeCombo.setItems(AkkumatikDialog.STOP_MODE);
			chargeStopModeCombo.select(akkuSettings.get(getActiveChannelProgram()).getChargeStopMode());
			chargeStopModeCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			chargeStopModeCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setChargeStopMode(findIndexByName(AkkumatikDialog.STOP_MODE, chargeStopModeCombo.getText())); 
				}
			});
			chargeStopModeCombo.setLayoutData(new RowData(80, comboHeight));

			composite_3 = new Composite(composite, SWT.NONE);
			composite_3.setLayout(new RowLayout(SWT.HORIZONTAL));
			composite_3.setLayoutData(new RowData(170, GDE.IS_LINUX ? 95 : GDE.IS_WINDOWS ? 85 : 75));

			Composite composite_4 = new Composite(composite_3, SWT.NONE);
			composite_4.setLayoutData(new RowData(150, 20));

			Label lblCurrentType = new Label(composite_3, SWT.NONE);
			lblCurrentType.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblCurrentType.setLayoutData(new RowData(75, comboHeight));
			lblCurrentType.setText(Messages.getString(MessageIds.GDE_MSGT3460));

			currentModeCombo = new CCombo(composite_3, SWT.BORDER);
			currentModeCombo.setEditable(false);
			currentModeCombo.setBackground(this.application.COLOR_WHITE);
			currentModeCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE);
			currentModeCombo.select(akkuSettings.get(getActiveChannelProgram()).getCurrentMode());
			currentModeCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setCurrentMode(findIndexByName(CHARGE_CURRENT_TYPE, currentModeCombo.getText())); 
				}
			});
			currentModeCombo.setLayoutData(new RowData(70, comboHeight));

			filler = new Composite(composite_3, SWT.NONE);
			filler.setLayoutData(new RowData(120, GDE.IS_MAC ? 4 : 2));

			Label lblCapacityAmount = new Label(composite_3, SWT.NONE);
			lblCapacityAmount.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblCapacityAmount.setLayoutData(new RowData(75, comboHeight));
			lblCapacityAmount.setText(Messages.getString(MessageIds.GDE_MSGT3461));

			chargeAmountCombo = new CCombo(composite_3, SWT.BORDER);
			chargeAmountCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			ArrayList<String> capaAmount = new ArrayList<>();
			for (int i = 1; i < 100; ++i)
				capaAmount.add("" + (100 * i));
			capaAmount.add(0, "0");
			chargeAmountCombo.setItems(capaAmount.toArray(new String[0]));
			chargeAmountCombo.select(akkuSettings.get(getActiveChannelProgram()).getAmount()/100);
			chargeAmountCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setAmount(Integer.parseInt(chargeAmountCombo.getText())); 
				}
			});
			chargeAmountCombo.setLayoutData(new RowData(70, comboHeight));
			chargeAmountCombo.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent evt) {
					if (evt.character == SWT.CR) {
						int value = Integer.parseInt(chargeAmountCombo.getText());
						if (value > 9999) {
							value = 9999;
							chargeAmountCombo.setText(""+ value);
						}
						actualAkkuSetting.setAmount(value); 
					}
				}
			});
			chargeAmountCombo.addVerifyListener(new VerifyListener() {
				@Override
				public void verifyText(VerifyEvent evt) {
					evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
				}
			});

			grpDischarge = new Group(composite, SWT.NONE);
			grpDischarge.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			grpDischarge.setLayout(new RowLayout(SWT.HORIZONTAL));
			grpDischarge.setLayoutData(new RowData(170, GDE.IS_LINUX ? 40 : GDE.IS_WINDOWS ? 35 : 25));
			grpDischarge.setText(Messages.getString(MessageIds.GDE_MSGT3401));

			Label lblDischargeCurrent = new Label(grpDischarge, SWT.NONE);
			lblDischargeCurrent.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			lblDischargeCurrent.setLayoutData(new RowData(80, comboHeight));
			lblDischargeCurrent.setText(Messages.getString(MessageIds.GDE_MSGT3459));

			dischargeCurrentCombo = new CCombo(grpDischarge, SWT.BORDER);
			dischargeCurrentCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			ArrayList<String> dischargeCurrentList = new ArrayList<>();
			for (int i = 1; i < 101; ++i)
				dischargeCurrentList.add("" + (50 * i));
			dischargeCurrentCombo.setItems(dischargeCurrentList.toArray(new String[0]));
			dischargeCurrentCombo.select(akkuSettings.get(getActiveChannelProgram()).getDisChargeCurrent()/50 - 1);
			dischargeCurrentCombo.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					actualAkkuSetting.setDisChargeCurrent(Integer.parseInt(dischargeCurrentCombo.getText())); 
				}
			});
			dischargeCurrentCombo.setLayoutData(new RowData(80, comboHeight));
			dischargeCurrentCombo.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent evt) {
					if (evt.character == SWT.CR) {
						int value = Integer.parseInt(dischargeCurrentCombo.getText());
						if (value > 5000) {
							value = 5000;
							dischargeCurrentCombo.setText(""+value);
						}
						actualAkkuSetting.setDisChargeCurrent(value); 
					}
				}
			});
			dischargeCurrentCombo.addVerifyListener(new VerifyListener() {
				@Override
				public void verifyText(VerifyEvent evt) {
					evt.doit = StringHelper.verifyTypedInput(DataTypes.INTEGER, evt.text);
				}
			});

			statusLabel = new Label(dialogShell, SWT.NONE);
			statusLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			FormData fd_statusLabel = new FormData();
			fd_statusLabel.left = new FormAttachment(composite, 10, SWT.LEFT);
			fd_statusLabel.bottom = new FormAttachment(composite, 20, SWT.BOTTOM);
			fd_statusLabel.top = new FormAttachment(composite, 2);
			fd_statusLabel.right = new FormAttachment(composite, -10, SWT.RIGHT);
			statusLabel.setLayoutData(fd_statusLabel);
			statusLabel.setText(Messages.getString(MessageIds.GDE_MSGI3402));

			Composite composite_5 = new Composite(dialogShell, SWT.NONE);
			composite_5.setLayout(new RowLayout(SWT.HORIZONTAL));
			FormData fd_composite_5 = new FormData();
			fd_composite_5.top = new FormAttachment(statusLabel, 2);
			fd_composite_5.bottom = new FormAttachment(0, 450);
			fd_composite_5.left = new FormAttachment(0, 10);
			fd_composite_5.right = new FormAttachment(0, 375);
			composite_5.setLayoutData(fd_composite_5);

			btnTransfer = new Button(composite_5, SWT.NONE);
			btnTransfer.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			btnTransfer.setEnabled(true);
			btnTransfer.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					statusLabel.setText(new String(actualAkkuSetting.toString()));
					log.log(Level.OFF, actualAkkuSetting.toString());
					log.log(Level.OFF, StringHelper.byte2Hex2CharString(actualAkkuSetting.getBytes2Write(), actualAkkuSetting.getBytes2Write().length));
					btnTransfer.setEnabled(false);
					btnStart.setEnabled(true);
					btnStop.setEnabled(false);
				}
			});
			btnTransfer.setLayoutData(new RowData(GDE.IS_MAC ? 118 : 115, GDE.IS_WINDOWS ? SWT.DEFAULT : comboHeight));
			btnTransfer.setText(Messages.getString(MessageIds.GDE_MSGT3462));

			btnStart = new Button(composite_5, SWT.NONE);
			btnStart.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			btnStart.setEnabled(false);
			btnStart.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
//					byte[] program2Write = actualAkkuSetting.getBytes2Write();
//					byte[] writeBuffer = new byte[START_COM.length + program2Write.length];
//					System.arraycopy(program2Write, 0, writeBuffer, 0, program2Write.length);
//					System.arraycopy(START_COM, 0, writeBuffer, program2Write.length, START_COM.length);
					if (serialPort != null && serialPort.isConnected())
						setData2Write(START_COM);
					AkkumatikDialog.log.log(Level.OFF, StringHelper.byte2Hex2CharString(START_COM, START_COM.length));
					btnTransfer.setEnabled(false);
					btnStart.setEnabled(false);
					btnStop.setEnabled(true);
				}
			});
			btnStart.setLayoutData(new RowData(GDE.IS_MAC ? 118 : 115, GDE.IS_WINDOWS ? SWT.DEFAULT : comboHeight));
			btnStart.setText("Start");

			btnStop = new Button(composite_5, SWT.NONE);
			btnStop.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			btnStop.setEnabled(false);
			btnStop.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					if (serialPort != null && serialPort.isConnected())
						setData2Write(STOP_COM);
					AkkumatikDialog.log.log(Level.OFF, StringHelper.byte2Hex2CharString(STOP_COM, STOP_COM.length));
					btnTransfer.setEnabled(true);
					btnStart.setEnabled(false);
					btnStop.setEnabled(false);
				}
			});
			btnStop.setLayoutData(new RowData(GDE.IS_MAC ? 118 : 115, GDE.IS_WINDOWS ? SWT.DEFAULT : comboHeight));
			btnStop.setText("Stop");

			Button btnClose = new Button(composite_5, SWT.NONE);
			btnClose.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE-1, SWT.NORMAL));
			btnClose.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					AkkumatikDialog.this.dialogShell.dispose();
				}
			});
			btnClose.setLayoutData(new RowData(GDE.IS_LINUX ? 355 : GDE.IS_WINDOWS ? 353 : 363, GDE.IS_WINDOWS ? SWT.DEFAULT : comboHeight));
			btnClose.setText(Messages.getString(MessageIds.GDE_MSGT3463));
			
			//update combo entries according selected battery type and output channel
			update(Akkumatik.ACCU_TYPES[akkuSettings.get(programNameSelection.getSelectionIndex()).getAccuTyp()], akkuSettings.get(getActiveChannelProgram()).getChannel());
			updateChargeDischarge();
		}
	}

	/**
	 * update combo entries according selected battery type and output channel
	 * @param batteryTypeIndex 0=NiCd, 1=NiMH, 2=Pb, 3=PbGel, 4=Li36, 5=Li37, 6=LiFe, 7=IuXX
	 */
	private void update(String batteryType, int channelNumber) {
		
		capacityCombo.select(actualAkkuSetting.getCapacity()/100 - 1);
		capacityCombo.setText(""+actualAkkuSetting.getCapacity());
		cycleCountCombo.select(actualAkkuSetting.getCycle());
		chargeCurrentCombo.select(actualAkkuSetting.getChargeCurrent()/50 - 1);
		chargeCurrentCombo.setText(""+actualAkkuSetting.getChargeCurrent());
		chargeAmountCombo.select(actualAkkuSetting.getAmount()/100);	
		chargeAmountCombo.setText(""+actualAkkuSetting.getAmount());
		dischargeCurrentCombo.select(actualAkkuSetting.getDisChargeCurrent()/50 - 1);
		dischargeCurrentCombo.setText(""+actualAkkuSetting.getDisChargeCurrent());
		
		switch (batteryType) {
		default:
		case "NiCd":
			batteryTypeCombo.select(0);
			programCombo.setItems(Akkumatik.PROCESS_MODE_NI);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_NI, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_NI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_NI, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_NI);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_NI, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_NI);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_NI, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "NiMH":
			batteryTypeCombo.select(1);
			programCombo.setItems(Akkumatik.PROCESS_MODE_NI);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_NI, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_NI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_NI, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_NI);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_NI, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_NI);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_NI, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "Blei":
		case "Pb":
			batteryTypeCombo.select(2);
			programCombo.setItems(Akkumatik.PROCESS_MODE_PB);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_PB, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_PB);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_PB, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_PB);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_PB, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_PB);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_PB, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "BGel":
		case "PbGel":
			batteryTypeCombo.select(3);
			programCombo.setItems(Akkumatik.PROCESS_MODE_PB);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_PB, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_LI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_PB, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_PB);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_PB, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_PB);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_PB, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "Li36":
			batteryTypeCombo.select(4);
			programCombo.setItems(Akkumatik.PROCESS_MODE_LI);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_LI, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_LI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_LI, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_LI);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_LI, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_PB);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_LI, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "Li37":
			batteryTypeCombo.select(5);
			programCombo.setItems(Akkumatik.PROCESS_MODE_LI);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_LI, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_LI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_LI, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_LI);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_LI, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_PB);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_LI, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "LiFe":
			batteryTypeCombo.select(6);
			programCombo.setItems(Akkumatik.PROCESS_MODE_LI);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_LI, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_LI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_LI, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_LI);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_LI, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_PB);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_LI, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;
		case "IUxx":
			batteryTypeCombo.select(7);
			programCombo.setItems(Akkumatik.PROCESS_MODE_LI);
			programCombo.select(findIndexByName(Akkumatik.PROCESS_MODE_LI, Akkumatik.PROCESS_MODE[actualAkkuSetting.getProgram()]));
			chargeModeCombo.setItems(Akkumatik.CHARGE_MODE_LI);
			chargeModeCombo.select(findIndexByName(Akkumatik.CHARGE_MODE_LI, Akkumatik.CHARGE_MODE[actualAkkuSetting.getChargeMode()]));
			currentModeCombo.setItems(CHARGE_CURRENT_TYPE_LI);
			currentModeCombo.select(findIndexByName(CHARGE_CURRENT_TYPE_LI, CHARGE_CURRENT_TYPE[actualAkkuSetting.getCurrentMode()]));
			chargeStopModeCombo.setItems(STOP_MODE_PB);
			chargeStopModeCombo.select(findIndexByName(STOP_MODE_LI, STOP_MODE[actualAkkuSetting.getChargeStopMode()]));
			break;			
		}

		ArrayList<String> cells = new ArrayList<>();
		
		switch (channelNumber) {
		default:
		case 1:
			btnChannel1.setSelection(true);
			btnChannel2.setSelection(false);
			grpDischarge.setEnabled(true);
			grpDischarge.setForeground(this.application.COLOR_BLACK);
			//		Zellenzahl bei NiCd, NiMh         1...34
			//		Zellenzahl bei Blei, Blei-Gel      1...20
			//		Zellenzahl bei Li-Ionen, Li-Po   1...12
			//		Zellenzahl bei LiFePO4 (A123) 1…14
			switch (batteryType) {
			default:
			case "NiCd":
			case "NiMH":
				for (int i = 1; i < 35; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			case "Pb":
			case "PbGel":
				for (int i = 1; i < 21; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			case "Li36":
			case "Li37":
				for (int i = 1; i < 13; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			case "LiFe":
				batteryTypeCombo.select(6);
				for (int i = 1; i < 15; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			case "IUxx":
				for (int i = 1; i < 13; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			}
			break;

		case 2:
			btnChannel1.setSelection(false);
			btnChannel2.setSelection(true);
			grpDischarge.setEnabled(false);
			grpDischarge.setForeground(this.application.COLOR_GREY);
			//			Zellenzahl bei NiCd, NiMh 1...8 abhängig von der Versorgungsspannung
			//			Zellenzahl bei Blei, Blei-Gel 1...4 abhängig von der Versorgungsspannung
			//			Zellenzahl bei Li-Ionen, Li-Polymer 1…3 abhängig von der Versorgungsspannung
			//			Zellenzahl bei LiFePO4 (A123) 1…3 abhängig von der Versorgungsspannung
			switch (batteryType) {
			default:
			case "NiCd":
			case "NiMH":
				for (int i = 1; i < 9; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			case "Pb":
			case "PbGel":
				for (int i = 1; i < 5; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			case "Li36":
			case "Li37":
			case "LiFe":
			case "IUxx":
				for (int i = 1; i < 4; ++i)
					cells.add("" + i);
				cellCountCombo.setItems(cells.toArray(new String[0]));
				break;
			}
			break;
		}

		int cellsSelection = actualAkkuSetting.getCellCount()-1;

		if (cellsSelection > cellCountCombo.getItemCount() - 1)
			cellCountCombo.select(cellCountCombo.getItemCount() - 1);
		else
			cellCountCombo.select(cellsSelection);
		
		updateChargeDischarge();
		
		if (this.serialPort != null && this.serialPort.isConnected()) {
			//btnTransfer.setEnabled(true);
			btnStart.setEnabled(true);
			btnStop.setEnabled(false);
		}
		else {
			//btnTransfer.setEnabled(false);
			btnStart.setEnabled(false);
			btnStop.setEnabled(false);
		}
	}
	
	/**
	 * @param nameArray string array to search for match
	 * @param searchString string to find match
	 * @return index of matched process type search string or -1, if no match
	 */
	private int findIndexByName(String[] nameArray, String searchString) {
		boolean isMatchFound = false;
		int index = 0;
		for (; index < nameArray.length; ++ index)
			if (nameArray[index].equals(searchString)) {
				isMatchFound = true;
				break;
			}
		return isMatchFound ? index : -1;
	}
	
	/**
	 * @return index of active battery program
	 */
	private int getActiveChannelProgram() {
		if (this.akkumatikSettings != null && this.akkumatikSettings.getDialogSettings() != null && this.akkumatikSettings.getDialogSettings().getActiveChannel() != null) {
			int activeSettingChannel = this.akkumatikSettings.getDialogSettings().getActiveChannel();
			int activeChannelProgram = activeSettingChannel == 1 ? this.akkumatikSettings.getDialogSettings().getChannel1().getActiveSetting()
					: this.akkumatikSettings.getDialogSettings().getChannel2().getActiveSetting();
			return activeChannelProgram;
		}
		return 0;
	}
	
	/**
	 * Programm (0= LADE, 1= ENTL, 2= E+L, 3= L+E, 4= (L)E+L, 5= (E)L+E, 6= SENDER, LAGERN wird mit 0 oder 1 gemeldet)
	 */
	private void updateChargeDischarge() {
		switch (programCombo.getSelectionIndex()) {
		case 0: //charge
			grpCharge.setEnabled(true);
			for (Control child : grpCharge.getChildren()) {
				child.setEnabled(true);
				child.setForeground(this.application.COLOR_BLACK);
			}
			composite_3.setEnabled(true);
			for (Control child : composite_3.getChildren()) {
				child.setEnabled(true);
				child.setForeground(this.application.COLOR_BLACK);
			}
			grpDischarge.setEnabled(false);
			grpDischarge.setForeground(this.application.COLOR_GREY);
			for (Control child : grpDischarge.getChildren()) {
				child.setEnabled(false);
				child.setForeground(this.application.COLOR_GREY);
			}
			break;
		case 1: //discharge
			grpCharge.setEnabled(false);
			for (Control child : grpCharge.getChildren()) {
				child.setEnabled(false);
				child.setForeground(this.application.COLOR_GREY);
			}
			composite_3.setEnabled(false);
			for (Control child : composite_3.getChildren()) {
				child.setEnabled(false);
				child.setForeground(this.application.COLOR_GREY);
			}
			grpDischarge.setEnabled(true);
			grpDischarge.setForeground(this.application.COLOR_BLACK);
			for (Control child : grpDischarge.getChildren()) {
				child.setEnabled(true);
				child.setForeground(this.application.COLOR_BLACK);
			}
			break;
		default:
			grpCharge.setEnabled(true);
			for (Control child : grpCharge.getChildren()) {
				child.setEnabled(true);
				child.setForeground(this.application.COLOR_BLACK);
			}
			composite_3.setEnabled(true);
			for (Control child : composite_3.getChildren()) {
				child.setEnabled(true);
				child.setForeground(this.application.COLOR_BLACK);
			}
			grpDischarge.setEnabled(true);
			grpDischarge.setForeground(this.application.COLOR_BLACK);
			for (Control child : grpDischarge.getChildren()) {
				child.setEnabled(true);
				child.setForeground(this.application.COLOR_BLACK);
			}
			break;
		}
	}

	/**
	 * method to test this class
	 * @param args
	 */
	public static void main(String[] args) {
		initLogger();
		Logger.getLogger(GDE.STRING_EMPTY).setLevel(Level.TIME);

		Settings.getInstance();
		String basePath = Settings.getApplHomePath();

		try {
			Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
					.newSchema(new StreamSource(AkkumatikDialog.class.getClassLoader().getResourceAsStream("resource/" + AkkumatikDialog.AKKUMATIK_SETTINGS_XSD))); //$NON-NLS-1$
			JAXBContext jc = JAXBContext.newInstance("gde.device.estner"); //$NON-NLS-1$

			AMSettings akkumatikSettings = new ObjectFactory().createAMSettings();
			AkkumatikDialog.log.log(Level.TIME, "XSD init time = " + StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - GDE.StartTime))); //$NON-NLS-1$ //$NON-NLS-2$
	
			try {
				long time = new Date().getTime();

				// read existing settings XML
				Unmarshaller unmarshaller = jc.createUnmarshaller();
				unmarshaller.setSchema(schema);
				akkumatikSettings = (AMSettings) unmarshaller.unmarshal(new File(basePath + AkkumatikDialog.AKKUMATIK_CONFIGURATION_SUFFIX
					 + GDE.FILE_ENDING_DOT_XML));
				AkkumatikDialog.log.log(Level.TIME, "read setup XML time = " + StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - time))); //$NON-NLS-1$ //$NON-NLS-2$
			
				if (akkumatikSettings.getDialogSettings() != null) {
					AkkumatikDialog.log.log(Level.OFF, "active channel = " + akkumatikSettings.getDialogSettings().getActiveChannel());
					AkkumatikDialog.log.log(Level.OFF, "active setting channel 1, aktive entry = " + akkumatikSettings.getDialogSettings().getChannel1().getActiveSetting());
					AkkumatikDialog.log.log(Level.OFF, "active setting channel 2, aktive entry = " + akkumatikSettings.getDialogSettings().getChannel2().getActiveSetting());
				}
			
				if (akkumatikSettings.getAkkuSettings() != null) {
					List<Setting> akkuSettings = akkumatikSettings.getAkkuSettings().setting;
					for (Setting setting : akkuSettings)
						AkkumatikDialog.log.log(Level.OFF, "Akku Setting Name = " + setting.getName());
						
				}
								
				time = new Date().getTime();
				// store back manipulated XML
				Marshaller marshaller = jc.createMarshaller();
				marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.valueOf(true));
				marshaller.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, AkkumatikDialog.AKKUMATIK_SETTINGS_XSD);
				marshaller.marshal(akkumatikSettings, new FileOutputStream(basePath + AkkumatikDialog.AKKUMATIK_CONFIGURATION_SUFFIX + "_1" + GDE.FILE_ENDING_DOT_XML));
				AkkumatikDialog.log.log(Level.TIME, "write setup XML time = " + StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - GDE.StartTime))); //$NON-NLS-1$ //$NON-NLS-2$
	
			}
			catch (Exception e) {
				AkkumatikDialog.log.log(Level.SEVERE, e.getMessage(), e);
			}

		}
		catch (Exception e) {
			AkkumatikDialog.log.log(Level.SEVERE, e.getMessage(), e);
			return;
		}
	}
	
	private static void initLogger() {
		AkkumatikDialog.logHandler = new ConsoleHandler();
		AkkumatikDialog.logHandler.setFormatter(new LogFormatter());
		AkkumatikDialog.logHandler.setLevel(Level.INFO);
		AkkumatikDialog.rootLogger = Logger.getLogger(GDE.STRING_EMPTY);
		// clean up all handlers from outside
		Handler[] handlers = AkkumatikDialog.rootLogger.getHandlers();
		for (Handler handler : handlers) {
			AkkumatikDialog.rootLogger.removeHandler(handler);
		}
		AkkumatikDialog.rootLogger.setLevel(Level.ALL);
		AkkumatikDialog.rootLogger.addHandler(AkkumatikDialog.logHandler);
	}

	public static void setData2Write(byte[] newData2Write) {
		synchronized(data2Write) {
			data2Write = new byte[newData2Write.length];
			System.arraycopy(newData2Write, 0, data2Write, 0, data2Write.length);
			isDataAvailable = true;
		}
	}
	
	public static byte[] getData2Write() {
		synchronized(data2Write) {
			byte[] retData2Write = null;
			if (isDataAvailable) {
				retData2Write = new byte[data2Write.length];
				System.arraycopy(data2Write, 0, retData2Write, 0, retData2Write.length);
				isDataAvailable = false;
			}
			return retData2Write;
		}
	}
}
