package gde.junit;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.swt.SWTException;
import org.junit.Test;

import gde.config.Settings;
import gde.device.tasi.TA612CTests;

public class TestTasiTC612C extends TestSuperClass {
	
	static Logger						log				= Logger.getLogger(TestTasiTC612C.class.getName());


	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	public void setUp() throws Exception {
		super.setUp();
		Settings.getInstance().setPartialDataTable(true);
	}


	@Test
	public void testTA612CTests() throws Exception {
		try {
			TA612CTests.main(new String[] {});
			TA612CTests.main(new String[] {"/Users/brueg/git/dataexplorer/Tasi/src/resource/TA612C.xml",
					"/Users/brueg/Library/Application Support/DataExplorer/Devices/DeviceProperties_V50.xsd",
					"/Users/brueg/git/dataexplorer/Tasi/test-output",
					"/Users/brueg/git/dataexplorer/Tasi/build/Tasi.jar"});
		}
		catch (Exception e) {
			if (e instanceof SWTException) {// workaround junit does not have a active shell -> GDE.display.getActiveShell() == null
				log.log(Level.WARNING, e.getMessage());
				return;
			}
			throw e;
		}
	}
	

}
