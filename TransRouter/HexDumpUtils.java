package com.efx.tps;

public class HexDumpUtils {

	public static String formatHexRecord(byte[] bytes, int offset, int sz) {
		StringBuilder builder = new StringBuilder();

		for (int index = offset; index < sz; index++) {
			int value = bytes[index] & 0x00FF;
			builder.append(String.format("%02x ", value));
		}
		// line seperator
		// builder.append(String.format("%n"));
		return builder.toString();
	}

	public static String formatHexDump(byte[] bytes, int offset, int sz,
			int width) {

		StringBuilder builder = new StringBuilder();

		for (int row_offset = offset; row_offset < offset
				+ sz; row_offset += width) {

			builder.append(String.format("%06d: ", row_offset));

			StringBuilder ascii = new StringBuilder();

			// row of hex digits of specified width followed by ascii field...
			// non-printable characters are output as '.'
			for (int index = 0; index < width; index++) {
				if (row_offset + index < sz) {
					int value = bytes[row_offset + index] & 0x00FF;
					builder.append(String.format("%02x ", value));

					if (value < 0x20 || value > 0x7e) {
						ascii.append('.');
					} else {
						ascii.append((char) value);
					}

				} else {
					builder.append("   ");
				}
			}

			if (row_offset < sz) {
				builder.append(" | ");
				builder.append(ascii);
			}

			// line seperator
			builder.append(String.format("%n"));
		}
		return builder.toString();
	}	
	
}
