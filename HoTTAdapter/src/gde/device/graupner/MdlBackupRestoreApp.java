package gde.device.graupner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Vector;
import java.util.jar.JarFile;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import gde.Analyzer;
import gde.GDE;
import gde.comm.DeviceCommPort;
import gde.config.Settings;
import gde.device.DataBitsTypes;
import gde.device.DeviceConfiguration;
import gde.device.DevicePropertiesType;
import gde.device.DeviceType;
import gde.device.FlowControlTypes;
import gde.device.IDevice;
import gde.device.ObjectFactory;
import gde.device.ParityTypes;
import gde.device.SerialPortType;
import gde.device.StopBitsTypes;
import gde.device.graupner.hott.MessageIds;
import gde.exception.TimeOutException;
import gde.log.LogFormatter;
import gde.messages.Messages;
import gde.ui.SWTResourceManager;
import gde.utils.FileUtils;
import gde.utils.StringHelper;
import gde.utils.WaitTimer;

public class MdlBackupRestoreApp {
	final static Logger									log											= Logger.getLogger("MdlBackupRestoreApp");
	
	private String selectedPort;
	HoTTAdapterSerialPort								serialPort = null;
	final Settings											settings								= Settings.getInstance();
	private IDevice 										device;
	final HashMap<String, String>				extensionFilterMap								= new HashMap<String, String>();


	protected Shell 										shlMdlBackuprestore;
	protected Display 									display = Display.getDefault();
	
	private CCombo											portSelectCombo;
	private CLabel											portDescription;
	private Thread											listPortsThread;
	private boolean											isUpdateSerialPorts					= true;

	private Vector<String>							availablePorts							= new Vector<String>();

	private final ArrayList<String> 		txMdlList = new ArrayList<>();
	private final ArrayList<String> 		pcMdlList = new ArrayList<>();
	private final ArrayList<String>			selectedMdlList = new ArrayList<>();
	private Transmitter									txType = Transmitter.UNSPECIFIED;
	
	private Button											readMdlButton;
	private Table												pcMdlsTable, txMdlsTable;
	private TableColumn									indexColumn, fileNameColum, fileDateColum;

	private CLabel											mdlStatusInfoLabel;
	private ProgressBar									mdlStatusProgressBar;
	private Button											saveMdlsButton;
	
	private StringBuilder								selectedPcBaseFolder		= new StringBuilder().append(this.settings.getDataFilePath());
	private String											selectedPcFolder				= selectedPcBaseFolder.toString();
	private HashMap<Integer,String>			selectedMdlFile = new HashMap<>();
	private int 												pcMdlsTableSelectionIndex = 1;

	private Group pcMdlsGroup;
	private Group txMdlsGroup;
	private Button fileSelectButton;
	private Button deleteSelectionButton;
	private Button deleteAllButton;
	private Button upButton;
	private Button downButton;
	private Button saveToTxButton;


	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			LogFormatter lf = new LogFormatter();
			Logger rootLogger = Logger.getLogger(GDE.STRING_EMPTY);
			Handler[] handlers = rootLogger.getHandlers();
			for (Handler handler : handlers) {
				rootLogger.removeHandler(handler);
			}
			rootLogger.setLevel(Level.ALL);
			ConsoleHandler logHandler = new ConsoleHandler();
			logHandler.setFormatter(lf);
			logHandler.setLevel(Level.INFO);
			rootLogger.addHandler(logHandler);
			log.setLevel(Level.INFO);

			MdlBackupRestoreApp window = new MdlBackupRestoreApp();
			window.open();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * calculates the new class name for the device
	 */
	protected IDevice getInstanceOfDevice(DeviceConfiguration selectedActiveDeviceConfig) {
		IDevice newInst = null;
		try {
			newInst = selectedActiveDeviceConfig.defineInstanceOfDevice();
		}
		catch (NoClassDefFoundError e) {
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		return newInst;
	}


	/**
	 * Open the window.
	 */
	public void open() {
		
		DeviceConfiguration deviceConfig = Analyzer.getInstance().getDeviceConfigurations().get("HoTTAdapter");
		if (deviceConfig == null) {
			JarFile jarFile;
			try {
				jarFile = new JarFile(String.format("%s%s%s", FileUtils.getDevicePluginJarBasePath(), GDE.STRING_FILE_SEPARATOR_UNIX, "HoTTAdapter.jar"));
				settings.extractDeviceProperties(jarFile, "HoTTAdapter", Settings.getDevicesPath());
				Analyzer.getInstance().getDeviceConfigurations().add(Analyzer.getInstance(), "HoTTAdapter", "HoTTAdapter" + GDE.FILE_ENDING_DOT_XML, false);
				deviceConfig = Analyzer.getInstance().getDeviceConfigurations().get("HoTTAdapter");
			}
			catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		device = this.getInstanceOfDevice(deviceConfig);
		Messages.setDeviceResourceBundle("gde.device.graupner.hott.messages", Settings.getInstance().getLocale(), this.getClass().getClassLoader()); //$NON-NLS-1$
		
		this.serialPort = new HoTTAdapterSerialPort((HoTTAdapter) device, null);

		createContents();
		updateAvailablePorts();
		shlMdlBackuprestore.open();
		log.log(Level.INFO, "shlMdlBackuprestore size = " + shlMdlBackuprestore.getSize());
//		shlMdlBackuprestore.pack();
//		log.log(Level.INFO, "shlMdlBackuprestore size = " + shlMdlBackuprestore.getSize());
//		shlMdlBackuprestore.layout();
//		log.log(Level.INFO, "shlMdlBackuprestore size = " + shlMdlBackuprestore.getSize());
		while (!shlMdlBackuprestore.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shlMdlBackuprestore = new Shell();
		shlMdlBackuprestore.setSize(1032, 466);
		shlMdlBackuprestore.setText("MDL Backup/Restore");
		shlMdlBackuprestore.setLayout(new FillLayout(SWT.HORIZONTAL));
		shlMdlBackuprestore.addListener(SWT.Close, new Listener() {
			@Override
			public void handleEvent(Event evt) {
				if (log.isLoggable(Level.FINE)) log.log(Level.FINE, shlMdlBackuprestore.getLocation().toString() + "event = " + evt); //$NON-NLS-1$

				java.nio.file.Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir") + GDE.STRING_FILE_SEPARATOR_UNIX + "backup_" + txType.getName().toLowerCase());
				try {
					Files.walk(tmpDir).forEach(source -> {
						try {
							if (!Files.isDirectory(source)) {
								System.out.format("deleting file %s\n", source);
								Files.deleteIfExists(source);
							}
						}
						catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
					System.out.format("deleting dir %s\n", tmpDir);
					Files.deleteIfExists(tmpDir);
				}
				catch (IOException e) {
					// ignore
				}
			}
		});
		
		Composite innerComposite = new Composite(shlMdlBackuprestore, SWT.NONE);
		innerComposite.setLayout(new RowLayout(SWT.VERTICAL));
		{
			Group serialPortSelectionGrp = new Group(innerComposite, SWT.NONE);
			RowLayout portSelectionGroupLayout = new RowLayout(SWT.HORIZONTAL);
			portSelectionGroupLayout.center = true;
			serialPortSelectionGrp.setLayout(portSelectionGroupLayout);
			//serialPortSelectionGrp.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
			serialPortSelectionGrp.setLayoutData(new RowData(1024, 30));
			{
				this.portDescription = new CLabel(serialPortSelectionGrp, SWT.NONE);
				//this.portDescription.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.portDescription.setText("serieller Port");
				this.portDescription.setLayoutData(new RowData(90, GDE.IS_LINUX ? 22 : GDE.IS_MAC ? 20 : 18));
				this.portDescription.setToolTipText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0165));
			}
			{
				this.portSelectCombo = new CCombo(serialPortSelectionGrp, SWT.FLAT | SWT.BORDER);
				//this.portSelectCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.portSelectCombo.setLayoutData(new RowData(440, GDE.IS_LINUX ? 22 : GDE.IS_MAC ? 20 : 18));
				this.portSelectCombo.setEditable(false);
				this.portSelectCombo.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0199));
				this.portSelectCombo.setToolTipText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0165));
				this.portSelectCombo.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						MdlBackupRestoreApp.this.selectedPort = MdlBackupRestoreApp.this.portSelectCombo.getItem(MdlBackupRestoreApp.this.portSelectCombo.getSelectionIndex()).trim().split(GDE.STRING_BLANK)[0];
						log.log(Level.OFF, "Selected serial port = " + MdlBackupRestoreApp.this.selectedPort);
						device.setPort(MdlBackupRestoreApp.this.selectedPort);
						readMdlButton.setEnabled(true);
					}
				});
			}
			serialPortSelectionGrp.layout();
		}

		{
			Group listMdlsGroup = new Group(innerComposite, SWT.NONE);
			//if (!GDE.IS_MAC) listMdlsGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
			listMdlsGroup.setLayout(new RowLayout(SWT.HORIZONTAL));
			listMdlsGroup.setLayoutData(new RowData(1024, 320));
			{
				Composite filler = new Composite(listMdlsGroup, SWT.NONE);
				//if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData(new RowData(5, 20));
			}
			{
				this.readMdlButton = new Button(listMdlsGroup, SWT.PUSH | SWT.CENTER);
				this.readMdlButton.setLayoutData(new RowData(155, 33));
				//this.readMdlButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.readMdlButton.setText("Lese MDLs");
				this.readMdlButton.setEnabled(false);
				this.readMdlButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "readMdlButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						isUpdateSerialPorts = false; //stop port scan
						portSelectCombo.setEnabled(false);
						readMdlButton.setEnabled(false);

						new Thread("ReadModels") {
							@Override
							public void run() {
									try {
										pcMdlList.clear();
										selectedMdlList.clear();
										selectedMdlFile.clear();								
										txType = serialPort.loadModelData(txMdlList, pcMdlList, System.getProperty("java.io.tmpdir"), MdlBackupRestoreApp.this.mdlStatusInfoLabel, MdlBackupRestoreApp.this.mdlStatusProgressBar);
										display.asyncExec(new Runnable() {
											@Override
											public void run() {
												pcMdlsTable.removeAll();
												txMdlsTable.removeAll();
												int index = 1;
												for (String mdlName : txMdlList) {
													new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", index), mdlName }); //$NON-NLS-1$
													new TableItem(txMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", index++), mdlName }); //$NON-NLS-1$
													selectedMdlList.add("");
												}
												WaitTimer.delay(500);
												portSelectCombo.setEnabled(true);
												readMdlButton.setEnabled(true);
												saveMdlsButton.setEnabled(true);
												saveToTxButton.setEnabled(true && txType.equals(Transmitter.MZ_12pro));
												fileSelectButton.setEnabled(true);
												upButton.setEnabled(false);
												downButton.setEnabled(false);
												deleteSelectionButton.setEnabled(true);
												deleteAllButton.setEnabled(true);
											}
										});
									}
									catch (IOException | TimeOutException e) {
										openMessageDialog(e.getMessage());
										log.log(Level.WARNING, e.getMessage());
									}
							}
						}.start();
					}
				});
			}
			{
				Composite filler = new Composite(listMdlsGroup, SWT.NONE);
				//if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData(new RowData(700, 20));
			}
			{
				pcMdlsGroup = new Group(listMdlsGroup, SWT.NONE);
				//if (!GDE.IS_MAC) pcMdlsGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				//pcMdlsGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE + 2, SWT.NORMAL));
				pcMdlsGroup.setText("PC");
				pcMdlsGroup.setLayoutData(new RowData(780, 250));
				pcMdlsGroup.setLayout(new FillLayout(SWT.HORIZONTAL));
				{
					this.pcMdlsTable = new Table(pcMdlsGroup, SWT.FULL_SELECTION | SWT.BORDER | SWT.MULTI);
					//pcMdlsTable.setLayoutData(new RowData(500, 180));
					this.pcMdlsTable.setLinesVisible(true);
					this.pcMdlsTable.setHeaderVisible(true);
					//this.pcMdlsTable.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					setPcTableHeader(this.pcMdlsTable);
					this.pcMdlsTable.addListener(SWT.Selection, new Listener() {
						@Override
						public void handleEvent(Event event) {
							pcMdlsTableSelectionIndex = pcMdlsTable.getSelectionIndex() + 1;
							log.log(Level.INFO, "pcMdlsTableSelectionIndex = " + pcMdlsTableSelectionIndex);
							TableItem item = (TableItem) event.item;
							log.log(Level.INFO, "Selection={" + item.getText(0).trim() + "|" + item.getText(1) + "|" + item.getText(2) + "}"); //$NON-NLS-1$ //$NON-NLS-2$
							if (item.getText(1) != null && item.getText(1).length() > 0) {
								int index = Integer.parseInt(item.getText(0).trim());
								if (index == 1) {
									upButton.setEnabled(false);
									downButton.setEnabled(true);
								}
								else if (index > 1 && index <= 248) { //max mdls for transmitter mz-12pro
									upButton.setEnabled(true);
									downButton.setEnabled(true);
								}
								else if (index == 250) { //max mdls for transmitter mz-12pro
									upButton.setEnabled(true);
									downButton.setEnabled(false);
								} 
							}
							else {
								upButton.setEnabled(false);
								downButton.setEnabled(false);
							}
						}
					});
				}
				pcMdlsGroup.layout();
			}
			{
				txMdlsGroup = new Group(listMdlsGroup, SWT.NONE);
				txMdlsGroup.setLayoutData(new RowData(230, 250));
				//if (!GDE.IS_MAC) txMdlsGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				//txMdlsGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE + 2, SWT.NORMAL));
				txMdlsGroup.setText("Tx");
				txMdlsGroup.setLayout(new FillLayout(SWT.HORIZONTAL));
				{
					this.txMdlsTable = new Table(txMdlsGroup, SWT.FULL_SELECTION | SWT.BORDER | SWT.MULTI);
					//txMdlsTable.setLayoutData(new RowData(500, 180));
					this.txMdlsTable.setLinesVisible(true);
					this.txMdlsTable.setHeaderVisible(true);
					//this.txMdlsTable.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					setTxTableHeader(this.txMdlsTable);
				}
				txMdlsGroup.layout();
			}
		}
		{
			Composite actionButtonComposite = new Composite(innerComposite, SWT.NONE);
			//if (!GDE.IS_MAC) actionButtonComposite.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
			actionButtonComposite.setLayout( new RowLayout(org.eclipse.swt.SWT.HORIZONTAL));
			actionButtonComposite.setLayoutData(new RowData(1024, 100));
			actionButtonComposite.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE + 2, SWT.NORMAL));
			{
				Composite filler = new Composite(actionButtonComposite, SWT.NONE);
				//if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData( new RowData(5, 30));
			}
			{
				this.saveMdlsButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.saveMdlsButton.setLayoutData(new RowData(155, 33));
				this.saveMdlsButton.setEnabled(false);
				//this.saveMdlsButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.saveMdlsButton.setText("sichere MDLs");
				this.saveMdlsButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "saveMdlsButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						//open base folder selection dialog
						final String baseFolderName = MdlBackupRestoreApp.this.openDirFileDialog(Messages.getString(MessageIds.GDE_MSGT2432), MdlBackupRestoreApp.this.selectedPcBaseFolder.toString());
						new Thread("BackupModels") {
							@Override
							public void run() {
								try {
									if (baseFolderName.length() < 3) {
										openMessageDialog(Messages.getString(MessageIds.GDE_MSGE2400));
										return;
									}
									//merge moved selected Mdl into pcMdlList, actual it is not part of this list
									if (!selectedMdlFile.isEmpty()) {
										for (Integer index : selectedMdlFile.keySet())
											pcMdlList.set(index - 1, selectedMdlFile.get(index));
										selectedMdlFile.clear();
									}
									//start copy process
									if (baseFolderName != null && baseFolderName.length() > 0) {
										updateMdlTransferProgress(mdlStatusInfoLabel, mdlStatusProgressBar, 0, 0);
										try {
											java.nio.file.Path destDir = Paths.get(baseFolderName);
											java.nio.file.Path srcDir = Paths.get(System.getProperty("java.io.tmpdir") + GDE.STRING_FILE_SEPARATOR_UNIX + "backup_" + txType.getName().toLowerCase());
											long size = Files.walk(srcDir).mapToLong(p -> p.toFile().length()).sum();
											size = size - (size % 8192);
											final long sizes[] = { size, size };
											updateMdlTransferProgress(mdlStatusInfoLabel, mdlStatusProgressBar, sizes[0], sizes[1]);
											final java.nio.file.Path dest = destDir.resolve(srcDir.getFileName());
											log.log(Level.FINE, String.format("copying %s => %s%n", srcDir, dest));
											Files.walk(srcDir).forEach(source -> {
												java.nio.file.Path destination = dest.resolve(srcDir.relativize(source));
												try {
													if (!Files.isDirectory(destination)) {
														log.log(Level.FINE, String.format("Files.copy(%s, %s)%n", source, destination));
														Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
														WaitTimer.delay(100);
														updateMdlTransferProgress(mdlStatusInfoLabel, mdlStatusProgressBar, sizes[0], sizes[1] -= source.toFile().length());
													}
												}
												catch (IOException e) {
													throw new UncheckedIOException(e);
												}
											});
										}
										catch (IOException e) {
											openMessageDialog(e.getMessage());
										}
									}
								}
								catch (Exception e) {
									log.log(Level.SEVERE, e.getMessage(), e);
									openMessageDialog(e.getMessage());
								}
							}
						}.start();
					}
				});
			}
			{
				this.fileSelectButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.fileSelectButton.setLayoutData(new RowData(155, 33));
				this.fileSelectButton.setEnabled(false);
				//this.fileSelectButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.fileSelectButton.setText("Datei");
				this.fileSelectButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "fileSelectButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						FileDialog fd = openFileOpenDialog("MDL Selection", new String[] {"*.mdl"}, selectedPcFolder.toString(), GDE.STRING_EMPTY, SWT.SINGLE);
						String path = fd.getFilterPath();
						selectedPcFolder = path;
						String fileName = fd.getFileName();
						log.log(Level.INFO, path + " - " + fileName);
						//TODO check if selected MDL is from txType, else open message dialog an return
						if (!txType.equals(Transmitter.detectTransmitter(fileName, path))) {
							openMessageDialog("Transmitter radio type doesn't match " + txType.getName() +"/" + Transmitter.detectTransmitter(fileName, path).getName());
							return;
						}
						//merge moved selected Mdl into pcMdlTable actual it is not part of this list
						if (!selectedMdlFile.isEmpty()) {
							for (Integer index : selectedMdlFile.keySet())
								pcMdlList.set(index-1, selectedMdlFile.get(index));
							selectedMdlFile.clear();
						}
						//put new selected MDL to table selection index
						selectedMdlFile.put(pcMdlsTableSelectionIndex, fileName.substring(0, fileName.length()-4) + ";" + path);
						pcMdlsTable.removeAll();
						String tmpdir = System.getProperty("java.io.tmpdir");
						for (int i=0; i<pcMdlList.size(); ++i) {
							if (i == pcMdlsTableSelectionIndex-1)
								new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", i+1), fileName.substring(0, fileName.length()-4), path}); //$NON-NLS-1$
							else
								new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", i+1), pcMdlList.get(i).split(";")[0], pcMdlList.get(i).split(";").length > 1 && !pcMdlList.get(i).split(";")[1].contains(tmpdir) ? pcMdlList.get(i).split(";")[1] : ""}); //$NON-NLS-1$
						}
						upButton.setEnabled(false);
						downButton.setEnabled(false);
						saveMdlsButton.setEnabled(false);
						saveToTxButton.setEnabled(true && txType.equals(Transmitter.MZ_12pro));
					}
				});
			}
			{
				deleteSelectionButton = new Button(actionButtonComposite,SWT.PUSH | SWT.CENTER);
				this.deleteSelectionButton.setLayoutData(new RowData(155, 33));
				this.deleteSelectionButton.setEnabled(false);
				//this.deleteSelectionButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				deleteSelectionButton.setText("Löschen");
				this.deleteSelectionButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "deleteSelectionButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						int selectionIndex = pcMdlsTable.getSelectionIndex();
						TableItem item = pcMdlsTable.getSelection()[0];
						log.log(Level.INFO, "index" + selectionIndex + " Selection={" + item.getText(0).trim() + "|" + item.getText(1) + "|" + item.getText(2) + "}"); //$NON-NLS-1$ //$NON-NLS-2$
						if (item.getText(0).trim().length() > 0) {
							int index = Integer.parseInt(item.getText(0).trim());
							pcMdlsTable.clear(selectionIndex);
							pcMdlList.set(index-1, "");
						}
						upButton.setEnabled(false);
						downButton.setEnabled(false);
					}
				});
			}
			{
				deleteAllButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.deleteAllButton.setLayoutData(new RowData(155, 33));
				this.deleteAllButton.setEnabled(false);
				//this.deleteAllButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				deleteAllButton.setText("Alles Löschen");
				this.deleteAllButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "deleteSelectionButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						pcMdlsTable.clearAll();
						for (int i=0; i<pcMdlList.size(); ++i)
							pcMdlList.set(i, "");
						saveMdlsButton.setEnabled(false);
						saveToTxButton.setEnabled(true && txType.equals(Transmitter.MZ_12pro));
						fileSelectButton.setEnabled(true);
						upButton.setEnabled(false);
						downButton.setEnabled(false);
						deleteSelectionButton.setEnabled(false);
						deleteAllButton.setEnabled(false);
					}
				});
			}
			{
				upButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.upButton.setLayoutData(new RowData(55, 33));
				this.upButton.setEnabled(false);
				//this.upButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				upButton.setImage(SWTResourceManager.getImage("resource/ArrowUp.gif"));
				//upButton.setText("Up");
				this.upButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "upButton.widgetSelected, event=" + evt); //$NON-NLS-1$

						int selectionIndex = pcMdlsTable.getSelectionIndex();
						TableItem item = pcMdlsTable.getSelection()[0];
						log.log(Level.INFO, "index" + selectionIndex + " Selection={" + item.getText(0).trim() + "|" + item.getText(1) + "|" + item.getText(2) + "}"); //$NON-NLS-1$ //$NON-NLS-2$
						int index = Integer.parseInt(item.getText(0).trim());
						String selectedMdl = selectedMdlFile.get(index);
						if (selectedMdl != null) { //selected MDL file found
							selectedMdlFile.put(index-1, selectedMdl);
							selectedMdlFile.remove(index);
							//pcMdlList.set(selectionIndex+1, selectedMdl);								
						}
						else {
							for (int i=0; i<pcMdlList.size(); ++i) {
								if (i == selectionIndex) {
									String upShift = pcMdlList.get(selectionIndex);
									String downShift = pcMdlList.get(selectionIndex-1);
									pcMdlList.set(selectionIndex, downShift);
									pcMdlList.set(selectionIndex-1, upShift);								
									continue;
								}
							}
						}

						pcMdlsTable.removeAll();
						String tmpdir = System.getProperty("java.io.tmpdir");
						int i=1;
						for (String mdlEntry : pcMdlList) {  
							if (selectedMdlFile.get(i) != null) {
								String[] mdlPath = selectedMdlFile.get(i).split(";");
 								new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", i), mdlPath[0], mdlPath[1]}); //$NON-NLS-1$
							}
							else
								new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", i), mdlEntry.split(";")[0], mdlEntry.split(";").length > 1 && !mdlEntry.split(";")[1].contains(tmpdir) ? mdlEntry.split(";")[1] : ""}); //$NON-NLS-1$
							++i;
						}
						pcMdlsTable.setSelection(selectionIndex-=1);
						if (selectionIndex == 0) {
							upButton.setEnabled(false);
							downButton.setEnabled(true);
						}
						else if (selectionIndex > 0 && selectionIndex <= 248) { //max mdls for transmitter mz-12pro
							upButton.setEnabled(true);
							downButton.setEnabled(true);
						}
						else if (selectionIndex == 249) { //max mdls for transmitter mz-12pro
							upButton.setEnabled(true);
							downButton.setEnabled(false);
						}
					}
				});
			}
			{
				downButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.downButton.setLayoutData(new RowData(55, 33));
				this.downButton.setEnabled(false);
				//this.downButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				downButton.setImage(SWTResourceManager.getImage("resource/ArrowDown.gif"));
				//downButton.setText("down");
				this.downButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "downButton.widgetSelected, event=" + evt); //$NON-NLS-1$

						int selectionIndex = pcMdlsTable.getSelectionIndex();
						TableItem item = pcMdlsTable.getSelection()[0];
						log.log(Level.INFO, "index " + selectionIndex + " Selection={" + item.getText(0).trim() + "|" + item.getText(1) + "|" + item.getText(2) + "}"); //$NON-NLS-1$ //$NON-NLS-2$
						int index = Integer.parseInt(item.getText(0).trim());
						String selectedMdl = selectedMdlFile.get(index);
						if (selectedMdl != null) { //selected MDL file found
							selectedMdlFile.put(index+1, selectedMdl);
							selectedMdlFile.remove(index);
							//pcMdlList.set(selectionIndex+1, selectedMdl);
						}
						else {
							for (int i=0; i<pcMdlList.size(); ++i) {
								if (i == selectionIndex) {
									String upShift = pcMdlList.get(selectionIndex+1);
									String downShift = pcMdlList.get(selectionIndex);
									pcMdlList.set(selectionIndex+1, downShift);
									pcMdlList.set(selectionIndex, upShift);
									++i;
									continue;
								}
							}
						}

						pcMdlsTable.removeAll();
						String tmpdir = System.getProperty("java.io.tmpdir");
						int i=1;
						for (String mdlEntry : pcMdlList) {  //TODO contains Temp Windows only
							if (selectedMdlFile.get(i) != null) {
								String[] mdlPath = selectedMdlFile.get(i).split(";");
								new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", i), mdlPath[0], mdlPath[1]}); //$NON-NLS-1$
							}
							else
								new TableItem(pcMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", i), mdlEntry.split(";")[0], mdlEntry.split(";").length > 1 && !mdlEntry.split(";")[1].contains(tmpdir) ? mdlEntry.split(";")[1] : ""}); //$NON-NLS-1$
							++i;
						}
						pcMdlsTable.setSelection(selectionIndex+=1);
						log.log(Level.INFO, "new index " + selectionIndex); //$NON-NLS-1$ //$NON-NLS-2$
						if (selectionIndex == 0) {
							upButton.setEnabled(false);
							downButton.setEnabled(true);
						}
						else if (selectionIndex > 0 && selectionIndex <= 248) { //max mdls for transmitter mz-12pro
							upButton.setEnabled(true);
							downButton.setEnabled(true);
						}
						else if (selectionIndex == 249) { //max mdls for transmitter mz-12pro
							upButton.setEnabled(true);
							downButton.setEnabled(false);
						}
					}
				});
			}
			{
				Composite filler = new Composite(actionButtonComposite, SWT.NONE);
				//if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData( new RowData(90, 33));
			}
			{
				saveToTxButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.saveToTxButton.setLayoutData(new RowData(155, 33));
				this.saveToTxButton.setEnabled(false);
				//this.saveToTxButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				saveToTxButton.setText("Schreibe MDLs");
				this.saveToTxButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "saveToTxButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						readMdlButton.setEnabled(false);
						saveMdlsButton.setEnabled(false);
						saveToTxButton.setEnabled(false);
						fileSelectButton.setEnabled(false);
						upButton.setEnabled(false);
						downButton.setEnabled(false);
						deleteSelectionButton.setEnabled(false);
						deleteAllButton.setEnabled(false);

						new Thread("WriteModels") {
							@Override
							public void run() {
									try {
										//merge moved + selected Mdl into pcMdlTable actual it is not part of this list
										if (!selectedMdlFile.isEmpty()) {
											for (Integer index : selectedMdlFile.keySet())
												pcMdlList.set(index-1, selectedMdlFile.get(index));
											selectedMdlFile.clear();
										}

										//merge pcMdlList with selectedMdlList
										for (int i=0; i<selectedMdlList.size() && i<pcMdlList.size(); ++i) {
											if (!(selectedMdlList.get(i).length() > 0 && selectedMdlList.get(i).contains(";")) && pcMdlList.get(i).length() > 0) { //mdl;path to mdl
												selectedMdlList.set(i, pcMdlList.get(i));
											}	
										}
										String errorMdlFiles = serialPort.writeModelData(selectedMdlList, mdlStatusInfoLabel, mdlStatusProgressBar);
										if (errorMdlFiles.length() > 0) {
											openMessageDialog(errorMdlFiles);
											return;
										}
										display.asyncExec(new Runnable() {
											@Override
											public void run() {
												pcMdlsTable.removeAll();
												txMdlsTable.removeAll();
											}
										});
									}
									catch (Throwable e) {
										log.log(Level.WARNING, e.getMessage());
										openMessageDialog(e.getMessage());
									}
							}
						}.start();
					}
				});
			}
			{
				Composite mdlStatusComposite = new Composite(actionButtonComposite, SWT.NONE);
				//if (!GDE.IS_MAC) mdlStatusComposite.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				mdlStatusComposite.setLayoutData(new RowData(1008, 36));
				mdlStatusComposite.setLayout(new RowLayout(org.eclipse.swt.SWT.HORIZONTAL));
				{
					this.mdlStatusInfoLabel = new CLabel(mdlStatusComposite, SWT.NONE);
					//if (!GDE.IS_MAC) this.mdlStatusInfoLabel.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
					this.mdlStatusInfoLabel.setLayoutData(new RowData(800, 15));
					//this.mdlStatusInfoLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.mdlStatusInfoLabel.setText(Messages.getString(MessageIds.GDE_MSGT2443, new Object[] { 0, 0 }));
				}
				{
					this.mdlStatusProgressBar = new ProgressBar(mdlStatusComposite, SWT.NONE);
					this.mdlStatusProgressBar.setLayoutData(new RowData(1001, 10));
					this.mdlStatusProgressBar.setMinimum(0);
					this.mdlStatusProgressBar.setMaximum(100);
				}
			}
		}
	}

	/**
	 * set the table header number, file name, date, time, size
	 */
	private void setPcTableHeader(Table table) {
		table.removeAll();
		TableColumn[] columns = table.getColumns();
		for (TableColumn tableColumn : columns) {
			tableColumn.dispose();
		}
		this.indexColumn = new TableColumn(table, SWT.LEFT);
		this.indexColumn.setWidth(50);
		this.indexColumn.setText(Messages.getString(MessageIds.GDE_MSGT2445)); //0001
		this.fileNameColum = new TableColumn(table, SWT.LEFT);
		this.fileNameColum.setWidth(150);
		this.fileNameColum.setText(Messages.getString(MessageIds.GDE_MSGT2446)); //qAlpha 250.mdl
		this.fileDateColum = new TableColumn(table, SWT.LEFT);
		this.fileDateColum.setWidth(450);
		this.fileDateColum.setText("Path"); //C:/Users/Documents/DataExplorer/mz-12pro
	}
	/**
	 * set the table header number, file name, date, time, size
	 */
	private void setTxTableHeader(Table table) {
		table.removeAll();
		TableColumn[] columns = table.getColumns();
		for (TableColumn tableColumn : columns) {
			tableColumn.dispose();
		}
		this.indexColumn = new TableColumn(table, SWT.LEFT);
		this.indexColumn.setWidth(40);
		this.indexColumn.setText(Messages.getString(MessageIds.GDE_MSGT2445)); //0001
		this.fileNameColum = new TableColumn(table, SWT.LEFT);
		this.fileNameColum.setWidth(150);
		this.fileNameColum.setText(Messages.getString(MessageIds.GDE_MSGT2446)); //qAlpha 250.mdl
	}

	/**
	 * query the available serial ports and update the serialPortGroup combo
	 */
	void updateAvailablePorts() {
		// execute independent from dialog UI
		this.listPortsThread = new Thread("updateAvailablePorts") {
			@Override
			public void run() {
				try {
					while (MdlBackupRestoreApp.this.shlMdlBackuprestore != null && !MdlBackupRestoreApp.this.shlMdlBackuprestore.isDisposed()) {
						if (MdlBackupRestoreApp.this.isUpdateSerialPorts) {
							MdlBackupRestoreApp.this.availablePorts.clear();
							MdlBackupRestoreApp.this.availablePorts.addAll(DeviceCommPort.listConfiguredSerialPorts(false, "", new Vector<String>()).keySet());
							if (MdlBackupRestoreApp.this.shlMdlBackuprestore != null && !MdlBackupRestoreApp.this.shlMdlBackuprestore.isDisposed()) {
								display.syncExec(new Runnable() {
									@Override
									public void run() {
										if (!MdlBackupRestoreApp.this.shlMdlBackuprestore.isDisposed()) {
											if (MdlBackupRestoreApp.this.availablePorts != null && MdlBackupRestoreApp.this.availablePorts.size() > 0) {
												MdlBackupRestoreApp.this.portSelectCombo.setItems(DeviceCommPort.prepareSerialPortList());
												int index = MdlBackupRestoreApp.this.availablePorts.indexOf(MdlBackupRestoreApp.this.selectedPort);
												if (index > -1) {
													MdlBackupRestoreApp.this.portSelectCombo.select(index);
												}
												else if (MdlBackupRestoreApp.this.selectedPort == null) {
													MdlBackupRestoreApp.this.portSelectCombo.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0197));
												}
											}
											else {
												MdlBackupRestoreApp.this.portSelectCombo.setItems(new String[0]);
												MdlBackupRestoreApp.this.portSelectCombo.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0198));
											}
										}
									}
								});
							}
						}
						else
							return;
						WaitTimer.delay(500);
					}
				}
				catch (Throwable t) {
					MdlBackupRestoreApp.log.log(Level.WARNING, t.getMessage(), t);
				}
				log.log(Level.OFF, "updateAvailablePortsThread time =", StringHelper.getFormatedTime("ss:SSS", (new Date().getTime() - GDE.StartTime)));
			}
		};
		this.listPortsThread.start();
	}

	public String openDirFileDialog(String name, String path) {
		DirectoryDialog fileDirDialog = new DirectoryDialog(GDE.shell, SWT.PRIMARY_MODAL | SWT.NONE);
		if (path != null) {
			path = path.replace(GDE.STRING_FILE_SEPARATOR_UNIX, GDE.FILE_SEPARATOR);
			path = !path.endsWith(GDE.FILE_SEPARATOR) ? path + GDE.FILE_SEPARATOR : path;
		}
		if (log.isLoggable(Level.FINER)) log.log(Level.FINER, "dialogName = " + name + " path = " + path); //$NON-NLS-1$ //$NON-NLS-2$
		fileDirDialog.setText(name);
		if (path != null) fileDirDialog.setFilterPath(path);
		return fileDirDialog.open();
	}

	public FileDialog openFileOpenDialog(String name, String[] extensions, String path, String fileName, int addStyle) {
		FileDialog fileOpenDialog = new FileDialog(GDE.shell, SWT.PRIMARY_MODAL | SWT.OPEN | addStyle);
		if (path != null) {
			path = path.replace(GDE.STRING_FILE_SEPARATOR_UNIX, GDE.FILE_SEPARATOR);
			path = !path.endsWith(GDE.FILE_SEPARATOR) ? path + GDE.FILE_SEPARATOR : path;
		}
		if (log.isLoggable(Level.FINER)) log.log(Level.FINER, "dialogName = " + name + " path = " + path); //$NON-NLS-1$ //$NON-NLS-2$
		fileOpenDialog.setText(name);
		fileOpenDialog.setFileName(fileName == null ? GDE.STRING_EMPTY : fileName);
		if (extensions != null) {
			adaptFilter(fileOpenDialog, extensions);
		}
		if (path != null) fileOpenDialog.setFilterPath(path);
		fileOpenDialog.open();
		return fileOpenDialog;
	}
	
	/**
	 * adapt extensions list to upper and lower case if OS distinguish
	 * @param fileOpenDialog
	 * @param extensions
	 */
	private void adaptFilter(FileDialog fileOpenDialog, String[] extensions) {
		if (!GDE.IS_WINDOWS) { // Apples MAC OS seams to reply with case insensitive file names
			Vector<String> tmpExt = new Vector<String>();
			for (String extension : extensions) {
				if (!extension.equals(GDE.FILE_ENDING_STAR_STAR)) {
					tmpExt.add(extension); // lower case is default
					tmpExt.add(extension.toUpperCase());
				} else
					tmpExt.add(GDE.FILE_ENDING_STAR);
			}
			extensions = tmpExt.toArray(new String[1]);
		}
		fileOpenDialog.setFilterExtensions(extensions);
		fileOpenDialog.setFilterNames(getExtensionDescription(extensions));
	}

	/**
	 * @param extensions
	 * @return the mapped extension description *.osd -> DataExplorer files
	 */
	public String[] getExtensionDescription(String[] extensions) {
		String[] filterNames = new String[extensions.length];
		for (int i = 0; i < filterNames.length; i++) {
			int beginIndex = extensions[i].indexOf(GDE.CHAR_DOT);
			String tmpExt = (beginIndex != -1 ? extensions[i].substring(beginIndex + 1) : extensions[i]);
			filterNames[i] = this.extensionFilterMap.get(tmpExt.toLowerCase());

			if (filterNames[i] == null)
				filterNames[i] = extensions[i];
			else {
				beginIndex = filterNames[i].indexOf(GDE.CHAR_DOT);
				if (beginIndex > 0) { // replace extension case
					String tmpFilterExt = filterNames[i].substring(filterNames[i].indexOf(GDE.CHAR_DOT) + 1, filterNames[i].length() - 1);
					filterNames[i] = tmpExt.equals(tmpFilterExt) ? filterNames[i] : filterNames[i].replace(tmpFilterExt, tmpExt);
				}
			}
		}
		return filterNames;
	}


	public void openMessageDialog(final String message) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				MessageBox messageDialog = new MessageBox(GDE.shell, SWT.OK | SWT.ICON_WARNING);
				messageDialog.setText(GDE.NAME_LONG);
				messageDialog.setMessage(message);
				messageDialog.open();
			}
		});
	}
	
	/**
	 * update text and progressbar information regarding the actual executing file transfer
	 * @param infoLabel displaying the remaining and total size
	 * @param progressBar displaying visual progress
	 * @param totalSize
	 * @param remainingSize
	 */
	public void updateMdlTransferProgress(final CLabel infoLabel, final ProgressBar progressBar, final long totalSize, final long remainingSize) {
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				if (totalSize == 0) {
					progressBar.setSelection(0);
					infoLabel.setText(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGT2443, new Object[] { 0, 0 }));
				} else {
					progressBar.setSelection((int) ((totalSize - remainingSize) * 100 / totalSize));
					infoLabel.setText(Messages.getString(gde.device.graupner.hott.MessageIds.GDE_MSGT2443, new Object[] { (totalSize - remainingSize), totalSize }));
				}
			}
		});
	}

}
