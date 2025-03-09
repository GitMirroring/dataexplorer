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
import gde.exception.TimeOutException;
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
import java.io.IOException;
import java.util.ArrayList;
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

	ArrayList<String> mdlArrayList;
	
	private Button							readMdlButton;
	private Table								pcMdlsTable, txMdlsTable;
	private TableColumn					indexColumn, fileNameColum, fileDateColum, fileTimeColum, fileSizeColum;

	private CLabel							mdlStatusInfoLabel;
	private ProgressBar					mdlStatusProgressBar;
	private Button							saveMdlsButton;
	
	StringBuilder								selectedPcBaseFolder		= new StringBuilder().append(this.settings.getDataFilePath());
	StringBuilder								selectedPcFolder				= selectedPcBaseFolder;
	TreeItem										lastSelectedPcTreeItem;
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
		shlMdlBackuprestore.setText("MDL Backup/Restore");
		shlMdlBackuprestore.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		Composite innerComposite = new Composite(shlMdlBackuprestore, SWT.NONE);
		innerComposite.setLayout(new RowLayout(SWT.VERTICAL));
		{
			Group serialPortSelectionGrp = new Group(innerComposite, SWT.NONE);
			RowLayout portSelectionGroupLayout = new RowLayout(SWT.HORIZONTAL);
			portSelectionGroupLayout.center = true;
			serialPortSelectionGrp.setLayout(portSelectionGroupLayout);
			serialPortSelectionGrp.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
			serialPortSelectionGrp.setLayoutData(new RowData(1024, 30));
			{
				this.portDescription = new CLabel(serialPortSelectionGrp, SWT.NONE);
				this.portDescription.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.portDescription.setText("serieller Port");
				this.portDescription.setLayoutData(new RowData(90, GDE.IS_LINUX ? 22 : GDE.IS_MAC ? 20 : 18));
				this.portDescription.setToolTipText(Messages.getString(gde.messages.MessageIds.GDE_MSGT0165));
			}
			{
				this.portSelectCombo = new CCombo(serialPortSelectionGrp, SWT.FLAT | SWT.BORDER);
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
						readMdlButton.setEnabled(true);
					}
				});
			}
			serialPortSelectionGrp.layout();
		}

		{
			Group listMdlsGroup = new Group(innerComposite, SWT.NONE);
			if (!GDE.IS_MAC) listMdlsGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
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
				this.readMdlButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.readMdlButton.setText("Lese Modellspeicher");
				this.readMdlButton.setEnabled(false);
				this.readMdlButton.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent evt) {
						log.log(Level.FINEST, "readMdlButton.widgetSelected, event=" + evt); //$NON-NLS-1$
						new Thread("ReadModels") {
							@Override
							public void run() {
									try {
										serialPort.loadModelData(mdlArrayList, System.getProperty("java.io.tmpdir"), MdlBackupRestoreApp.this.mdlStatusInfoLabel, MdlBackupRestoreApp.this.mdlStatusProgressBar);
										int index = 1;
										for (String mdlName : mdlArrayList) {
											new TableItem(txMdlsTable, SWT.NONE).setText(new String[] { String.format(" %-3d", index++), mdlName }); //$NON-NLS-1$
										}
									}
									catch (IOException | TimeOutException e) {
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
				if (!GDE.IS_MAC) pcMdlsGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
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
							TableItem item = (TableItem) event.item;
							log.log(Level.FINE, "Selection={" + item.getText(1) + "}"); //$NON-NLS-1$ //$NON-NLS-2$
							TableItem[] selection = pcMdlsTable.getSelection();
						}
					});
				}
				pcMdlsGroup.layout();
			}
			{
				txMdlsGroup = new Group(listMdlsGroup, SWT.NONE);
				txMdlsGroup.setLayoutData(new RowData(200, 250));
				if (!GDE.IS_MAC) txMdlsGroup.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
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
			if (!GDE.IS_MAC) actionButtonComposite.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
			actionButtonComposite.setLayout( new RowLayout(org.eclipse.swt.SWT.HORIZONTAL));
			actionButtonComposite.setLayoutData(new RowData(1024, 100));
			actionButtonComposite.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE + 2, SWT.NORMAL));
			{
				Composite filler = new Composite(actionButtonComposite, SWT.NONE);
				if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData( new RowData(5, 30));
			}
			{
				this.saveMdlsButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.saveMdlsButton.setLayoutData(new RowData(155, 33));
				this.saveMdlsButton.setEnabled(false);
				this.saveMdlsButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.saveMdlsButton.setText(Messages.getString(MessageIds.GDE_MSGT2437));
				this.saveMdlsButton.addSelectionListener(new SelectionAdapter() {
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
				this.fileSelectButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.fileSelectButton.setLayoutData(new RowData(155, 33));
				this.fileSelectButton.setEnabled(false);
				this.fileSelectButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				this.fileSelectButton.setText("Datei");
			}
			{
				deleteSelectionButton = new Button(actionButtonComposite,SWT.PUSH | SWT.CENTER);
				this.deleteSelectionButton.setLayoutData(new RowData(155, 33));
				this.deleteSelectionButton.setEnabled(false);
				this.deleteSelectionButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				deleteSelectionButton.setText("Löschen");
			}
			{
				deleteAllButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.deleteAllButton.setLayoutData(new RowData(155, 33));
				this.deleteAllButton.setEnabled(false);
				this.deleteAllButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				deleteAllButton.setText("Alles Löschen");
			}
			{
				upButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.upButton.setLayoutData(new RowData(60, 33));
				this.upButton.setEnabled(false);
				this.upButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				upButton.setText("Up");
			}
			{
				downButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.downButton.setLayoutData(new RowData(60, 33));
				this.downButton.setEnabled(false);
				this.downButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				downButton.setText("down");
			}
			{
				Composite filler = new Composite(actionButtonComposite, SWT.NONE);
				if (!GDE.IS_MAC) filler.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				filler.setLayoutData( new RowData(90, 33));
			}
			{
				saveToTxButton = new Button(actionButtonComposite, SWT.PUSH | SWT.CENTER);
				this.saveToTxButton.setLayoutData(new RowData(155, 33));
				this.saveToTxButton.setEnabled(false);
				this.saveToTxButton.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
				saveToTxButton.setText("Schreibe Modellspeicher");
			}
			{
				Composite mdlStatusComposite = new Composite(actionButtonComposite, SWT.NONE);
				if (!GDE.IS_MAC) mdlStatusComposite.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
				mdlStatusComposite.setLayoutData(new RowData(1008, 36));
				mdlStatusComposite.setLayout(new RowLayout(org.eclipse.swt.SWT.HORIZONTAL));
				{
					this.mdlStatusInfoLabel = new CLabel(mdlStatusComposite, SWT.NONE);
					if (!GDE.IS_MAC) this.mdlStatusInfoLabel.setBackground(SWTResourceManager.getColor(this.settings.getUtilitySurroundingBackground()));
					this.mdlStatusInfoLabel.setLayoutData(new RowData(800, 15));
					this.mdlStatusInfoLabel.setFont(SWTResourceManager.getFont(GDE.WIDGET_FONT_NAME, GDE.WIDGET_FONT_SIZE, SWT.NORMAL));
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
		//updateSelectedPcFolder(null); //Initialize
	}
	

	/**
	 * update PC directory/folder
	 */
	public void updatePcFolder() {
		final TreeItem treeItem = this.lastSelectedPcTreeItem;
		GDE.display.asyncExec(new Runnable() {
			@Override
			public void run() {
				//updateSelectedPcFolder(treeItem);
			}
		});
	}

	
	/**
	 *
	 */
	private void updatePcBaseFolder() {
		String baseFolderName = this.selectedPcBaseFolder.length() > this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX) + 1 ? this.selectedPcBaseFolder.substring(this.selectedPcBaseFolder
				.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX) + 1) : GDE.IS_WINDOWS ? this.selectedPcBaseFolder.substring(0, this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX))
				: this.selectedPcBaseFolder.substring(this.selectedPcBaseFolder.lastIndexOf(GDE.STRING_FILE_SEPARATOR_UNIX));
		try {
			//getDirListing gets only direct child folders, no sub child folders
			List<File> folderList = FileUtils.getDirListing(new File(this.selectedPcBaseFolder.toString()));
//			for (File folder : folderList) {
//				TreeItem tmpTreeItem = new TreeItem(this.pcRootTreeItem, SWT.NONE);
//				tmpTreeItem.setText(folder.getName());
//				tmpTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
//			}

			//display opened folder icon and expand the tree if there are child nodes
//			if (this.pcRootTreeItem.getItemCount() > 1) {
//				this.pcRootTreeItem.setExpanded(true);
//				this.pcRootTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/FolderOpen.gif")); //$NON-NLS-1$
//			}
		}
		catch (FileNotFoundException e) {
			FileTransferTabItem.log.log(Level.SEVERE, e.getMessage(), e);
		}
	}

	/**
	 * update the tree item icons and file listing of the selected directory/folder
	 * @param evtItem
	 */
//	private void updateSelectedPcFolder(TreeItem evtItem) {
//		try {
//			TreeItem parentItem, tmpItem;
//			setTableHeader(this.pcFoldersTable);
//
//			if (evtItem == null) evtItem = this.pcRootTreeItem;
//			for (TreeItem item : evtItem.getItems()) {
//				item.dispose();
//			}
//			//apply closed folder icon to previous selected tree item
//			if (this.lastSelectedPcTreeItem != null && !this.lastSelectedPcTreeItem.isDisposed() && this.lastSelectedPcTreeItem.getParentItem() != null) {
//				this.lastSelectedPcTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
//				while (!this.pcRootTreeItem.getText().equals((parentItem = this.lastSelectedPcTreeItem.getParentItem()).getText())) {
//					parentItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
//					this.lastSelectedPcTreeItem = parentItem;
//				}
//			}
//
//			//build path traversing tree items, apply open folder icon
//			this.selectedPcFolder = new StringBuilder().append(GDE.STRING_FILE_SEPARATOR_UNIX).append(evtItem.getText());
//			tmpItem = evtItem;
//			parentItem = tmpItem.getParentItem();
//			if (parentItem != null) {
//				while (this.pcRootTreeItem != (parentItem = tmpItem.getParentItem())) {
//					this.selectedPcFolder.insert(0, parentItem.getText());
//					this.selectedPcFolder.insert(0, GDE.STRING_FILE_SEPARATOR_UNIX);
//					parentItem.setImage(SWTResourceManager.getImage("/gde/resource/FolderOpen.gif")); //$NON-NLS-1$
//					tmpItem = parentItem;
//				}
//				this.selectedPcFolder.insert(0, this.selectedPcBaseFolder);
//			}
//			else {
//				this.selectedPcFolder = new StringBuilder().append(this.selectedPcBaseFolder);
//			}
//			//update with new folder and file information
//			FileTransferTabItem.log.log(Level.FINE, "selectedPcFolder = " + this.selectedPcFolder.toString());
//			List<File> files = FileUtils.getFileListing(new File(this.selectedPcFolder.toString()), 0);
//			int index = 0;
//			for (File file : files) {
//				new TableItem(this.pcFoldersTable, SWT.NONE).setText(new String[] { GDE.STRING_EMPTY + index++, file.getName(), StringHelper.getFormatedTime("yyyy-MM-dd", file.lastModified()), //$NON-NLS-1$
//						StringHelper.getFormatedTime("HH:mm", file.lastModified()), GDE.STRING_EMPTY + file.length() }); //$NON-NLS-1$
//			}
//			List<File> folders = FileUtils.getDirListing(new File(this.selectedPcFolder.toString()));
//			for (File folder : folders) {
//				TreeItem tmpTreeItem = new TreeItem(evtItem, SWT.NONE);
//				tmpTreeItem.setText(folder.getName());
//				tmpTreeItem.setImage(SWTResourceManager.getImage("/gde/resource/Folder.gif")); //$NON-NLS-1$
//			}
//			evtItem.setExpanded(true);
//			evtItem.setImage(SWTResourceManager.getImage("/gde/resource/FolderOpen.gif")); //$NON-NLS-1$
//			this.lastSelectedPcTreeItem = evtItem;
//		}
//		catch (Exception e) {
//			FileTransferTabItem.log.log(Level.SEVERE, e.getMessage(), e);
//		}
//	}

	/**
	 * set the table header number, file name, date, time, size
	 */
	private void setPcTableHeader(Table table) {
		table.removeAll();
		TableColumn[] columns = table.getColumns();
		for (TableColumn tableColumn : columns) {
			tableColumn.dispose();
		}
		this.indexColumn = new TableColumn(table, SWT.CENTER);
		this.indexColumn.setWidth(50);
		this.indexColumn.setText(Messages.getString(MessageIds.GDE_MSGT2445)); //0001
		this.fileNameColum = new TableColumn(table, SWT.CENTER);
		this.fileNameColum.setWidth(150);
		this.fileNameColum.setText(Messages.getString(MessageIds.GDE_MSGT2446)); //qAlpha 250.mdl
		this.fileDateColum = new TableColumn(table, SWT.CENTER);
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
		this.indexColumn = new TableColumn(table, SWT.CENTER);
		this.indexColumn.setWidth(40);
		this.indexColumn.setText(Messages.getString(MessageIds.GDE_MSGT2445)); //0001
		this.fileNameColum = new TableColumn(table, SWT.CENTER);
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
