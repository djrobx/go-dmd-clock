package com.rinke.solutions.pinball.renderer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.rinke.solutions.pinball.DMD;

public class PinDumpRendererTest {
	
	String base = "./src/test/resources/renderer/";
	String filename = "250216_224617_pin2dmd.dump.gz";
	
	PinDumpRenderer uut = new PinDumpRenderer();

	@Test
	public void testReadImage() throws Exception {
		DMD dmd = new DMD(128,32);
		uut.readImage(base+filename, dmd );
		assertEquals(611, uut.getFrames().size());
	}

}
