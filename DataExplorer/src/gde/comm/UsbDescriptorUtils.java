/**
 * content copied from usb4java DescriptorUtils
 */
package gde.comm;

import java.util.HashMap;
import java.util.Map;

import javax.usb.UsbDeviceDescriptor;

import org.usb4java.LibUsb;

public class UsbDescriptorUtils {

	public UsbDescriptorUtils() {
		// TODO Auto-generated constructor stub
	}
	
  /** Mapping from USB class id to USB class name. */
  private static final Map<Byte, String> CLASS_NAMES =
      new HashMap<Byte, String>();

  static
  {
      CLASS_NAMES.put(LibUsb.CLASS_PER_INTERFACE, "Per Interface");
      CLASS_NAMES.put(LibUsb.CLASS_AUDIO, "Audio");
      CLASS_NAMES.put(LibUsb.CLASS_COMM, "Communications");
      CLASS_NAMES.put(LibUsb.CLASS_HID, "HID");
      CLASS_NAMES.put(LibUsb.CLASS_PHYSICAL, "Physical");
      CLASS_NAMES.put(LibUsb.CLASS_IMAGE, "Imaging");
      CLASS_NAMES.put(LibUsb.CLASS_PRINTER, "Printer");
      CLASS_NAMES.put(LibUsb.CLASS_MASS_STORAGE, "Mass Storage");
      CLASS_NAMES.put(LibUsb.CLASS_HUB, "Hub");
      CLASS_NAMES.put(LibUsb.CLASS_DATA, "Data");
      CLASS_NAMES.put(LibUsb.CLASS_SMART_CARD, "Smart Card");
      CLASS_NAMES.put(LibUsb.CLASS_CONTENT_SECURITY, "Content Security");
      CLASS_NAMES.put(LibUsb.CLASS_VIDEO, "Video");
      CLASS_NAMES.put(LibUsb.CLASS_PERSONAL_HEALTHCARE, "Personal Healthcare");
      CLASS_NAMES.put(LibUsb.CLASS_DIAGNOSTIC_DEVICE, "Diagnostic Device");
      CLASS_NAMES.put(LibUsb.CLASS_WIRELESS, "Wireless");
      CLASS_NAMES.put(LibUsb.CLASS_APPLICATION, "Application");
      CLASS_NAMES.put(LibUsb.CLASS_VENDOR_SPEC, "Vendor-specific");
  }


  /**
   * Decodes a binary-coded decimal into a string and returns it.
   *
   * @param bcd
   *            The binary-coded decimal to decode.
   * @return The decoded binary-coded decimal.
   */
  public static String decodeBCD(final short bcd)
  {
      return String.format("%x.%02x", (bcd & 0xFF00) >> 8, bcd & 0x00FF);
  }

  /**
   * Returns the name of the specified USB class. "unknown" is returned for a
   * class which is unknown to libusb.
   *
   * @param usbClass
   *            The numeric USB class.
   * @return The USB class name.
   */
  public static String getUSBClassName(final byte usbClass)
  {
      final String name = CLASS_NAMES.get(usbClass);

      if (name == null)
      {
          return "Unknown";
      }

      return name;
  }


  /**
   * Dumps the specified USB device descriptor into a string and returns it.
   *
   * @param descriptor
   *            The USB device descriptor to dump.
   * @param manufacturer
   *            The manufacturer string or null if unknown.
   * @param product
   *            The product string or null if unknown.
   * @param serial
   *            The serial number string or null if unknown.
   * @return The descriptor dump.
   */
  public static String dump(final UsbDeviceDescriptor descriptor,
      final String manufacturer, final String product, final String serial)
  {
      return String.format(
          "Device Descriptor:%n" +
          "  bLength %18d%n" +
          "  bDescriptorType %10d%n" +
          "  bcdUSB %19s%n" +
          "  bDeviceClass %13d %s%n" +
          "  bDeviceSubClass %10d%n" +
          "  bDeviceProtocol %10d%n" +
          "  bMaxPacketSize0 %10d%n" +
          "  idVendor %17s%n" +
          "  idProduct %16s%n" +
          "  bcdDevice %16s%n" +
          "  iManufacturer %12d%s%n" +
          "  iProduct %17d%s%n" +
          "  iSerial %18d%s%n" +
          "  bNumConfigurations %7d%n",
          descriptor.bLength(),
          descriptor.bDescriptorType(),
          decodeBCD(descriptor.bcdUSB()),
          descriptor.bDeviceClass() & 0xff,
          getUSBClassName(descriptor.bDeviceClass()),
          descriptor.bDeviceSubClass() & 0xff,
          descriptor.bDeviceProtocol() & 0xff,
          descriptor.bMaxPacketSize0() & 0xff,
          String.format("0x%04x", descriptor.idVendor() & 0xffff),
          String.format("0x%04x", descriptor.idProduct() & 0xffff),
          decodeBCD(descriptor.bcdDevice()),
          descriptor.iManufacturer() & 0xff,
          (manufacturer == null) ? "" : (" " + manufacturer),
          descriptor.iProduct() & 0xff,
          (product == null) ? "" : (" " + product),
          descriptor.iSerialNumber() & 0xff,
          (serial == null) ? "" : (" " + serial),
          descriptor.bNumConfigurations() & 0xff);
  }

}
