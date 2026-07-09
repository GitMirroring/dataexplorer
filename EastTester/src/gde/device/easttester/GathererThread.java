package gde.device.easttester;

import java.util.logging.Logger;

import org.eclipse.swt.SWT;

import gde.GDE;
import gde.data.Channel;
import gde.data.Channels;
import gde.data.RecordSet;
import gde.device.ChannelTypes;
import gde.device.PropertyType;
import gde.exception.ApplicationConfigurationException;
import gde.exception.DataInconsitsentException;
import gde.exception.DevicePropertiesInconsistenceException;
import gde.exception.SerialPortException;
import gde.exception.TimeOutException;
import gde.log.Level;
import gde.messages.Messages;
import gde.ui.DataExplorer;
import gde.utils.TimeLine;
import gde.utils.WaitTimer;

public class GathererThread extends Thread {
	final static String			$CLASS_NAME									= GathererThread.class.getName();
	final static Logger			log													= Logger.getLogger(GathererThread.class.getName());
	final static int				WAIT_TIME_RETRYS						= 36;

	final DataExplorer			application;
	final ET5410SerialPort	serialPort;
	final ET5410						device;
	final Channels					channels;
	
	Channel									activeChannel;
	RecordSet								activeRecordSet;
	int											channelNumber;
	Channel									channel;
	int											stateNumber									= 1;
	String									recordSetKey;
	boolean									isPortOpenedByLiveGatherer	= false;
	int											retryCounter								= GathererThread.WAIT_TIME_RETRYS;									// 36 * 5 sec timeout = 180 sec


	/**
	 * data gatherer thread definition 
	 * @throws SerialPortException 
	 * @throws ApplicationConfigurationException 
	 * @throws Exception 
	 */
	public GathererThread(DataExplorer currentApplication, ET5410 useDevice, ET5410SerialPort useSerialPort, int channelConfigNumber)
			throws ApplicationConfigurationException, SerialPortException {
		super("dataGatherer");
		this.application = currentApplication;
		this.device = useDevice;
		this.serialPort = useSerialPort;
		this.channels = Channels.getInstance();
		this.channelNumber = channelConfigNumber;
		this.channel = this.channels.get(this.channelNumber);
		this.recordSetKey = GDE.STRING_BLANK + this.device.getRecordSetStateNameReplacement(this.stateNumber);

		if (!this.serialPort.isConnected()) {
			this.serialPort.open();
			this.isPortOpenedByLiveGatherer = true;
		}
	}

	@Override
	public void run() {
		final String $METHOD_NAME = "run"; //$NON-NLS-1$
		String processName = GDE.STRING_EMPTY;
		
		RecordSet channelRecordSet = null;
		long startCycleTime = 0;
		long tmpCycleTime = 0;
		long lastTmpCycleTime = 0;
		long delayTime = 0;
		long measurementCount = -1;
		double deviceTimeStep_ms = device.getTimeStep_ms();
		String[] deviceIdentifier = new String[] {GDE.STRING_QUESTION_MARK, GDE.STRING_QUESTION_MARK, GDE.STRING_QUESTION_MARK, GDE.STRING_QUESTION_MARK}; 
		byte[] dataBuffer = null;
		int[] points = new int[5];

		this.serialPort.isInterruptedByUser = false;
		if (log.isLoggable(Level.TIME)) log.logp(Level.TIME, GathererThread.$CLASS_NAME, $METHOD_NAME, "====> entry initial time step ms = " + this.device.getTimeStep_ms()); //$NON-NLS-1$

		try {
			this.serialPort.cleanInputStream();
			deviceIdentifier = this.serialPort.getIDN(); //ET5410 09411830014 V1.02.1806.028 V1.10.1806.012
			if (!this.serialPort.getMode().equals("BATT")) {
				this.application.openMessageDialogAsync(Messages.getString(MessageIds.GDE_MSGW1703));
				this.serialPort.isInterruptedByUser = true;
			}
		}
		catch (Exception e) {
			log.log(java.util.logging.Level.SEVERE, e.getMessage(), e);
		}
		startCycleTime = lastTmpCycleTime = System.currentTimeMillis();
		while (!this.serialPort.isInterruptedByUser) {
			try {
				if (this.application != null) this.application.setPortConnected(true);
				for (int i = 0; i < device.getChannelCount(); i++) {
					if (this.serialPort.isInterruptedByUser) 
						break;
					//check if device is processing
					if (!this.serialPort.isProcessing()) {
						this.application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI1702));
						WaitTimer.delay(1000);
						continue;
					}
					// get data from device
					dataBuffer = this.serialPort.getData();
					if (log.isLoggable(Level.FINE)) log.log(Level.FINE, new String(dataBuffer));
					// check if device is ready for data capturing else wait for 180 seconds max. for actions
					//TODO this.parser.parse(new String(dataBuffer), 42);					
					try {
						this.channelNumber = this.application.getActiveChannelNumber();
						this.stateNumber = 1;
						if (log.isLoggable(Level.FINE)) log.logp(Level.FINE, GathererThread.$CLASS_NAME, $METHOD_NAME,	device.getChannelCount() + " - data for channel = " + channelNumber + " state = " + stateNumber);
						if (this.channelNumber > device.getChannelCount()) 
							continue; //skip data if not configured
						this.channel = this.channels.get(this.channelNumber);
	
						PropertyType stateProperty = this.device.getStateProperty(this.stateNumber);
						if (stateProperty != null) 
							processName = this.device.getRecordSetStateNameReplacement(this.stateNumber);
						else 
							throw new DevicePropertiesInconsistenceException(Messages.getString(MessageIds.GDE_MSGW1702, new Object[] {this.stateNumber}));
						
						if (log.isLoggable(Level.FINER)) log.logp(Level.FINER, GathererThread.$CLASS_NAME, $METHOD_NAME,	"processing mode = " + processName ); //$NON-NLS-1$
						channelRecordSet = this.channel.get(this.channel.getLastActiveRecordSetName());
					}
					catch (Exception e) {
						log.log(Level.WARNING, e.getMessage(), e);
						if (e instanceof DevicePropertiesInconsistenceException) {
							this.application.openMessageDialogAsync(e.getMessage());
							throw e;
						}
						break;
					}

					// check if a record set matching for re-use is available and prepare a new if required
					if (this.channel.size() == 0 || channelRecordSet == null || !this.recordSetKey.endsWith(GDE.STRING_BLANK + processName)) { //$NON-NLS-1$
						this.application.setStatusMessage(GDE.STRING_EMPTY);
						setRetryCounter(GathererThread.WAIT_TIME_RETRYS); // 36 * receive timeout sec timeout = 180 sec
						// record set does not exist or is outdated, build a new name and create, in case of ChannelTypes.TYPE_CONFIG try sync with channel number
						this.recordSetKey = (device.recordSetNumberFollowChannel() && this.channel.getType() == ChannelTypes.TYPE_CONFIG ? this.channel.getNextRecordSetNumber(this.channelNumber) : this.channel.getNextRecordSetNumber())	
								+ GDE.STRING_RIGHT_PARENTHESIS_BLANK + processName;
						this.channel.put(this.recordSetKey, RecordSet.createRecordSet(this.recordSetKey, this.application.getActiveDevice(), channel.getNumber(), true, false, true));
						if (log.isLoggable(Level.FINE)) log.logp(Level.FINE, GathererThread.$CLASS_NAME, $METHOD_NAME, this.recordSetKey + " created for channel " + this.channel.getName()); //$NON-NLS-1$
						if (this.channel.getActiveRecordSet() == null) 
							this.channel.setActiveRecordSet(this.recordSetKey);
						channelRecordSet = this.channel.get(this.recordSetKey);
						channelRecordSet.setRecordSetDescription(String.format("%s \nS/N:%s FW:%s HW:%s", channelRecordSet.getDescription()
								,deviceIdentifier[1], deviceIdentifier[2], deviceIdentifier[3]));
						
						if (this.channel.getType() == ChannelTypes.TYPE_CONFIG)
							this.channel.applyTemplate(this.recordSetKey, false);
						else 
							this.channel.applyTemplateBasics(this.recordSetKey);
						
						if (this.channel.getName().equals(this.channels.getActiveChannel().getName())) {
							this.channels.getActiveChannel().switchRecordSet(this.recordSetKey);
						}
						this.application.getMenuToolBar().updateRecordSetSelectCombo();
						this.channel.get(this.recordSetKey).updateVisibleAndDisplayableRecordsForTable();
						measurementCount = 0;
						startCycleTime = 0;
						points = new int[channelRecordSet.size()];
					}
					// prepare the data for adding to record set
					tmpCycleTime = System.currentTimeMillis();
					if (measurementCount++ == 0) {
						startCycleTime = tmpCycleTime;
					}

					if (channelRecordSet != null) {
						if (this.serialPort.isInterruptedByUser) break;
						this.device.convertDataBytes(points, dataBuffer);
						points[2] = this.serialPort.getCapacity(points[2]);
						if (points.length == channelRecordSet.size()) 
							channelRecordSet.addPoints(points, (tmpCycleTime - startCycleTime));
						else
							this.application.setStatusMessage(String.format("Miss match record set size = %d to parsed values length = %d, please correct!", channelRecordSet.size(), points.length), SWT.COLOR_RED);
							
						if (log.isLoggable(Level.FINER)) log.logp(Level.TIME, GathererThread.$CLASS_NAME, $METHOD_NAME, "time after add = " + TimeLine.getFomatedTimeWithUnit(tmpCycleTime - startCycleTime)); //$NON-NLS-1$
						if (measurementCount > 0 && measurementCount % 10 == 0) {
							this.activeChannel = this.channels.getActiveChannel();
							if (activeChannel != null) {
								this.activeRecordSet = activeChannel.getActiveRecordSet();
								if (activeRecordSet != null) this.device.updateVisibilityStatus(channelRecordSet, true);
							}
						}
					}
					this.application.updateAllTabs(false);
				}
				if (deviceTimeStep_ms > 0) { //time step is constant
					delayTime = (long) (deviceTimeStep_ms - (System.currentTimeMillis() - lastTmpCycleTime));
					if (delayTime > 0) {
						WaitTimer.delay(delayTime);
					}
					if (log.isLoggable(Level.TIME)) log.logp(Level.TIME, GathererThread.$CLASS_NAME, $METHOD_NAME, "delayTime = " + TimeLine.getFomatedTimeWithUnit(delayTime)); //$NON-NLS-1$
					lastTmpCycleTime = System.currentTimeMillis();;
				}
				if (log.isLoggable(Level.TIME)) log.logp(Level.TIME, GathererThread.$CLASS_NAME, $METHOD_NAME, "time = " + TimeLine.getFomatedTimeWithUnit(lastTmpCycleTime - startCycleTime)); //$NON-NLS-1$
			}
			catch (DataInconsitsentException e) {
				log.log(Level.WARNING, e.getMessage(), e);
			}
			catch (Throwable e) {
				if (!this.serialPort.isInterruptedByUser) {
					// this case will be reached while eStation program is started, checked and the check not asap committed, stop pressed
					if (e instanceof TimeOutException) {
						if (measurementCount > 0) {
							this.application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGW1701));
							log.logp(Level.FINE, GathererThread.$CLASS_NAME, $METHOD_NAME, "timeout!"); //$NON-NLS-1$
						}
						else {
							this.application.setStatusMessage(Messages.getString(MessageIds.GDE_MSGI1702));
							log.logp(Level.FINE, GathererThread.$CLASS_NAME, $METHOD_NAME, "wait for activation ..."); //$NON-NLS-1$
							if (0 == (setRetryCounter(getRetryCounter() - 1))) {
								log.log(Level.FINE, "activation timeout"); //$NON-NLS-1$
								this.application.openMessageDialogAsync(Messages.getString(MessageIds.GDE_MSGW1700));
								stopDataGatheringThread(false, null);
							}
						}
					}
					// program end or unexpected exception occurred, stop data gathering to enable save data by user
					else {
						log.log(Level.FINE, "program end detected"); //$NON-NLS-1$
						stopDataGatheringThread(true, e);
					}
				}
			}
		}
		if (this.application != null) {
			this.application.setPortConnected(false);
			this.application.setStatusMessage(""); //$NON-NLS-1$
		}
		log.logp(Level.FINE, GathererThread.$CLASS_NAME, $METHOD_NAME, "======> exit"); //$NON-NLS-1$
	}

	/**
	 * stop the data gathering and check if reasonable data in record set to finalize or clear
	 * @param enableEndMessage
	 * @param throwable
	 */
	void stopDataGatheringThread(boolean enableEndMessage, Throwable throwable) {
		final String $METHOD_NAME = "stopDataGatheringThread"; //$NON-NLS-1$

		if (throwable != null) {
			log.logp(Level.WARNING, $CLASS_NAME, $METHOD_NAME, throwable.getMessage(), throwable);
		}
		
		this.serialPort.isInterruptedByUser = true;

		try {
			for (int i = 1; i <= this.channels.size(); i++) {
				RecordSet recordSet = this.channels.get(i).get(this.channels.get(i).getLastActiveRecordSetName());
				if (recordSet != null && recordSet.getRecordDataSize(true) > 5) { // some other exception while program execution, record set has data points
					finalizeRecordSet(recordSet, false);
					if (enableEndMessage) 
						this.application.openMessageDialog(Messages.getString(gde.device.easttester.MessageIds.GDE_MSGT1709));
				}
			}
		}
		finally {
			if (this.serialPort != null && this.serialPort.getXferErrors() > 0) {
				log.log(Level.WARNING, "During complete data transfer " + this.serialPort.getXferErrors() + " number of errors occured!"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			if (this.serialPort != null && this.serialPort.isConnected() && this.isPortOpenedByLiveGatherer == true && this.serialPort.isConnected()) {
				this.serialPort.close();
			}
		}		
	}

	/**
	 * close port, set isDisplayable according channel configuration and calculate slope
	 */
	void finalizeRecordSet(RecordSet tmpRecordSet, boolean doClosePort) {
		if (doClosePort && this.isPortOpenedByLiveGatherer && this.serialPort.isConnected()) this.serialPort.close();

		if (tmpRecordSet != null) {
			this.device.updateVisibilityStatus(tmpRecordSet, true);
			this.device.makeInActiveDisplayable(tmpRecordSet);
			this.application.updateStatisticsData();
			this.application.updateDataTable(this.recordSetKey, false);
			
			this.device.setAverageTimeStep_ms(tmpRecordSet.getAverageTimeStep_ms());
			log.log(Level.TIME, "set average time step msec = " + this.device.getAverageTimeStep_ms());
		}
	}

	/**
	 * cleanup all allocated resources and display the message
	 * @param message
	 */
	void cleanup(final String message) {
		for (int i = 1; i <= this.channels.size(); i++) {
			RecordSet recordSet = this.channels.get(i).get(this.channels.get(i).getLastActiveRecordSetName());
			if (recordSet != null) {
				recordSet.clear();
				this.channel.remove(recordSet.getName());
				if (Thread.currentThread().threadId() == this.application.getThreadId()) {
					this.application.getMenuToolBar().updateRecordSetSelectCombo();
					this.application.updateStatisticsData();
					this.application.updateDataTable(this.recordSetKey, true);
					this.application.openMessageDialog(message);
				}
				else {
					final String useRecordSetKey = this.recordSetKey;
					GDE.display.asyncExec(new Runnable() {
						public void run() {
							GathererThread.this.application.getMenuToolBar().updateRecordSetSelectCombo();
							GathererThread.this.application.updateStatisticsData();
							GathererThread.this.application.updateDataTable(useRecordSetKey, true);
							GathererThread.this.application.openMessageDialog(message);
						}
					});
				}
			}
			else
				this.application.openMessageDialog(message);
		}
	}

	/**
	 * @param enabled the isCollectDataStopped to set
	 */
	void setCollectDataStopped(boolean enabled) {
		this.serialPort.isInterruptedByUser = enabled;
	}

	/**
	 * @return the isCollectDataStopped
	 */
	boolean isCollectDataStopped() {
		return this.serialPort.isInterruptedByUser;
	}

	/**
	 * @return the retryCounter
	 */
	int getRetryCounter() {
		return this.retryCounter;
	}

	/**
	 * @param newRetryCounter the retryCounter to set
	 */
	int setRetryCounter(int newRetryCounter) {
		return this.retryCounter = newRetryCounter;
	}

}
