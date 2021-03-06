package com.rinke.solutions.pinball.view.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import org.eclipse.swt.SWT;

import com.rinke.solutions.beans.Autowired;
import com.rinke.solutions.beans.Bean;
import com.rinke.solutions.beans.Value;
import com.rinke.solutions.pinball.DMD;
import com.rinke.solutions.pinball.animation.Animation;
import com.rinke.solutions.pinball.animation.CompiledAnimation;
import com.rinke.solutions.pinball.io.DMCImporter;
import com.rinke.solutions.pinball.io.FileHelper;
import com.rinke.solutions.pinball.io.PaletteImporter;
import com.rinke.solutions.pinball.io.SmartDMDImporter;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.PalMapping;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.PaletteType;
import com.rinke.solutions.pinball.model.Plane;
import com.rinke.solutions.pinball.model.RGB;
import com.rinke.solutions.pinball.ui.ConfigDialog;
import com.rinke.solutions.pinball.ui.PalettePicker;
import com.rinke.solutions.pinball.util.Config;
import com.rinke.solutions.pinball.util.FileChooserUtil;
import com.rinke.solutions.pinball.util.MessageUtil;
import com.rinke.solutions.pinball.view.model.ViewModel;

@Slf4j
@Bean
public class PaletteHandler extends AbstractCommandHandler implements ViewBindingHandler {
	
	FileHelper fileHelper = new FileHelper();
	@Autowired FileChooserUtil fileChooserUtil;
	@Autowired MessageUtil messageUtil;
	@Autowired PalettePicker palettePicker;
	
	@Value(key=Config.COLOR_ACCURACY,defaultValue="0")
    private int colorAccuracy;

	public PaletteHandler(ViewModel vm) {
		super(vm);
	}
	
	public void onRenamePalette(String newName) {
		if (newName.contains(" - ")) {
			vm.selectedPalette.name = newName.split(" - ")[1];
			vm.paletteMap.refresh();
			int idx = vm.selectedPalette.index;
			vm.setSelectedPalette(null);	// to force refresh
			vm.setSelectedPaletteByIndex(idx);
		} else {
			messageUtil.warn("Illegal palette name", "Palette names must consist of palette index and name.\nName format therefore must be '<idx> - <name>'");
			vm.setEditedPaletteName(vm.selectedPalette.index + " - " + vm.selectedPalette.name);
		}
	}
	
	public void onSelectedPaletteTypeChanged(PaletteType o, PaletteType palType) {
		if( vm.selectedPalette != null ) {
			vm.selectedPalette.type = palType;
			// normalize: only one palette can be of type DEFAULT
			if (PaletteType.DEFAULT.equals(palType)) {
				for (Palette p : vm.paletteMap.values()) {
					if (p.index != vm.selectedPalette.index) { // set previous default to
						if (p.type.equals(PaletteType.DEFAULT)) {
							p.type = PaletteType.NORMAL;
						}
					}
				}
			}
		} else {
			log.warn("onSelectedPaletteTypeChanged but no palette selected");
		}
	}
	
	public void onPickColor(int rgb) {
		if( vm.selectedPalette != null && vm.numberOfPlanes >= 15) {
			vm.selectedPalette.colors[vm.selectedColor] = RGB.fromInt(rgb);
		}
		vm.setPaletteDirty(true);
	}

	public void onSelectedPaletteChanged(Palette o, Palette newPalette) {
		if( newPalette != null) {
			log.info("new palette is {}", vm.selectedPalette);
			vm.setSelectedPaletteType(vm.selectedPalette.type);
		}
	}
	
	public void onSwapColors(int old, int newIdx) {
		log.info("swapping colors in palette for actual scene: {} -> {}", old, newIdx);
		// TODO should change all scenes that use that palette
		if( vm.selectedScene != null ) {
			CompiledAnimation ani = vm.selectedScene;
			for( Frame f : ani.frames ) {
				swap(f, old, newIdx, vm.dmd.getWidth(), vm.dmd.getHeight());
			}
		}
	}
	
	public void onPickPalette() {
		if( vm.selectedScene != null ) {
			palettePicker.setAccuracy(colorAccuracy);
			palettePicker.setColorListProvider(p->extractColorsFromScene(vm.selectedScene, p));
			palettePicker.open();
			colorAccuracy = palettePicker.getAccuracy();
			if( palettePicker.getResult() != null && vm.selectedPalette != null ) {
				int i = 0;
				for( RGB c : palettePicker.getResult()) {
					if( i < vm.selectedPalette.numberOfColors ) vm.selectedPalette.colors[i++] = c;
				}
				vm.setPaletteDirty(true);
			}
		}
	}
	
	List<RGB> extractColorsFromScene(CompiledAnimation scene, int accuracy) {
		int w = scene.width;
		int h = scene.height;
		List<RGB> result = new ArrayList<>();
		for( Frame f: scene.frames) {
			for( int x = 0; x < w; x++) {
				for(int y=0; y < h; y++) {
					int bytesPerRow = w / 8;
			    	byte mask = (byte) (0b10000000 >> (x % 8));
			    	int rgb = 0;
			    	for(int plane = 0; plane < f.planes.size(); plane++) {
			    		rgb += (f.planes.get(plane).data[x / 8 + y * bytesPerRow] & mask) != 0 ? (1<<plane) : 0;
			    	}
			    	// color
			    	RGB col = RGB.fromInt(rgb);
			    	if( !inList( result, col, accuracy ) ) {
			    		result.add(col);
			    	}
				}
			}
		}
		return result;
	}
	
	/**
	 * checks if a given color is already in the list with respect to the given accuracy
	 * @param colors list of colors
	 * @param col color to check
	 * @param accuracy accuracy from 0 to 100 (100 very accurate -> exact match)
	 * @return true if similar color is found
	 */
	private boolean inList(List<RGB> colors, RGB col, int accuracy) {
		for( RGB c : colors) {
			float d = getColorDistance(c, col); 
			//System.out.println(d);
			if( d < (0.001f + 2f * accuracy) ) return true; 
		}
		return false;
	}
	
	public float getColorDistance(RGB c1, RGB c2) {
		return (float) getColorDistance(c1.red, c1.green, c1.blue, c2.red, c2.green, c2.blue);
	}

	public double getColorDistance(int r1, int g1, int b1, int r2, int g2, int b2) {
		return Math.sqrt(Math.pow(r2 - r1, 2) + Math.pow(g2 - g1, 2) + Math.pow(b2 - b1, 2));
	}

	// TODO sehr ähnlich funktionen gibts im Animation Quantisierer
	/**
	 * swap color index from old to new
	 * @param f 
	 * @param o old color index
	 * @param n new color index
	 */
	public void swap(Frame f, int o, int n, int w, int h) {
		List<Plane> np = new ArrayList<>();
		// copy planes
		for(int plane = 0; plane < f.planes.size(); plane++) {
			np.add( new Plane(f.planes.get(plane)));
		}
		// transform (swap) planes
		for( int x = 0; x < w; x++) {
			for(int y=0; y < h; y++) {
				int bytesPerRow = w / 8;
		    	byte mask = (byte) (0b10000000 >> (x % 8));
		    	int v = 0;
		    	int nv = 0;
		    	for(int plane = 0; plane < f.planes.size(); plane++) {
		    		v += (f.planes.get(plane).data[x / 8 + y * bytesPerRow] & mask) != 0 ? (1<<plane) : 0;
		    	}
		    	// swap
		    	if( v == o ) nv = n;
		    	else if( v == n ) nv = o;
		    	else nv = v;
		    	
		    	for(int plane = 0; plane < np.size(); plane++) {
		    		if( ((1<<plane)) != 0) {
		        		if( (nv & 0x01) != 0) {
		        			np.get(plane).data[y*bytesPerRow+x/8] |= mask;
		        		} else {
		        			np.get(plane).data[y*bytesPerRow+x/8] &= ~mask;
		        		}
		    		}
		    		nv >>= 1;
		    	}		    	
			} // y
		} // x
		// replace it
		f.planes = np;
	}
	
	public void onExtractPalColorsFromFrame() {
		palettePicker.setAccuracy(colorAccuracy);
		palettePicker.setColorListProvider(p->extractColorsFromFrame(vm.dmd, p));
		palettePicker.open();
		colorAccuracy = palettePicker.getAccuracy();
		if( palettePicker.getResult() != null && vm.selectedPalette != null ) {
			int i = 0;
			for( RGB c : palettePicker.getResult()) {
				if( i < vm.selectedPalette.numberOfColors ) vm.selectedPalette.colors[i++] = c;
			}
			vm.setPaletteDirty(true);
		}
	}

	List<RGB> extractColorsFromFrame(DMD dmd, int accuracy) {
		List<RGB> res = new ArrayList<>();
		for( int x = 0; x < dmd.getWidth(); x++) {
			for(int y = 0; y < dmd.getHeight(); y++) {
				int rgb = dmd.getPixelWithoutMask(x, y);
				RGB col = RGB.fromInt(rgb);
				if( !inList(res, col, accuracy) ) {
					res.add(col);
				}
			}
		}
		return res;
	}

	private boolean isNewPaletteName(String text) {
		for (Palette pal : vm.paletteMap.values()) {
			if (pal.name.equals(text))
				return false;
		}
		return true;
	}
	
	public void copyPalettePlaneUpgrade() {
		String name = vm.selectedPalette.name;
		if (!isNewPaletteName(name)) {
			name = "new" + UUID.randomUUID().toString().substring(0, 4);
		}
		
		RGB[] actCols = vm.selectedPalette.colors;
		RGB[] cols = new RGB[actCols.length];
		// copy
		for( int i = 0; i< cols.length; i++) cols[i] = RGB.of(actCols[i]);
		
		cols[2] = RGB.of(actCols[4]);
		cols[3] = RGB.of(actCols[15]);
		
		Palette newPalette = new Palette(cols, vm.paletteMap.size(), name);
		for( Palette pal : vm.paletteMap.values() ) {
			if( pal.sameColors(cols)) {
				vm.setSelectedPalette(pal);
				// bound editor.v.paletteTool.setPalette(vm.selectedPalette);	
				// bound editor.v.paletteComboViewer.setSelection(new StructuredSelection(vm.selectedPalette), true);
				return;
			}
		}
		
		vm.paletteMap.put(newPalette.index, newPalette);
		vm.setSelectedPalette(newPalette);
	}
	
	public void onNewPalette() {
		String name = vm.editedPaletteName;
		if (!isNewPaletteName(name)) {
			name = "new" + UUID.randomUUID().toString().substring(0, 4);
		}
		Palette newPal =  new Palette(vm.selectedPalette.colors, getHighestIndex(vm.paletteMap)+1, name);
		vm.paletteMap.put(newPal.index,newPal);
		vm.setSelectedPalette(newPal);
		vm.setDirty(true);
	}

	private int getHighestIndex(Map<Integer, Palette> paletteMap) {
		OptionalInt max = paletteMap.values().stream().mapToInt(p->p.index).max();
		return max.orElse(0);
	}

	public void onSavePalette() {
		String filename = fileChooserUtil.choose(SWT.SAVE, vm.selectedPalette.name, new String[] { "*.xml", "*.json" }, new String[] { "Paletten XML", "Paletten JSON" });
		if (filename != null) {
			log.info("store palette to {}", filename);
			fileHelper.storeObject(vm.selectedPalette, filename);
		}
	}

	public void onLoadPalette() {
		String filename = fileChooserUtil.choose(SWT.OPEN, null, new String[] { "*.xml", "*.json,", "*.txt", "*.dmc" }, new String[] { "Palette XML",
				"Palette JSON", "smartdmd", "DMC" });
		if (filename != null)
			loadPalette(filename);
	}

	void loadPalette(String filename) {
		java.util.List<Palette> palettesImported = null;
		if (filename.toLowerCase().endsWith(".txt") || filename.toLowerCase().endsWith(".dmc")) {
			palettesImported = getImporterByFilename(filename).importFromFile(filename);
		} else {
			Palette pal = (Palette) fileHelper.loadObject(filename);
			log.info("load palette from {}", filename);
			palettesImported = Arrays.asList(pal);
		}
		if( palettesImported != null ) {
			String override = checkOverride(vm.paletteMap, palettesImported);
			if (!override.isEmpty()) {
				int res = messageUtil.warn(SWT.ICON_WARNING | SWT.OK | SWT.IGNORE | SWT.ABORT,
						"Override warning", "importing these palettes will override palettes: " + override + "\n");
				if (res != SWT.ABORT) {
					importPalettes(palettesImported, res == SWT.OK);
				}
			} else {
				importPalettes(palettesImported, true);
			}
			//editor.v.recentPalettesMenuManager.populateRecent(filename);
			// view needs to listen / Custom Binding
			vm.setRecentPalette(filename);
		}
	}

	private PaletteImporter getImporterByFilename(String filename) {
		if (filename.toLowerCase().endsWith(".txt")) {
			return new SmartDMDImporter();
		} else if (filename.toLowerCase().endsWith(".dmc")) {
			return new DMCImporter();
		}
		return null;
	}
	
	void importPalettes(java.util.List<Palette> palettesImported, boolean override) {
		for (Palette p : palettesImported) {
			if (vm.paletteMap.containsKey(p.index)) {
				if (override)
					vm.paletteMap.put(p.index, p);
			} else {
				vm.paletteMap.put(p.index, p);
			}
		}
	}

	String checkOverride(java.util.Map<Integer,Palette> pm, java.util.List<Palette> palettesImported) {
		StringBuilder sb = new StringBuilder();
		for (Palette pi : palettesImported) {
			if (pi.index != 0 && pm.containsKey(pi.index)) {
				sb.append(pi.index + ", ");
			}
		}
		return sb.toString();
	}
	
	public void onDeletePalette() {
		if( vm.selectedPalette != null && vm.paletteMap.size()>1 ) {
			// check if any scene is using this
			List<String> res = new ArrayList<>();
			for( Animation a: vm.scenes.values()) {
				if( a.getPalIndex() == vm.selectedPalette.index ) {
					res.add("Scene "+a.getDesc());
				}
			}
			for( Animation a: vm.recordings.values()) {
				if( a.getPalIndex() == vm.selectedPalette.index ) {
					res.add(a.getDesc());
				}
			}
			// also check keyframes
			for( PalMapping pm : vm.keyframes.values()) {
				if( pm.palIndex == vm.selectedPalette.index ) {
					res.add("KeyFrame "+pm.name);
				}
			}
			if( res.isEmpty() ) {
				vm.paletteMap.remove(vm.selectedPalette.index);
				// ensure there is a default palette
				int c = 0;
				for( Palette p : vm.paletteMap.values()) {
					if( p.type == PaletteType.DEFAULT ) c++;
				}
				if( c == 0 ) vm.paletteMap.get(0).type = PaletteType.DEFAULT;
				// select first
				vm.setSelectedPalette(vm.paletteMap.values().iterator().next());
			} else {
				messageUtil.warn("Palette cannot be deleted", "It is used by: "+res);
			}
		}
	}

}
