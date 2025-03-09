package gde.device.graupner;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import gde.Analyzer;
import gde.GDE;
import gde.comm.DeviceCommPort;
import gde.config.Settings;
import gde.device.DeviceConfiguration;
import gde.device.IDevice;
import gde.device.graupner.hott.MessageIds;
import gde.messages.Messages;
import gde.ui.DataExplorer;
import gde.ui.SWTResourceManager;
import gde.utils.FileUtils;
import gde.utils.StringHelper;
import gde.utils.WaitTimer;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;

public class MdlBackupRestoreApp {
	final static Logger					log											= Logger.getLogger("MdlBackupRestoreApp");
	
	private String selectedPort;
	HoTTAdapterSerialPort	serialPort = null;
	final DataExplorer					application							= null; //DataExplorer.getInstance();
	final Settings							settings								= Settings.getInstance();
	IDevice device;


	protected Shell shlMdlBackuprestore;
	
	private CCombo							portSelectCombo;
	private CLabel							portDescription;
	private Thread																listPortsThread;
	private boolean																isUpdateSerialPorts					= true;

	private Vector<String>												availablePorts							= new Vector<String>();

	
	private Button							pcBaseFolderButton;
	private CLabel							pcBaseFolderSelectionLabel;
	private Tree								pcFolderTree;
	private TreeItem						pcRootTreeItem;
	private Table								pcFoldersTable;
	private TableColumn					indexColumn, fileNameColum, fileDateColum, fileTimeColum, fileSizeColum;

	private CLabel							mdlBackupInfoLabel;
	private ProgressBar					mdlBackupProgressBar;
	private Button							modelLoadButton;
	
	StringBuilder								selectedPcBaseFolder		= new StringBuilder().append(this.settings.getDataFilePath());
	StringBuilder								selectedPcFolder				= selectedPcBaseFolder;
	TreeItem										lastSelectedPcTreeItem;


	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
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
		device = this.getInstanceOfDevice(deviceConfig);
		Messages.setDeviceResourceBundle("gde.device.graupner.hott.messages", Settings.getInstance().getLocale(), this.getClass().getClassLoader()); //$NON-NLS-1$
		
		this.serialPort = this.application != null ? new HoTTAdapterSerialPort((HoTTAdapter) device, this.application) : new HoTTAdapterSerialPort((HoTTAdapter) device, null);

		Display display = Display.getDefault();
		createContents();
		updateAvailablePorts();
		shlMdlBackuprestore.open();
		shlMdlBackuprestore.pack();
		shlMdlBackuprestore.layout();
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
		shlMdlBackuprestore.setSize(450, 300);
		shlMdlBackuprestore.setText("MZ-12Pro MDL Backup/Restore");
		shlMdlBackuprestore.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		Composite innerComposite = new Composite(shlMdlBackuprestore, SWT.NONE);
		innerComposite.setLayout(new RowLayout(SWT.VERTICAL));
		{
			Group serialPortSelectionGroup = new Group(innerComposite, SWT.NONE);
			RowLayout portSelectionGroupLayout = new RowLayout(SWT.HORIZONTAL);
			portSelectionGroupLayout.center = true;
			serialPortSelectionGroup.setLayout(portSelectionGroupLayout);
			serialPortSelectionGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
			serialPortSelectionGroup.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0163));
			serialPortSelectionGroup.setLayoutData(new RowData(1090, 40));
			{
				this.portDescription = new CLabel(serialPortSelectionGroup, SWT.NONE);
				this.portDescription.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.portDescription.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0164));
				this.portDescription.setLayoutData(new RowData(50, GDE.IS_LINUX ? 22 : GDE.IS_MAC ? 20 : 18));
				this.portDescription.setToolTipText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0165));
			}
			{
				this.portSelectCombo = new CCombo(serialPortSelectionGroup, SWT.FLAT | SWT.BORDER);
				this.portSelectCombo.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.portSelectCombo.setLayoutData(new RowData(440, GDE.IS_LINUX ? 22 : GDE.IS_MAC ? 20 : 18));
				this.portSelectCombo.setEditable(false);
				this.portSelectCombo.setText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0199));
				this.portSelectCombo.setToolTipText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0165));
				this.portSelectCombo.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						MdlBackupRestoreApp.this.selectedPort = MdlBackupRestoreApp.this.portSelectCombo.getItem(MdlBackupRestoreApp.this.portSelectCombo.getSelectionIndex()).trim().split(GDE.STRING_BLANK)[0];
						MdlBackupRestoreApp.this.isUpdateSerialPorts = false;
						log.log(Level.OFF, "Selected serial port = " + MdlBackupRestoreApp.this.selectedPort);
						device.setPort(MdlBackupRestoreApp.this.selectedPort);
					}
				});
			}
			serialPortSelectionGroup.layout();
		}

		{
			Group pcFolderGroup = new Group(innerComposite, SWT.NONE);
			if (!GDE.IS_MAC) pcFolderGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
			RowLayout pcGroupLayout = new RowLayout(org.eclipse.swt.SWT.HORIZONTAL);
			pcFolderGroup.setLayout(pcGroupLayout);
			pcFolderGroup.setLayoutData(new RowData(1090, 250));
			pcFolderGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE + 2, SWT.NORMAL));
			pcFolderGroup.setText(Messages.getString(MessageIds.GDE_MSGT2427));
			{
				Composite filler = new Composite(pcFolderGroup, SWT.NONE);
				//if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				GridLayout composite1Layout = new GridLayout();
				composite1Layout.makeColumnsEqualWidth = true;
				RowData composite1LData = new RowData();
				composite1LData.width = 5;
				composite1LData.height = 20;
				filler.setLayoutData(composite1LData);
				filler.setLayout(composite1Layout);
			}
			{
				this.pcBaseFolderButton = new Button(pcFolderGroup, SWT.PUSH | SWT.CENTER);
				RowData pcBaseFolderButtonLData = new RowData();
				pcBaseFolderButtonLData.width = 155;
				pcBaseFolderButtonLData.height = 33;
				this.pcBaseFolderButton.setLayoutData(pcBaseFolderButtonLData);
				this.pcBaseFolderButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.pcBaseFolderButton.setText(Messages.getString(MessageIds.GDE_MSGT2432));
				this.pcBaseFolderButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "pcBaseFolderButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						String baseFolderName = application.openDirFileDialog(Messages.getString(MessageIds.GDE_MSGT2432), selectedPcBaseFolder.toString());
						if (baseFolderName != null && baseFolderName.length() > 0) {
							pcBaseFolderSelectionLabel.setText(baseFolderName);
							updatePcBaseFolder();
						}
					}
				});
			}
			{
				this.pcBaseFolderSelectionLabel = new CLabel(pcFolderGroup, SWT.NONE);
				if (!GDE.IS_MAC) this.pcBaseFolderSelectionLabel.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				this.pcBaseFolderSelectionLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.pcBaseFolderSelectionLabel.setText(this.selectedPcBaseFolder.toString());
				RowData pcBaseFolderSelectionLabelLData = new RowData();
				pcBaseFolderSelectionLabelLData.width = 580 + 337;
				pcBaseFolderSelectionLabelLData.height = 28;
				this.pcBaseFolderSelectionLabel.setLayoutData(pcBaseFolderSelectionLabelLData);
			}
			{
				Composite filler = new Composite(pcFolderGroup, SWT.NONE);
				if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				GridLayout compositeLayout = new GridLayout();
				compositeLayout.makeColumnsEqualWidth = true;
				RowData compositeLData = new RowData();
				compositeLData.width = 5;
				compositeLData.height = 180;
				filler.setLayoutData(compositeLData);
				filler.setLayout(compositeLayout);
			}
			{
				RowData pcFolderTreeLData = new RowData();
				pcFolderTreeLData.width = 425;
				pcFolderTreeLData.height = 175;
				this.pcFolderTree = new Tree(pcFolderGroup, SWT.BORDER);
				this.pcFolderTree.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.pcFolderTree.setLayoutData(pcFolderTreeLData);
				this.pcFolderTree.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "pcFolderTree.widgetSelected, event=" + evt); //$NON-NLS-1$
						TreeItem evtItem = (TreeItem) evt.item;
						log.log(Level.FINEST, "pcFolderTree.widgetSelected, tree item = " + evtItem.getText()); //$NON-NLS-1$
						updateSelectedPcFolder(evtItem);
					}
				});
				{
					this.pcRootTreeItem = new TreeItem(this.pcFolderTree, SWT.NONE);
					this.pcRootTreeItem.setText(this.selectedPcBaseFolder.substring(this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX) + 1));
					updatePcBaseFolder();
				}
			}
			{
				Composite filler = new Composite(pcFolderGroup, SWT.NONE);
				if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				GridLayout composite1Layout = new GridLayout();
				composite1Layout.makeColumnsEqualWidth = true;
				RowData composite1LData = new RowData();
				composite1LData.width = 15;
				composite1LData.height = 180;
				filler.setLayoutData(composite1LData);
				filler.setLayout(composite1Layout);
			}
			{
				this.pcFoldersTable = new Table(pcFolderGroup, SWT.FULL_SELECTION | SWT.BORDER | SWT.MULTI);
				RowData targetDirectoryTableLData = new RowData();
				targetDirectoryTableLData.width = 580;
				targetDirectoryTableLData.height = 175;
				this.pcFoldersTable.setLayoutData(targetDirectoryTableLData);
				this.pcFoldersTable.setLinesVisible(true);
				this.pcFoldersTable.setHeaderVisible(true);
				this.pcFoldersTable.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				setTableHeader(this.pcFoldersTable);
				this.pcFoldersTable.addListener(SWT.Selection, new Listener() {
					@Override
					public void handleEvent(Event event) {
						TableItem item = (TableItem) event.item;
						log.log(Level.FINE, "Selection={" + item.getText(1) + "}"); //$NON-NLS-1$ //$NON-NLS-2$
						TableItem[] selection = pcFoldersTable.getSelection();
					}
				});
			}
		}
		{
			Group mdlBackupGroup = new Group(innerComposite, SWT.NONE);
			if (!GDE.IS_MAC) mdlBackupGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
			mdlBackupGroup.setLayout( new RowLayout(org.eclipse.swt.SWT.HORIZONTAL));
			mdlBackupGroup.setLayoutData(new RowData(1090, 50));
			mdlBackupGroup.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE + 2, SWT.NORMAL));
			mdlBackupGroup.setText("MDL Backup");
			{
				Composite filler = new Composite(mdlBackupGroup, SWT.NONE);
				if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData( new RowData(5, 30));
			}
			{
				this.modelLoadButton = new Button(mdlBackupGroup, SWT.PUSH | SWT.CENTER);
				this.modelLoadButton.setLayoutData(new RowData(155, 33));
				this.modelLoadButton.setEnabled(true);
				this.modelLoadButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.modelLoadButton.setText(Messages.getString(MessageIds.GDE_MSGT2437));
				this.modelLoadButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "downLoadButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						new Thread("BackupModels") {
							@Override
							public void run() {
								try {
									if (selectedPcFolder.length() < 3) {
										application.openMessageDialog(Messages.getString(MessageIds.GDE_MSGE2400));
										return;
									}
									serialPort.loadModelData(selectedPcFolder.toString(), MdlBackupRestoreApp.this.mdlBackupInfoLabel, MdlBackupRestoreApp.this.mdlBackupProgressBar);
									updatePcFolder();
								}
								catch (Exception e) {
									log.log(Level.SEVERE, e.getMessage(), e);
									application.openMessageDialog(e.getMessage());
								}
							}
						}.start();
					}
				});
			}
			{
				Composite mdlBackupComposite = new Composite(mdlBackupGroup, SWT.NONE);
				if (!GDE.IS_MAC) mdlBackupComposite.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				mdlBackupComposite.setLayoutData(new RowData(900, 36));
				mdlBackupComposite.setLayout(new RowLayout(org.eclipse.swt.SWT.HORIZONTAL));
				{
					this.mdlBackupInfoLabel = new CLabel(mdlBackupComposite, SWT.NONE);
					if (!GDE.IS_MAC) this.mdlBackupInfoLabel.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
					this.mdlBackupInfoLabel.setLayoutData(new RowData(800, 15));
					this.mdlBackupInfoLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
					this.mdlBackupInfoLabel.setText(Messages.getString(MessageIds.GDE_MSGT2443, new Object[] { 0, 0 }));
				}
				{
					this.mdlBackupProgressBar = new ProgressBar(mdlBackupComposite, SWT.NONE);
					this.mdlBackupProgressBar.setLayoutData(new RowData(900, 10));
					this.mdlBackupProgressBar.setMinimum(0);
					this.mdlBackupProgressBar.setMaximum(100);
				}
			}
		}
		updateSelectedPcFolder(null); //Initialize
	}
	

	/**
	 * update PC directory/folder
	 */
	public void updatePcFolder() {
		final TreeItem treeItem = this.lastSelectedPcTreeItem;
		GDE.display.asyncExec(new Runnable() {
			@Override
			public void run() {
				updateSelectedPcFolder(treeItem);
			}
		});
	}

	
	/**
	 *
	 */
	private void updatePcBaseFolder() {
		for (TreeItem item : this.pcRootTreeItem.getItems()) {
			item.dispose();
		}
		this.selectedPcBaseFolder = new StringBuilder().append(this.pcBaseFolderSelectionLabel.getText().replace(GDE.CHAR_FILE_SEPARATOR_WINDOWS, GDE.CHAR_FILE_SEPARATOR_UNIX));
		this.pcRootTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
		String baseFolderName = this.selectedPcBaseFolder.length() > this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX) + 1 ? this.selectedPcBaseFolder.substring(this.selectedPcBaseFolder
				.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX) + 1) : GDE.IS_WINDOWS ? this.selectedPcBaseFolder.substring(0, this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX))
				: this.selectedPcBaseFolder.substring(this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX));
		this.pcRootTreeItem.setText(baseFolderName);
		try {
			//getDirListing gets only direct child folders, no sub child folders
			List<File> folderList = FileUtils.getDirListing(new File(this.selectedPcBaseFolder.toString()));
			for (File folder : folderList) {
				TreeItem tmpTreeItem = new TreeItem(this.pcRootTreeItem, SWT.NONE);
				tmpTreeItem.setText(folder.getName());
				tmpTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
			}

			//display opened folder icon and expand the tree if there are child nodes
			if (this.pcRootTreeItem.getItemCount() > 1) {
				this.pcRootTreeItem.setExpanded(true);
				this.pcRootTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/FolderOpen.gif")); //$NON-NLS-1$
			}
		}
		catch (FileNotFoundException e) {
			FileTransferTabItem.log.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	/**
	 * update the tree item icons and file listing of the selected directory/folder
	 * @param evtItem
	 */
	private void updateSelectedPcFolder(TreeItem evtItem) {
		try {
			TreeItem parentItem, tmpItem;
			setTableHeader(this.pcFoldersTable);

			if (evtItem == null) evtItem = this.pcRootTreeItem;
			for (TreeItem item : evtItem.getItems()) {
				item.dispose();
			}
			//apply closed folder icon to previous selected tree item
			if (this.lastSelectedPcTreeItem != null && !this.lastSelectedPcTreeItem.isDisposed() && this.lastSelectedPcTreeItem.getParentItem() != null) {
				this.lastSelectedPcTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
				while (!this.pcRootTreeItem.getText().equals((parentItem = this.lastSelectedPcTreeItem.getParentItem()).getText())) {
					parentItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
					this.lastSelectedPcTreeItem = parentItem;
				}
			}

			//build path traversing tree items, apply open folder icon
			this.selectedPcFolder = new StringBuilder().append(GDE.STRING_FILE_SEPARATOR_UNIX).append(evtItem.getText());
			tmpItem = evtItem;
			parentItem = tmpItem.getParentItem();
			if (parentItem != null) {
				while (this.pcRootTreeItem != (parentItem = tmpItem.getParentItem())) {
					this.selectedPcFolder.insert(0, parentItem.getText());
					this.selectedPcFolder.insert(0, GDE.STRING_FILE_SEPARATOR_UNIX);
					parentItem.setImage(SWTResourceManager.getImage("/gde/resource/FolderOpen.gif")); //$NON-NLS-1$
					tmpItem = parentItem;
				}
				this.selectedPcFolder.insert(0, this.selectedPcBaseFolder);
			}
			else {
				this.selectedPcFolder = new StringBuilder().append(this.selectedPcBaseFolder);
			}
			//update with new folder and file information
			FileTransferTabItem.log.log(Level.FINE, "selectedPcFolder = " + this.selectedPcFolder.toString());
			List<File> files = FileUtils.getFileListing(new File(this.selectedPcFolder.toString()), 0);
			int index = 0;
			for (File file : files) {
				new TableItem(this.pcFoldersTable, SWT.NONE).setText(new String[] { GDE.STRING_EMPTY + index++, file.getName(), StringHelper.getFormatedTime("yyyy-MM-dd", file.lastModified()), //$NON-NLS-1$
						StringHelper.getFormatedTime("HH:mm", file.lastModified()), GDE.STRING_EMPTY + file.length() }); //$NON-NLS-1$
			}
			List<File> folders = FileUtils.getDirListing(new File(this.selectedPcFolder.toString()));
			for (File folder : folders) {
				TreeItem tmpTreeItem = new TreeItem(evtItem, SWT.NONE);
				tmpTreeItem.setText(folder.getName());
				tmpTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
			}
			evtItem.setExpanded(true);
			evtItem.setImage(SWTResourceManager.getImage("/gde/resource/FolderOpen.gif")); //$NON-NLS-1$
			this.lastSelectedPcTreeItem = evtItem;
		}
		catch (Exception e) {
			FileTransferTabItem.log.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	/**
	 * set the table header number, file name, date, time, size
	 */
	private void setTableHeader(Table table) {
		table.removeAll();
		TableColumn[] columns = table.getColumns();
		for (TableColumn tableColumn : columns) {
			tableColumn.dispose();
		}
		this.indexColumn = new TableColumn(table, SWT.CENTER);
		this.indexColumn.setWidth(44);
		this.indexColumn.setText(Messages.getString(MessageIds.GDE_MSGT2445)); //0001
		this.fileNameColum = new TableColumn(table, SWT.LEFT);
		this.fileNameColum.setWidth(218);
		this.fileNameColum.setText(Messages.getString(MessageIds.GDE_MSGT2446)); //0005_2012-4-25.bin
		this.fileDateColum = new TableColumn(table, SWT.CENTER);
		this.fileDateColum.setWidth(118);
		this.fileDateColum.setText(Messages.getString(MessageIds.GDE_MSGT2447)); //2012-05-28
		this.fileTimeColum = new TableColumn(table, SWT.CENTER);
		this.fileTimeColum.setWidth(64);
		this.fileTimeColum.setText(Messages.getString(MessageIds.GDE_MSGT2448)); //2012-05-28
		this.fileSizeColum = new TableColumn(table, SWT.RIGHT);
		this.fileSizeColum.setWidth(123);
		this.fileSizeColum.setText(Messages.getString(MessageIds.GDE_MSGT2449)); //2012-05-28
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
							MdlBackupRestoreApp.this.availablePorts.addAll(DeviceCommPort.listConfiguredSerialPorts(
									MdlBackupRestoreApp.this.settings.doPortAvailabilityCheck(),
									MdlBackupRestoreApp.this.settings.isSerialPortBlackListEnabled() ? MdlBackupRestoreApp.this.settings.getSerialPortBlackList() : GDE.STRING_EMPTY,
									MdlBackupRestoreApp.this.settings.isSerialPortWhiteListEnabled() ? MdlBackupRestoreApp.this.settings.getSerialPortWhiteList() : new Vector<String>()).keySet());
							if (MdlBackupRestoreApp.this.shlMdlBackuprestore != null && !MdlBackupRestoreApp.this.shlMdlBackuprestore.isDisposed()) {
								GDE.display.syncExec(new Runnable() {
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

}
