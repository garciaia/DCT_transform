// Version 1.0a
// Copyright (C) 1998, James R. Weeks and BioElectroMech.
// Visit BioElectroMech at www.obrador.com.  Email James@obrador.com.

// See license.txt for details about the allowed used of this software.
// This software is based in part on the work of the Independent JPEG Group.
// See IJGreadme.txt for details about the Independent JPEG Group's license.

// This encoder is inspired by the Java Jpeg encoder by Florian Raemy,
// studwww.eurecom.fr/~raemy.
// It borrows a great deal of code and structure from the Independent
// Jpeg Group's Jpeg 6a library, Copyright Thomas G. Lane.
// See license.txt for details.

import java.awt.AWTException;
import java.awt.Image;
import java.awt.image.PixelGrabber;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class JpegEncoder {
	/**
	 * Defines the chroma subsampling schemes available.
	 */
	public enum Subsampling {
		YUV_444, // 4:4:4
		YUV_422, // 4:2:2
		YUV_420 // 4:2:0
	}

	// The image to be compressed.
	private Image image;
	// The quality setting (1-100) for the compression.
	private int quality;
	// The output stream where the JPEG data will be written.
	private BufferedOutputStream outStream;

	// Helper classes that handle specific parts of the JPEG process.
	private JpegInfo jpegInfo;
	private DCT dct;
	private Quantizer quantizer;
	private Huffman Huffman;

	/*
	 * JpegEncoder - The JPEG main program which performs a jpeg compression of
	 * an image.
	 */
	public JpegEncoder(Image image, int quality, OutputStream outStream, Subsampling subsampling) {
		// The image to be encoded
		this.image = image;

		// The compression quality (1-100)
		// 1 -> good compression, bad quality,
		// 100 -> bad compression, good quality
		this.quality = quality;

		// The output stream to write the JPEG
		this.outStream = new BufferedOutputStream(outStream);

		// Initialize the helper components
		this.jpegInfo = new JpegInfo(image, subsampling);
		this.dct = new DCT();
		this.quantizer = new Quantizer(quality);
		this.Huffman = new Huffman();
	}

	/**
	 * The main method to start the compression process.
	 */
	public void compress() {
		writeHeaders();
		writeCompressedData();

		// Write the End of Image marker.
		try {
			outStream.write(new byte[] { (byte) 0xFF, (byte) 0xD9 });
		} catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}

		try {
			outStream.flush();
		} catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}
	}

	/**
	 * Writes all the necessary JPEG headers to the output stream.
	 * These headers define the structure and parameters of the image.
	 */
	private void writeHeaders() {
		try {
			// Start of Image
			outStream.write(new byte[] { (byte) 0xFF, (byte) 0xD8 });

			// JFIF Header
			byte[] jfif = {
					(byte) 0xff, (byte) 0xe0, (byte) 0x00, (byte) 0x10,
					(byte) 0x4a, (byte) 0x46, (byte) 0x49, (byte) 0x46,
					(byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00,
					(byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01,
					(byte) 0x00, (byte) 0x00
			};
			outStream.write(jfif);
		} catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}

		// write DQT
		writeDQT();

		// write Start of Frame
		writeSOF();

		// write DHT
		writeDHT();

		// write Start of Scan
		writeSOS();
	}

	/**
	 * Quantization tables segment.
	 * These tables are used to quantize the DCT coefficients
	 */
	private void writeDQT() {
		try {
			byte[] DQT = {
					(byte) 0xFF, (byte) 0xDB,
					(byte) 0x00, (byte) 0x84
			};
			outStream.write(DQT);

			// Luminance quantization table.
			outStream.write(0x00);
			for (int i = 0; i < 64; i++) {
				outStream.write(quantizer.getQuantumLuminance()[JpegInfo.JPEG_NATURAL_ORDER[i]]);
			}

			// Chrominance quantization table.
			outStream.write(0x01);
			for (int i = 0; i < 64; i++) {
				outStream.write(quantizer.getQuantumChrominance()[JpegInfo.JPEG_NATURAL_ORDER[i]]);
			}
		} catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}
	}

	/**
	 * Start of Frame segment
	 * This header specifies the image dimensions, number of components, and their
	 * properties.
	 */
	private void writeSOF() {
		try {
			byte ySample = (byte) ((jpegInfo.HsampleFactor[0] << 4) | jpegInfo.VsampleFactor[0]);
			byte cSample = (byte) ((jpegInfo.HsampleFactor[1] << 4) | jpegInfo.VsampleFactor[1]);

			byte[] sof = {
					(byte) 0xFF, (byte) 0xC0,
					(byte) 0x00, (byte) 17,
					(byte) 8,
					(byte) ((jpegInfo.imageHeight >> 8) & 0xFF),
					(byte) (jpegInfo.imageHeight & 0xFF),
					(byte) ((jpegInfo.imageWidth >> 8) & 0xFF),
					(byte) (jpegInfo.imageWidth & 0xFF),
					(byte) 3,
					// Component data for each of the 3 components
					(byte) 1, ySample, (byte) 0, // Y
					(byte) 2, cSample, (byte) 1, // Cb
					(byte) 3, cSample, (byte) 1, // Cr
			};
			outStream.write(sof);
		} catch (IOException e) {

		}
	}

	/**
	 * Huffman Tables segment.
	 * This defines the codes for both DC and AC
	 * coefficients for both luminance and chrominance.
	 */
	private void writeDHT() {
		// DC Luminance
		writeDHTTable(Huffman.getDcLuminanceBits(), Huffman.getDcLuminanceVal(), 0, true);
		// AC Luminance
		writeDHTTable(Huffman.getAcLuminanceBits(), Huffman.getAcLuminanceVal(), 0, false);
		// DC Chrominance
		writeDHTTable(Huffman.getDcChrominanceBits(), Huffman.getDcChrominanceVal(), 1, true);
		// AC Chrominance
		writeDHTTable(Huffman.getAcChrominanceBits(), Huffman.getAcChrominanceVal(), 1, false);
	}

	/**
	 * Helper method to write a Huffman table to the output stream.
	 */
	private void writeDHTTable(int[] bits, int[] values, int tableID, boolean isDC) {
		try {
			int length = 2 + 17 + values.length;
			outStream.write((byte) 0xFF);
			outStream.write((byte) 0xC4);
			outStream.write((byte) ((length >> 8) & 0xFF));
			outStream.write((byte) (length & 0xFF));
			outStream.write((isDC ? 0x00 : 0x10) | tableID);
			for (int bit : bits) {
				outStream.write(bit);
			}
			for (int value : values) {
				outStream.write(value);
			}
		} catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}
	}

	/**
	 * Start of Scan segment
	 * Indicates the beginning of the compressed image data itself
	 */
	private void writeSOS() {
		try {
			byte[] sos = {
					(byte) 0xFF, (byte) 0xDA,
					(byte) 0x00, (byte) 12,
					(byte) 0x03,
					(byte) 1, (byte) 0x00,
					(byte) 2, (byte) 0x11,
					(byte) 3, (byte) 0x11,
					(byte) 0x00, (byte) 0x3F, (byte) 0x00
			};
			outStream.write(sos);
		} catch (IOException e) {
			System.out.println("IO Error: " + e.getMessage());
		}
	}

	/**
	 * Writes the compressed bitstream to the output.
	 */

	private void writeCompressedData() {
		int[] lastDcValue = new int[3];
		BitStream bitStream = new BitStream(outStream);

		int blockWidth = 8 * jpegInfo.MaxHsampleFactor;
		int blockHeight = 8 * jpegInfo.MaxVsampleFactor;

		// Iterate over the image
		for (int y = 0; y < jpegInfo.paddedHeight; y += blockHeight) {
			for (int x = 0; x < jpegInfo.paddedWidth; x += blockWidth) {

				// Process Y components for this block
				for (int i = 0; i < jpegInfo.VsampleFactor[0]; i++) {
					for (int j = 0; j < jpegInfo.HsampleFactor[0]; j++) {
						double[][] yBlock = getBlock(jpegInfo.y, x + j * 8, y + i * 8);
						int[] quantizedY = quantizer.quantizeBlock(dct.forwardDCT(yBlock), true);
						lastDcValue[0] = Huffman.encodeBlock(bitStream, quantizedY, lastDcValue[0], true);
					}
				}

				// Process Cb component(s) for this block
				for (int i = 0; i < jpegInfo.VsampleFactor[1]; i++) {
					for (int j = 0; j < jpegInfo.HsampleFactor[1]; j++) {
						double[][] cbBlock = getBlock(jpegInfo.cb, (x / jpegInfo.MaxHsampleFactor) + j * 8,
								(y / jpegInfo.MaxVsampleFactor) + i * 8);
						int[] quantizedCb = quantizer.quantizeBlock(dct.forwardDCT(cbBlock), false);
						lastDcValue[1] = Huffman.encodeBlock(bitStream, quantizedCb, lastDcValue[1], false);
					}
				}

				// Process Cr component(s) for this block
				for (int i = 0; i < jpegInfo.VsampleFactor[2]; i++) {
					for (int j = 0; j < jpegInfo.HsampleFactor[2]; j++) {
						double[][] crBlock = getBlock(jpegInfo.cr, (x / jpegInfo.MaxHsampleFactor) + j * 8,
								(y / jpegInfo.MaxVsampleFactor) + i * 8);
						int[] quantizedCr = quantizer.quantizeBlock(dct.forwardDCT(crBlock), false);
						lastDcValue[2] = Huffman.encodeBlock(bitStream, quantizedCr, lastDcValue[2], false);
					}
				}
			}
		}
		bitStream.flush(); // Write any remaining bits in the buffer.
	}

	/**
	 * Extracts an 8x8 block of data from a 2D float array.
	 */
	private double[][] getBlock(float[][] component, int x, int y) {
		double[][] block = new double[8][8];
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				block[i][j] = component[y + i][x + j];
			}
		}
		return block;
	}

	/*
	 * JpegInfo - Given an image, sets default information about it and divides
	 * it into its constituant components, downsizing those that need to be.
	 */
	static class JpegInfo {
		int imageWidth;
		int imageHeight;
		int paddedWidth;
		int paddedHeight;

		// Sampling factors for each component (Y, Cb, Cr)
		int[] HsampleFactor = new int[3];
		int[] VsampleFactor = new int[3];
		int MaxHsampleFactor;
		int MaxVsampleFactor;

		// 2D arrays to hold the Y, Cb, and Cr components of the image.
		float[][] y;
		float[][] cb;
		float[][] cr;

		public static int[] JPEG_NATURAL_ORDER = {
				0, 1, 8, 16, 9, 2, 3, 10,
				17, 24, 32, 25, 18, 11, 4, 5,
				12, 19, 26, 33, 40, 48, 41, 34,
				27, 20, 13, 6, 7, 14, 21, 28,
				35, 42, 49, 56, 57, 50, 43, 36,
				29, 22, 15, 23, 30, 37, 44, 51,
				58, 59, 52, 45, 38, 31, 39, 46,
				53, 60, 61, 54, 47, 55, 62, 63,
		};

		JpegInfo(Image image, Subsampling subsampling) {
			this.imageWidth = image.getWidth(null);
			this.imageHeight = image.getHeight(null);

			// Set sampling factors based on the chosen scheme
			switch (subsampling) {
				case YUV_420:
					HsampleFactor[0] = 2;
					HsampleFactor[1] = 1;
					HsampleFactor[2] = 1;
					VsampleFactor[0] = 2;
					VsampleFactor[1] = 1;
					VsampleFactor[2] = 1;
					break;
				case YUV_422:
					HsampleFactor[0] = 2;
					HsampleFactor[1] = 1;
					HsampleFactor[2] = 1;
					VsampleFactor[0] = 1;
					VsampleFactor[1] = 1;
					VsampleFactor[2] = 1;
					break;
				case YUV_444:
				default:
					HsampleFactor[0] = 1;
					HsampleFactor[1] = 1;
					HsampleFactor[2] = 1;
					VsampleFactor[0] = 1;
					VsampleFactor[1] = 1;
					VsampleFactor[2] = 1;
					break;
			}

			MaxHsampleFactor = HsampleFactor[0];
			MaxVsampleFactor = VsampleFactor[0];

			this.paddedWidth = (imageWidth + (8 * MaxHsampleFactor - 1)) & ~(8 * MaxHsampleFactor - 1);
			this.paddedHeight = (imageHeight + (8 * MaxVsampleFactor - 1)) & ~(8 * MaxVsampleFactor - 1);

			convertToYCbCr(image);
		}

		/**
		 * Converts RGB to YCbCr, pads the image, and performs chroma subsampling.
		 */
		private void convertToYCbCr(Image image) {
			// Grab pixels from the source image
			int[] pixels = new int[imageWidth * imageHeight];
			PixelGrabber grabber = new PixelGrabber(image, 0, 0, imageWidth, imageHeight, pixels, 0, imageWidth);
			try {
				if (grabber.grabPixels() != true) {
					try {
						throw new AWTException("Grabber returned false: " + grabber.status());
					} catch (Exception e) {
					}
					;
				}
			} catch (InterruptedException e) {
			}
			;

			float[][] fullY = new float[paddedHeight][paddedWidth];
			float[][] fullCb = new float[paddedHeight][paddedWidth];
			float[][] fullCr = new float[paddedHeight][paddedWidth];

			// Convert RGB pixels to YCbCr
			for (int row = 0; row < imageHeight; row++) {
				for (int col = 0; col < imageWidth; col++) {
					int pixel = pixels[row * imageWidth + col];
					int r = (pixel >> 16) & 0xff;
					int g = (pixel >> 8) & 0xff;
					int b = pixel & 0xff;

					// The following three lines are a more correct color conversion but
					// the current conversion technique is sufficient and results in a higher
					// compression rate.

					// Y[y][x] = 16 + (float)(0.8588*(0.299 * (float)r + 0.587 * (float)g + 0.114 *
					// (float)b ));

					// Cb1[y][x] = 128 + (float)(0.8784*(-0.16874 * (float)r - 0.33126 * (float)g +
					// 0.5 * (float)b));

					// Cr1[y][x] = 128 + (float)(0.8784*(0.5 * (float)r - 0.41869 * (float)g -
					// 0.08131 * (float)b));
					fullY[row][col] = (float) (0.299 * r + 0.587 * g + 0.114 * b);
					fullCb[row][col] = (float) (128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
					fullCr[row][col] = (float) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);
				}
			}

			// Handle image padding
			for (int row = 0; row < paddedHeight; row++) {
				for (int col = 0; col < paddedWidth; col++) {
					if (row >= imageHeight || col >= imageWidth) {
						int srcRow = Math.min(row, imageHeight - 1);
						int srcCol = Math.min(col, imageWidth - 1);
						fullY[row][col] = fullY[srcRow][srcCol];
						fullCb[row][col] = fullCb[srcRow][srcCol];
						fullCr[row][col] = fullCr[srcRow][srcCol];
					}
				}
			}
			this.y = fullY;

			// Perform downsampling
			if (MaxHsampleFactor > 1 || MaxVsampleFactor > 1) {
				this.cb = downsample(fullCb, MaxHsampleFactor, MaxVsampleFactor);
				this.cr = downsample(fullCr, MaxHsampleFactor, MaxVsampleFactor);
			} else {
				this.cb = fullCb;
				this.cr = fullCr;
			}
		}

		/**
		 * Downsamples a color component
		 */
		private float[][] downsample(float[][] C, int hFactor, int vFactor) {
			int newWidth = C[0].length / hFactor;
			int newHeight = C.length / vFactor;
			float[][] output = new float[newHeight][newWidth];
			for (int y = 0; y < newHeight; y++) {
				for (int x = 0; x < newWidth; x++) {
					float sum = 0;
					for (int i = 0; i < vFactor; i++) {
						for (int j = 0; j < hFactor; j++) {
							sum += C[y * vFactor + i][x * hFactor + j];
						}
					}
					output[y][x] = sum / (hFactor * vFactor);
				}
			}
			return output;
		}
	}

	/*
	 * DCT - A Java implementation of the Discreet Cosine Transform
	 */
	static class DCT {
		DCT() {
		}

		/**
		 * Performs a forward DCT using the AAN algorithm.
		 */
		public double[][] forwardDCT(double[][] input) {
			double[][] output = new double[8][8];
			double tmp0, tmp1, tmp2, tmp3, tmp4, tmp5, tmp6, tmp7;
			double tmp10, tmp11, tmp12, tmp13;
			double z1, z2, z3, z4, z5, z11, z13;
			int i, j;

			// Subtracts 128 from the input values
			for (i = 0; i < 8; i++) {
				for (j = 0; j < 8; j++) {
					output[i][j] = (input[i][j] - 128.0);
				}
			}

			for (i = 0; i < 8; i++) {
				tmp0 = output[i][0] + output[i][7];
				tmp7 = output[i][0] - output[i][7];
				tmp1 = output[i][1] + output[i][6];
				tmp6 = output[i][1] - output[i][6];
				tmp2 = output[i][2] + output[i][5];
				tmp5 = output[i][2] - output[i][5];
				tmp3 = output[i][3] + output[i][4];
				tmp4 = output[i][3] - output[i][4];

				tmp10 = tmp0 + tmp3;
				tmp13 = tmp0 - tmp3;
				tmp11 = tmp1 + tmp2;
				tmp12 = tmp1 - tmp2;

				output[i][0] = tmp10 + tmp11;
				output[i][4] = tmp10 - tmp11;

				z1 = (tmp12 + tmp13) * (double) 0.707106781;
				output[i][2] = tmp13 + z1;
				output[i][6] = tmp13 - z1;

				tmp10 = tmp4 + tmp5;
				tmp11 = tmp5 + tmp6;
				tmp12 = tmp6 + tmp7;

				z5 = (tmp10 - tmp12) * (double) 0.382683433;
				z2 = ((double) 0.541196100) * tmp10 + z5;
				z4 = ((double) 1.306562965) * tmp12 + z5;
				z3 = tmp11 * ((double) 0.707106781);

				z11 = tmp7 + z3;
				z13 = tmp7 - z3;

				output[i][5] = z13 + z2;
				output[i][3] = z13 - z2;
				output[i][1] = z11 + z4;
				output[i][7] = z11 - z4;
			}

			for (i = 0; i < 8; i++) {
				tmp0 = output[0][i] + output[7][i];
				tmp7 = output[0][i] - output[7][i];
				tmp1 = output[1][i] + output[6][i];
				tmp6 = output[1][i] - output[6][i];
				tmp2 = output[2][i] + output[5][i];
				tmp5 = output[2][i] - output[5][i];
				tmp3 = output[3][i] + output[4][i];
				tmp4 = output[3][i] - output[4][i];

				tmp10 = tmp0 + tmp3;
				tmp13 = tmp0 - tmp3;
				tmp11 = tmp1 + tmp2;
				tmp12 = tmp1 - tmp2;

				output[0][i] = tmp10 + tmp11;
				output[4][i] = tmp10 - tmp11;

				z1 = (tmp12 + tmp13) * (double) 0.707106781;
				output[2][i] = tmp13 + z1;
				output[6][i] = tmp13 - z1;

				tmp10 = tmp4 + tmp5;
				tmp11 = tmp5 + tmp6;
				tmp12 = tmp6 + tmp7;

				z5 = (tmp10 - tmp12) * (double) 0.382683433;
				z2 = ((double) 0.541196100) * tmp10 + z5;
				z4 = ((double) 1.306562965) * tmp12 + z5;
				z3 = tmp11 * ((double) 0.707106781);

				z11 = tmp7 + z3;
				z13 = tmp7 - z3;

				output[5][i] = z13 + z2;
				output[3][i] = z13 - z2;
				output[1][i] = z11 + z4;
				output[7][i] = z11 - z4;
			}

			return output;
		}
	}

	/**
	 * Handles quantization of DCT coefficients
	 */
	static class Quantizer {
		private int[] quantum_luminance = new int[64];
		private int[] quantum_chrominance = new int[64];

		private static int[] QUANTUM_LUMINANCE = {
				16, 11, 10, 16, 24, 40, 51, 61,
				12, 12, 14, 19, 26, 58, 60, 55,
				14, 13, 16, 24, 40, 57, 69, 56,
				14, 17, 22, 29, 51, 87, 80, 62,
				18, 22, 37, 56, 68, 109, 103, 77,
				24, 35, 55, 64, 81, 104, 113, 92,
				49, 64, 78, 87, 103, 121, 120, 101,
				72, 92, 95, 98, 112, 100, 103, 99
		};

		private static int[] QUANTUM_CHROMINANCE = {
				17, 18, 24, 47, 99, 99, 99, 99,
				18, 21, 26, 66, 99, 99, 99, 99,
				24, 26, 56, 99, 99, 99, 99, 99,
				47, 66, 99, 99, 99, 99, 99, 99,
				99, 99, 99, 99, 99, 99, 99, 99,
				99, 99, 99, 99, 99, 99, 99, 99,
				99, 99, 99, 99, 99, 99, 99, 99,
				99, 99, 99, 99, 99, 99, 99, 99
		};

		/**
		 * Initializes the quantizer, scaling the base tables according to the quality
		 * setting
		 */
		Quantizer(int quality) {
			int scale = quality;

			if (scale <= 0)
				scale = 1;
			if (scale > 100)
				scale = 100;
			if (scale < 50)
				scale = 5000 / scale;
			else
				scale = 200 - scale * 2;

			for (int i = 0; i < 64; i++) {
				quantum_luminance[i] = Math.max(1, (QUANTUM_LUMINANCE[i] * scale + 50) / 100);
				quantum_chrominance[i] = Math.max(1, (QUANTUM_CHROMINANCE[i] * scale + 50) / 100);
			}
		}

		/**
		 * Quantizes a single 8x8 block of DCT coefficients.
		 */
		public int[] quantizeBlock(double[][] dctData, boolean isLuminance) {
			int[] outputData = new int[64];
			int[] table = isLuminance ? quantum_luminance : quantum_chrominance;
			for (int i = 0; i < 8; i++) {
				for (int j = 0; j < 8; j++) {
					int index = i * 8 + j;
					outputData[index] = (int) Math.round((dctData[i][j] / 8.0) / table[index]);
				}
			}
			return outputData;
		}

		public int[] getQuantumLuminance() {
			return quantum_luminance;
		}

		public int[] getQuantumChrominance() {
			return quantum_chrominance;
		}
	}

	/**
	 * Handles Huffman encoding of quantized data. This is the final, lossless
	 * compression step.
	 * This class was modified by James R. Weeks on 3/27/98.
	 * It now incorporates Huffman table derivation as in the C jpeg library
	 * from the IJG, Jpeg-6a.
	 */
	static class Huffman {
		private int[] dcLuminanceBits = { 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 };
		private int[] dcLuminanceVal = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
		private int[] acLuminanceBits = { 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 125 };
		private int[] acLuminanceVal = {
				0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
				0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
				0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
				0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
				0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
				0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
				0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
				0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
				0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
				0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
				0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
				0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
				0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
				0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
				0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
				0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
				0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
				0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
				0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
				0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
				0xf9, 0xfa
		};
		private int[] dcChrominanceBits = { 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 };
		private int[] dcChrominanceVal = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
		private int[] acChrominanceBits = { 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 119 };
		private int[] acChrominanceVal = {
				0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
				0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
				0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
				0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
				0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
				0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
				0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
				0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
				0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
				0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
				0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
				0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
				0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
				0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
				0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
				0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
				0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
				0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
				0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
				0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
				0xf9, 0xfa
		};
		private HuffmanTable dcLumTable;
		private HuffmanTable acLumTable;
		private HuffmanTable dcChromTable;
		private HuffmanTable acChromTable;

		Huffman() {
			// Pre-builds the Huffman tables
			dcLumTable = new HuffmanTable(dcLuminanceBits, dcLuminanceVal);
			acLumTable = new HuffmanTable(acLuminanceBits, acLuminanceVal);
			dcChromTable = new HuffmanTable(dcChrominanceBits, dcChrominanceVal);
			acChromTable = new HuffmanTable(acChrominanceBits, acChrominanceVal);
		}

		/**
		 * Encodes a single 8x8 block of quantized data.
		 * 
		 * @return The new DC value to be used for the next block's differential coding.
		 */
		public int encodeBlock(BitStream bitStream, int[] quantizedData, int lastDcValue, boolean isLuminance) {
			HuffmanTable dcTable = isLuminance ? dcLumTable : dcChromTable;
			HuffmanTable acTable = isLuminance ? acLumTable : acChromTable;

			// DC coefficient is encoded as the difference from the last block's DC value.
			int dcDiff = quantizedData[0] - lastDcValue;
			int category = 32 - Integer.numberOfLeadingZeros(Math.abs(dcDiff));
			if (dcDiff == 0)
				category = 0;
			bitStream.write(dcTable.codes[category], dcTable.sizes[category]);
			if (category > 0) {
				bitStream.write(dcDiff > 0 ? dcDiff : ((1 << category) - 1 + dcDiff), category);
			}

			// AC coefficients are run-length encoded.
			int zeroRun = 0;
			for (int i = 1; i < 64; i++) {
				int acValue = quantizedData[JpegInfo.JPEG_NATURAL_ORDER[i]];
				if (acValue == 0) {
					zeroRun++;
				} else {
					while (zeroRun > 15) {
						bitStream.write(acTable.codes[0xF0], acTable.sizes[0xF0]); // ZRL (Zero Run Length) code
						zeroRun -= 16;
					}
					int acCategory = 32 - Integer.numberOfLeadingZeros(Math.abs(acValue));
					int symbol = (zeroRun << 4) | acCategory;
					bitStream.write(acTable.codes[symbol], acTable.sizes[symbol]);
					bitStream.write(acValue > 0 ? acValue : ((1 << acCategory) - 1 + acValue), acCategory);
					zeroRun = 0;
				}
			}
			if (zeroRun > 0) {
				bitStream.write(acTable.codes[0x00], acTable.sizes[0x00]); // EOB (End of Block) code
			}
			return quantizedData[0];
		}

		// Getters for writing Huffman tables to the JPEG header.
		public int[] getDcLuminanceBits() {
			return dcLuminanceBits;
		}

		public int[] getDcLuminanceVal() {
			return dcLuminanceVal;
		}

		public int[] getAcLuminanceBits() {
			return acLuminanceBits;
		}

		public int[] getAcLuminanceVal() {
			return acLuminanceVal;
		}

		public int[] getDcChrominanceBits() {
			return dcChrominanceBits;
		}

		public int[] getDcChrominanceVal() {
			return dcChrominanceVal;
		}

		public int[] getAcChrominanceBits() {
			return acChrominanceBits;
		}

		public int[] getAcChrominanceVal() {
			return acChrominanceVal;
		}
	}

	/**
	 * Represents a pre-calculated Huffman table for efficient lookups during
	 * encoding.
	 */
	static class HuffmanTable {
		int[] sizes = new int[257];
		int[] codes = new int[257];

		/**
		 * Builds the Huffman code lookup tables from the bit lengths and values.
		 */
		HuffmanTable(int[] bits, int[] values) {
			List<Integer> huffSize = new ArrayList<>();
			for (int i = 1; i < 17; i++) {
				for (int j = 0; j < bits[i - 1]; j++) {
					huffSize.add(i);
				}
			}
			huffSize.add(0);

			int k = 0;
			int code = 0;
			int si = huffSize.get(0);
			while (true) {
				if (huffSize.get(k) == 0)
					break;
				while (huffSize.get(k) == si) {
					codes[values[k]] = code;
					sizes[values[k]] = si;
					code++;
					k++;
				}
				code <<= 1;
				si++;
			}
		}
	}

	/**
	 * Manages writing individual bits to the output stream, buffering them into
	 * bytes.
	 */
	static class BitStream {
		private OutputStream out;
		private int buffer;
		private int count;

		BitStream(OutputStream out) {
			this.out = out;
		}

		/**
		 * Writes a code of a certain size (number of bits) to the buffer.
		 */
		void write(int code, int size) {
			try {
				for (int i = size - 1; i >= 0; i--) {
					buffer = (buffer << 1) | ((code >> i) & 1);
					count++;
					if (count == 8) {
						out.write(buffer);
						if (buffer == 0xFF) {
							out.write(0);
						}
						buffer = 0;
						count = 0;
					}
				}
			} catch (IOException e) {
				System.out.println("IO Error: " + e.getMessage());
			}
		}

		/**
		 * Writes any remaining bits from the buffer to the output stream,
		 * padding with 1s if necessary.
		 */

		void flush() {
			try {
				if (count > 0) {
					buffer <<= (8 - count);
					out.write(buffer);
				}
			} catch (IOException e) {
				System.out.println("IO Error: " + e.getMessage());
			}

		}
	}
}
