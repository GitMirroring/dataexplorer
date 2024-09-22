/*
 * Copyright (C) 2014 Klaus Reimer <k@ailis.de>
 * See LICENSE.txt for licensing information.
 */
package gde.comm;

import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

/**
 * Simply lists all available USB devices.
 * 
 * @author Klaus Reimer <k@ailis.de>
 */
public class ClaimTestLibUsb
{
	final static short idVendor = 0x0000;
	final static short idProduct = 0x0001;
	final static byte interfaceId = 0x01;

    /**
     * Main method.
     * 
     * @param args
     *            Command-line arguments (Ignored)
     */
    public static void main(String[] args)
    {
        // Create the libusb context
        Context context = new Context();

        // Initialize the libusb context
        int result = LibUsb.init(context);
        if (result < 0)
        {
            throw new LibUsbException("Unable to initialize libusb", result);
        }

        // Read the USB device list
        DeviceList list = new DeviceList();
        result = LibUsb.getDeviceList(context, list);
        if (result < 0)
        {
            throw new LibUsbException("Unable to get device list", result);
        }

        try
        {
            // Iterate over all devices and list them
            for (Device device: list)
            {
                DeviceDescriptor descriptor = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(device, descriptor);
                if (result < 0)
                {
                    throw new LibUsbException(
                        "Unable to read device descriptor", result);
                }
                int address = LibUsb.getDeviceAddress(device);
                int busNumber = LibUsb.getBusNumber(device);
                System.out.format(
                    "Bus %03d, Device %03d: Vendor %04x, Product %04x%n",
                    busNumber, address, descriptor.idVendor(), descriptor.idProduct());
                
                if (descriptor.idVendor() == idVendor && descriptor.idProduct() == idProduct) {
              		DeviceHandle handle = new DeviceHandle();
              		result = LibUsb.open(device, handle);
              		if (result < 0) {
              			System.out.println(String.format("Unable to open device: %s. " + "Continuing without device handle.", LibUsb.strError(result)));
              			handle = null;
              		}
              		
              		result = LibUsb.detachKernelDriver(handle, interfaceId);
                  if (result != LibUsb.SUCCESS && 
                  		result != LibUsb.ERROR_NOT_SUPPORTED && 
                  				result != LibUsb.ERROR_NOT_FOUND) 
                  	System.out.println(new LibUsbException("Unable to detach kernel driver", result));

                  result = LibUsb.claimInterface(handle, interfaceId);
                  if (result < 0)
                  {
                      System.out.println(new LibUsbException(
                          String.format("Unable to claim interface 0x%02X", interfaceId), result).getMessage());
                  }
                  else {
                  	System.out.println(">>> interface claimed successful!");
                  	result = LibUsb.releaseInterface(handle, interfaceId);
                    if (result < 0)
                    {
                        System.out.println(new LibUsbException(
                            String.format("Unable to release interface 0x%02X", interfaceId), result).getMessage());
                    }
                  }

              		// Close the device if it was opened
              		if (handle != null) {
              			LibUsb.close(handle);
              		}
                }
            }
        }
        finally
        {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(list, true);
        }

        // Deinitialize the libusb context
        LibUsb.exit(context);
    }
}