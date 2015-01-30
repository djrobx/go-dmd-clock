package com.rinke.solutions.pinball.renderer;

import java.io.File;

import com.rinke.solutions.pinball.DMD;

import ar.com.hjg.pngj.IImageLine;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngReader;

public class PngRenderer extends Renderer {
	
	private String pattern = "Image-0x%04X";
	
	private boolean autoMerge = false;

	public void setAutoMerge(boolean autoMerge) {
		this.autoMerge = autoMerge;
	}

	public PngRenderer(String pattern, boolean autoMerge) {
		super();
		this.pattern = pattern;
		this.autoMerge = autoMerge;
	}

	public PngRenderer() {
	}

	public FrameSet convert(String name, DMD dmd, int frameNo) {

		String filename = getFilename(name, frameNo);

		PngReader pngr = new PngReader(new File(filename));
		PngReader pngrMerge = null;
		if( autoMerge ) pngrMerge = new PngReader(new File(getFilename(name, frameNo+1)));

		int channels = pngr.imgInfo.channels;
		if (channels < 3 || pngr.imgInfo.bitDepth != 8)
			throw new RuntimeException("This method is for RGB8/RGBA8 images");

		byte[] f1 = new byte[dmd.getFrameSizeInByte()];
		byte[] f2 = new byte[dmd.getFrameSizeInByte()];

		for (int row = 0; row < pngr.imgInfo.rows; row++) { // also:
			int[] scanline = ((ImageLineInt) pngr.readRow()).getScanline();
			int[] scanlineMerge = null;
			if( autoMerge ) {
				scanlineMerge = ((ImageLineInt) pngrMerge.readRow()).getScanline();
			}
			int rowOffset = dmd.getBytesPerRow() * row;
			for (int j = 0; j < pngr.imgInfo.cols; j++) {
				
				if( autoMerge ) {
					if (scanline[j * channels + 3] > 0 || 
							scanlineMerge[j * channels + 3] > 0	) {
						int v = scanline[j * channels + 0] + scanlineMerge[j * channels + 0]*2;
						if( v == 255 ) {
							f1[rowOffset + j / 8] |= (128 >> (j % 8));
						} else if( v == 510 ) {
							f2[rowOffset + j / 8] |= (128 >> (j % 8));
						} else {
							f1[rowOffset + j / 8] |= (128 >> (j % 8));
							f2[rowOffset + j / 8] |= (128 >> (j % 8));
						}
					}
				} else {
					if (scanline[j * channels + 3] > 0) { //
						// grau wert berechnen
						// 0 85 170 255
						float x = 0.299f * scanline[j * channels + 0] + 0.587f
								* scanline[j * channels + 1] + 0.114f
								* scanline[j * channels + 2];
						if (x > lowThreshold && x < midThreshold) {
							// set f1
							f1[rowOffset + j / 8] |= (128 >> (j % 8));
						} else if (x >= midThreshold && x < highThreshold) {
							f2[rowOffset + j / 8] |= (128 >> (j % 8));
						} else if (x > highThreshold) {
							f1[rowOffset + j / 8] |= (128 >> (j % 8));
							f2[rowOffset + j / 8] |= (128 >> (j % 8));
						}
					}
				}
			}
		}
		pngr.end(); // it's recommended to end the reader first, in case there
					// are trailing chunks to read
		if( autoMerge ) pngrMerge.end();
		return new FrameSet(dmd.getWidth(), dmd.getHeight(), f1, f2);
	}

	private String getFilename(String name, int frameNo) {
		String filename = name + "/"+ String.format(pattern,
				new Object[] { Integer.valueOf(frameNo) })+".png";
		return filename;
	}

	public static void main(String[] args) {
		Renderer renderer = new PngRenderer();
		String base = "/home/sr/Downloads/Pinball/";
		DMD dmd = new DMD(16, 32);
		for (int j = 0x329; j <= 0x0333; j++) {
			String number = String.format("%04X",
					new Object[] { Integer.valueOf(j) });
			renderer.convert(base + "Getaway/Image-0x"+number+".png", dmd,1);
			for (int i = 0; i < 30; i++) {
				int b = dmd.frame1[0 + i * dmd.getBytesPerRow()];
				String n = "00000000000" + Integer.toBinaryString(b);
				System.out.println("0b0"+n.substring(n.length() - 8, n.length()));
			}
		}
	}

}
