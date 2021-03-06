package com.rinke.solutions.pinball;

import static com.rinke.solutions.databinding.WidgetProp.*;
import static com.rinke.solutions.pinball.Commands.*;

import java.awt.SplashScreen;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.slf4j.LoggerFactory;

import com.rinke.solutions.beans.Autowired;
import com.rinke.solutions.beans.BeanFactory;
import com.rinke.solutions.beans.SimpleBeanFactory;
import com.rinke.solutions.databinding.DataBinder;
import com.rinke.solutions.databinding.GuiBinding;
import com.rinke.solutions.databinding.PojoBinding;
import com.rinke.solutions.pinball.animation.Animation;
import com.rinke.solutions.pinball.animation.Animation.EditMode;
import com.rinke.solutions.pinball.model.Bookmark;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.PalMapping;
import com.rinke.solutions.pinball.model.PalMapping.SwitchMode;
import com.rinke.solutions.pinball.model.Palette;
import com.rinke.solutions.pinball.model.PaletteType;
import com.rinke.solutions.pinball.model.Plane;
import com.rinke.solutions.pinball.renderer.ImageUtil;
import com.rinke.solutions.pinball.swt.ActionAdapter;
import com.rinke.solutions.pinball.swt.CocoaGuiEnhancer;
import com.rinke.solutions.pinball.ui.RegisterLicense;
import com.rinke.solutions.pinball.ui.UsbConfig;
import com.rinke.solutions.pinball.util.Config;
import com.rinke.solutions.pinball.util.FileChooserUtil;
import com.rinke.solutions.pinball.util.MessageUtil;
import com.rinke.solutions.pinball.util.RecentMenuManager;
import com.rinke.solutions.pinball.view.CmdDispatcher;
import com.rinke.solutions.pinball.view.CmdDispatcher.Command;
import com.rinke.solutions.pinball.view.model.ViewModel;
import com.rinke.solutions.pinball.widget.CircleTool;
import com.rinke.solutions.pinball.widget.DMDWidget;
import com.rinke.solutions.pinball.widget.DrawTool;
import com.rinke.solutions.pinball.widget.FloodFillTool;
import com.rinke.solutions.pinball.widget.LineTool;
import com.rinke.solutions.pinball.widget.PalettePickerTool;
import com.rinke.solutions.pinball.widget.PaletteTool;
import com.rinke.solutions.pinball.widget.RectTool;
import com.rinke.solutions.pinball.widget.SelectTool;
import com.rinke.solutions.pinball.widget.SetPixelTool;

@Slf4j
public class EditorView implements MainView {
	
	int numberOfHashes;

	public EditorView(int numberOfHashes, boolean checkDirty) {
		btnHash = new Button[numberOfHashes];
		this.numberOfHashes = numberOfHashes;
		this.checkDirty = checkDirty;
	}

	private static final String HELP_URL = "http://pin2dmd.com/editor/";

	@Autowired CmdDispatcher dispatcher;
	@Autowired ViewModel vm;
	@Autowired MessageUtil messageUtil;
	@Autowired FileChooserUtil fileChooserUtil;
	
	private void dispatchCmd(String name) {
		log.debug("dispatchCmd '{}'", name);
		dispatcher.dispatch(new Command<Object>(null, name));
	}
	
	private <T> void dispatchCmd(String name, T param) {
		log.debug("dispatchCmd '{}', param={}", name, param);
		dispatcher.dispatch(new Command<T>(param, name));
	}

	private void dispatchCmd(String name, Object...params ) {
		log.debug("dispatchCmd '{}', params={}", name, params);
		dispatcher.dispatch(new Command<Object[]>(params, name));
	}
	
	public void createBindingsInternal() {
		DataBinder dataBinder = new DataBinder();
		dataBinder.bind(this, vm);
	}
	
	Display display;
	protected Shell shell;

	@GuiBinding( prop=LABEL, propName="timecode" ) Label lblTcval;
	@GuiBinding( prop=LABEL, propName="selectedFrame" ) Label lblFrameNo;

	/** instance level SWT widgets */
	Button btnHash[];// = new Button[numberOfHashes];
	
	@GuiBinding( prop=TEXT, propName="duration" ) 
	Text txtDuration;
	@GuiBinding( props={MIN,MAX,SELECTION} ) Scale frame;
	
	@GuiBinding( props={INPUT,SELECTION}, propNames={"paletteMap","selectedPalette"} )
	ComboViewer paletteComboViewer;
	@GuiBinding( prop=LABEL, propName="editedPaletteName" ) 
	Combo paletteCombo;

	@GuiBinding( props={INPUT,SELECTION}, propNames={"recordings", "selectedRecording"} )
	TableViewer recordingsListViewer;
	@GuiBinding( props={INPUT,SELECTION}, propNames={"scenes", "selectedScene"} )
	TableViewer sceneListViewer;
	@GuiBinding( props={INPUT,SELECTION}, propNames={"keyframes", "selectedKeyFrame"} )
	TableViewer keyframeTableViewer;
	
	@GuiBinding(prop=ENABLED) Button deleteRecording;
	@GuiBinding(prop=ENABLED) Button deleteKeyFrame;
	@GuiBinding(prop=ENABLED) Button btnAddKeyframe;
	@GuiBinding(prop=ENABLED) private Button fetchDuration;
	
	@GuiBinding(prop=ENABLED) Button btnPrev;
	@GuiBinding(prop=ENABLED) Button btnNext;
	
	@GuiBinding(prop=SELECTION, propName="selectedPaletteType")
	ComboViewer paletteTypeComboViewer;
	
	@PojoBinding(srcs={"mask", "showMask", "palette", "drawingEnabled" }, 
			targets={"selectedMask","showMask", "selectedPalette", "drawingEnabled" }) 
	DMDWidget dmdWidget;
	
	@PojoBinding(src="selection", target="selection") 
	SelectTool selectTool;

	DMDWidget fullScreenWidget = null;
	ResourceManager resManager;

	Button btnNewPalette;
	Button btnRenamePalette;
	@GuiBinding(prop=ENABLED, propName="drawingEnabled") private ToolBar drawToolBar;
	
	@GuiBinding( props={INPUT,SELECTION}, propNames={"scenes", "selectedFrameSeq"} )
	ComboViewer frameSeqViewer;
	@GuiBinding(prop=ENABLED) Button markStart;
	@GuiBinding(prop=ENABLED) Button markEnd;
	@GuiBinding(prop=ENABLED) Button cutScene;
	@GuiBinding( props= { ENABLED, LABEL } ) private Button startStop;
	@GuiBinding(props={ENABLED,LABEL}) Button btnAddFrameSeq;
	@PojoBinding(srcs={"mask","maskOut","palette"}, targets={"selectedMask","showMask","previewDmdPalette"}) 
	DMDWidget previewDmd;

	@PojoBinding(srcs={"numberOfPlanes", "palette", "selectedColor"}, targets={"paletteToolPlanes", "selectedPalette", "selectedColor"}) 
	PaletteTool paletteTool;

	@GuiBinding( prop=TEXT, propName="numberOfPlanes" ) Text lblPlanesVal;
	@GuiBinding( prop=TEXT, propName="delay" ) Text txtDelayVal;
	Button btnSortAni;

	@GuiBinding(props={ENABLED,SELECTION}, propNames={"detectionMaskEnabled", "detectionMaskActive"}) 
	Button detectionMask;
	@GuiBinding(props={ENABLED,SELECTION}, propNames={"layerMaskEnabled", "layerMaskActive"}) 
	Button layerMask;
	@GuiBinding( prop=SELECTION, propName="livePreviewActive") 
	Button btnLivePreview;

	Menu menuPopRecentProjects;
	Menu mntmRecentAnimations;
	Menu mntmRecentPalettes;
	
	@GuiBinding( prop=ENABLED )
	private MenuItem copy;
	@GuiBinding( prop=ENABLED )
	private MenuItem cut;
	

	@GuiBinding(props={ENABLED,SELECTION,MAX}, propNames={"maskSpinnerEnabled","selectedMaskNumber", "maxNumberOfMasks"}) 
	Spinner maskSpinner;

	GoDmdGroup goDmdGroup;
	@GuiBinding(prop=ENABLED) MenuItem mntmUploadProject;
	@GuiBinding(prop=ENABLED) MenuItem mntmUploadPalettes;
	MenuItem mntmSaveProject;
	
	@GuiBinding(prop=ENABLED, propName="drawingEnabled") MenuItem mntmSelectAll;
	@GuiBinding(prop=ENABLED, propName="drawingEnabled") MenuItem mntmDeSelect;
	
	@GuiBinding( prop=ENABLED, propName="undoEnabled" )  MenuItem mntmUndo;
	@GuiBinding( prop=ENABLED, propName="redoEnabled" )  MenuItem mntmRedo;

	@GuiBinding(prop=ENABLED) private Button copyToNext;
	@GuiBinding(prop=ENABLED) private Button undo;
	@GuiBinding(prop=ENABLED) private Button redo;
	@GuiBinding(prop=ENABLED) private Button copyToPrev;
	@GuiBinding(prop=ENABLED) private Button deleteColMask;

	RecentMenuManager recentProjectsMenuManager;
	RecentMenuManager recentPalettesMenuManager;
	RecentMenuManager recentAnimationsMenuManager;
	
	@GuiBinding( props={INPUT,SELECTION}, propNames={"availableEditModes", "suggestedEditMode"} )
	ComboViewer editModeViewer;
	@GuiBinding(prop=ENABLED) private Button deleteScene;
	@GuiBinding(prop=SELECTION) Spinner spinnerDeviceId;
	@GuiBinding(prop=SELECTION) Spinner spinnerEventId;
	@GuiBinding(prop=ENABLED) Button btnAddEvent;
	Composite grpKeyframe;
	Text textProperty;
	
	@GuiBinding( props={INPUT,SELECTION}, propNames={"bookmarks", "selectedBookmark"} )
	ComboViewer bookmarkComboViewer;
	@GuiBinding( props={LABEL,ENABLED}, propNames={"editedBookmarkName", "bookmarkComboEnabled"} ) 
	private Combo bookmarkCombo;

	@GuiBinding(prop=ENABLED) private Button btnInvert;
	@GuiBinding(prop=ENABLED) private Button btnSetScenePal;
	@GuiBinding(prop=ENABLED) private Button setKeyFramePal;
	@GuiBinding(prop=ENABLED) private Button btnNewBookmark;
	@GuiBinding(prop=ENABLED) private Button btnDelBookmark;
	@GuiBinding(prop=ENABLED, propName="drawingEnabled") private Button btnAddFrame;
	@GuiBinding(prop=ENABLED) private Button btnDelFrame;

	private Config config;
	
	/**
	 * creates the top level menu
	 */
	void createMenu(Shell shell) {
		Menu menu = new Menu(shell, SWT.BAR);
		shell.setMenuBar(menu);

		MenuItem mntmfile = new MenuItem(menu, SWT.CASCADE);
		mntmfile.setText("&File");

		Menu menu_1 = new Menu(mntmfile);
		mntmfile.setMenu(menu_1);

		MenuItem mntmNewProject = new MenuItem(menu_1, SWT.NONE);
		mntmNewProject.setText("New Project\tCtrl-N");
		mntmNewProject.setAccelerator(SWT.MOD1 + 'N');
		mntmNewProject.addListener(SWT.Selection, e -> dirtyCheck("newProject", "New Project"));

		MenuItem mntmLoadProject = new MenuItem(menu_1, SWT.NONE);
		mntmLoadProject.setText("Load Project\tCtrl-O");
		mntmLoadProject.setAccelerator(SWT.MOD1 + 'O');
		mntmLoadProject.addListener(SWT.Selection, e -> dirtyCheck(LOAD_PROJECT, "Load Project"));

		mntmSaveProject = new MenuItem(menu_1, SWT.NONE);
		mntmSaveProject.setText("Save Project\tCrtl-S");
		mntmSaveProject.setAccelerator(SWT.MOD1 + 'S');
		mntmSaveProject.addListener(SWT.Selection, e -> dispatchCmd(SAVE_PROJECT));

		MenuItem mntmSaveAsProject = new MenuItem(menu_1, SWT.NONE);
		mntmSaveAsProject.setText("Save Project as\tShift-Crtl-S");
		mntmSaveAsProject.setAccelerator(SWT.MOD1|SWT.MOD2 + 'S');
		mntmSaveAsProject.addListener(SWT.Selection, e -> dispatchCmd(SAVE_AS_PROJECT));

		MenuItem mntmRecentProjects = new MenuItem(menu_1, SWT.CASCADE);
		mntmRecentProjects.setText("Recent Projects");

		menuPopRecentProjects = new Menu(mntmRecentProjects);
		mntmRecentProjects.setMenu(menuPopRecentProjects);

		separator(menu_1);

		MenuItem mntmImportProject = new MenuItem(menu_1, SWT.NONE);
		mntmImportProject.setText("Import Project");
		mntmImportProject.addListener(SWT.Selection, e -> dispatchCmd(IMPORT_PROJECT));

		MenuItem mntmExportRealPinProject = new MenuItem(menu_1, SWT.NONE);
		mntmExportRealPinProject.setText("Export Project (real pin)");
		mntmExportRealPinProject.addListener(SWT.Selection, e -> dispatchCmd(EXPORT_REAL_PIN_PROJECT));

		MenuItem mntmExportVpinProject = new MenuItem(menu_1, SWT.NONE);
		mntmExportVpinProject.setText("Export Project (virt pin)");
		mntmExportVpinProject.addListener(SWT.Selection, e -> dispatchCmd(EXPORT_VIRTUAL_PIN_PROJECT));

		mntmUploadProject = new MenuItem(menu_1, SWT.NONE);
		mntmUploadProject.setText("Upload Project");
		mntmUploadProject.addListener(SWT.Selection, e -> dispatchCmd(UPLOAD_PROJECT));
		separator(menu_1);

		MenuItem mntmExit = new MenuItem(menu_1, SWT.NONE);
		mntmExit.setText("Exit\tCtrl-Q");
		mntmExit.setAccelerator(SWT.MOD1 + 'Q');
		mntmExit.addListener(SWT.Selection, e -> dirtyCheck(QUIT, "Quit") );

		MenuItem mntmedit = new MenuItem(menu, SWT.CASCADE);
		mntmedit.setText("&Edit");

		Menu menu_5 = new Menu(mntmedit);
		mntmedit.setMenu(menu_5);

		cut = new MenuItem(menu_5, SWT.NONE);
		cut.setText("Cut \tCtrl-X");
		cut.setAccelerator(SWT.MOD1 + 'X');
		cut.addListener(SWT.Selection, e -> dispatchCmd(CUT,vm.selectedPalette));

		copy = new MenuItem(menu_5, SWT.NONE);
		copy.setText("Copy \tCtrl-C");
		copy.setAccelerator(SWT.MOD1 + 'C');
		copy.addListener(SWT.Selection, e -> dispatchCmd(COPY,vm.selectedPalette));

		MenuItem mntmPaste = new MenuItem(menu_5, SWT.NONE);
		mntmPaste.setText("Paste\tCtrl-V");
		mntmPaste.setAccelerator(SWT.MOD1 + 'V');
		mntmPaste.addListener(SWT.Selection, e -> dispatchCmd(PASTE)); 

		MenuItem mntmPasteWithHover = new MenuItem(menu_5, SWT.NONE);
		mntmPasteWithHover.setText("Paste Over\tShift-Ctrl-V");
		mntmPasteWithHover.setAccelerator(SWT.MOD1 + SWT.MOD2 + 'V');
		mntmPasteWithHover.addListener(SWT.Selection, e -> dispatchCmd(PASTE_HOOVER)); 
		
		mntmSelectAll = new MenuItem(menu_5, SWT.NONE);
		mntmSelectAll.setText("Select All\tCtrl-A");
		mntmSelectAll.setAccelerator(SWT.MOD1 + 'A');
		mntmSelectAll.addListener(SWT.Selection, e -> dispatchCmd(SELECT_ALL) );

		mntmDeSelect = new MenuItem(menu_5, SWT.NONE);
		mntmDeSelect.setText("Remove Selection\tShift-Ctrl-A");
		mntmDeSelect.setAccelerator(SWT.MOD1 + SWT.MOD2 + 'A');
		mntmDeSelect.addListener(SWT.Selection, e -> dispatchCmd(REMOVE_SELECTION) );

		separator(menu_5);

		mntmUndo = new MenuItem(menu_5, SWT.NONE);
		mntmUndo.setText("Undo\tCtrl-Z");
		mntmUndo.setAccelerator(SWT.MOD1 + 'Z');
		mntmUndo.addListener(SWT.Selection, e -> dispatchCmd(UNDO));

		mntmRedo = new MenuItem(menu_5, SWT.NONE);
		mntmRedo.setText("Redo\tShift-Ctrl-Z");
		mntmRedo.setAccelerator(SWT.MOD1 + SWT.MOD2 + 'Z');
		mntmRedo.addListener(SWT.Selection, e -> dispatchCmd(REDO));

		MenuItem mntmAnimations = new MenuItem(menu, SWT.CASCADE);
		mntmAnimations.setText("&Animations");

		Menu menu_2 = new Menu(mntmAnimations);
		mntmAnimations.setMenu(menu_2);

		MenuItem mntmLoadAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmLoadAnimation.setText("Load Animation(s)");
		mntmLoadAnimation.addListener(SWT.Selection, e -> dispatchCmd(LOAD_ANI_WITH_FC,true));
		
		MenuItem mntmLoadRecordings = new MenuItem(menu_2, SWT.NONE);
		mntmLoadRecordings.setText("Load Recording(s)");
		mntmLoadRecordings.addListener(SWT.Selection, e -> dispatchCmd(LOAD_ANI_WITH_FC,true));
		
		MenuItem mntmSaveAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmSaveAnimation.setText("Save Animation(s) ...");
		mntmSaveAnimation.addListener(SWT.Selection, e -> dispatchCmd(SAVE_ANI_WITH_FC,1));
		
		MenuItem mntmSaveSingleAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmSaveSingleAnimation.setText("Save single Animation");
		mntmSaveSingleAnimation.addListener(SWT.Selection, e -> dispatchCmd(SAVE_SINGLE_ANI_WITH_FC,1));
		
		MenuItem mntmQuantizeScene = new MenuItem(menu_2, SWT.NONE);
		mntmQuantizeScene.setText("Quantize Scene");
		mntmQuantizeScene.addListener(SWT.Selection, e -> dispatchCmd("quantizeScene"));

		MenuItem mntmRecentAnimationsItem = new MenuItem(menu_2, SWT.CASCADE);
		mntmRecentAnimationsItem.setText("Recent Animations");

		mntmRecentAnimations = new Menu(mntmRecentAnimationsItem);
		mntmRecentAnimationsItem.setMenu(mntmRecentAnimations);

		separator(menu_2);
		
		MenuItem mntmPlayFullscreen = new MenuItem(menu_2, SWT.NONE);
		mntmPlayFullscreen.setText("Play Fullscreen");
		mntmPlayFullscreen.setAccelerator(SWT.MOD1 + SWT.F11);
		mntmPlayFullscreen.addListener(SWT.Selection, e -> playFullScreen() );
		
		separator(menu_2);

		MenuItem mntmExportAnimation = new MenuItem(menu_2, SWT.NONE);
		mntmExportAnimation.setText("Export Animation as GIF");	
		mntmExportAnimation.addListener(SWT.Selection, e -> dispatchCmd(EXPORT_GIF));

		MenuItem mntmExportForGodmd = new MenuItem(menu_2, SWT.NONE);
		mntmExportForGodmd.setText("Export for goDMD ...");
		mntmExportForGodmd.addListener(SWT.Selection, e-> dispatchCmd(EXPORT_GO_DMD));

		MenuItem mntmpalettes = new MenuItem(menu, SWT.CASCADE);
		mntmpalettes.setText("&Palettes / Mode");
		Menu menu_3 = new Menu(mntmpalettes);
		mntmpalettes.setMenu(menu_3);

		MenuItem mntmLoadPalette = new MenuItem(menu_3, SWT.NONE);
		mntmLoadPalette.setText("Load Palette");
		mntmLoadPalette.addListener(SWT.Selection, e -> dispatchCmd(LOAD_PALETTE));

		MenuItem mntmSavePalette = new MenuItem(menu_3, SWT.NONE);
		mntmSavePalette.setText("Save Palette");
		mntmSavePalette.addListener(SWT.Selection, e -> dispatchCmd(SAVE_PALETTE));
		
		MenuItem mntmExtractColorsFrom = new MenuItem(menu_3, SWT.NONE);
		mntmExtractColorsFrom.setText("Extract Colors from Frame\tCtrl-E");
		mntmExtractColorsFrom.setAccelerator(SWT.MOD1 + 'E');
		mntmExtractColorsFrom.addListener(SWT.Selection, e -> dispatchCmd("extractPalColorsFromFrame"));
		
		MenuItem mntmRecentPalettesItem = new MenuItem(menu_3, SWT.CASCADE);
		mntmRecentPalettesItem.setText("Recent Palettes");

		mntmRecentPalettes = new Menu(mntmRecentPalettesItem);
		mntmRecentPalettesItem.setMenu(mntmRecentPalettes);

		separator(menu_3);

		mntmUploadPalettes = new MenuItem(menu_3, SWT.NONE);
		mntmUploadPalettes.setText("Upload Palettes");
		mntmUploadPalettes.addListener(SWT.Selection, e -> dispatchCmd(UPLOAD_PALETTE,vm.selectedPalette));

		separator(menu_3);

		MenuItem mntmConfig = new MenuItem(menu_3, SWT.NONE);
		mntmConfig.setText("Configuration");
		mntmConfig.addListener(SWT.Selection, e -> dispatchCmd(CONFIGURATION) );

		MenuItem mntmDevice = new MenuItem(menu_3, SWT.NONE);
		mntmDevice.setText("Create Device File / WiFi");
		mntmDevice.addListener(SWT.Selection, e -> dispatchCmd(DEVICE_CONFIGURATION));

		MenuItem mntmUsbconfig = new MenuItem(menu_3, SWT.NONE);
		mntmUsbconfig.setText("Configure Device via USB");
		mntmUsbconfig.addListener(SWT.Selection, e -> new UsbConfig(shell).open());

		MenuItem mntmhelp = new MenuItem(menu, SWT.CASCADE);
		mntmhelp.setText("&Help");

		Menu menu_4 = new Menu(mntmhelp);
		mntmhelp.setMenu(menu_4);

		MenuItem mntmGetHelp = new MenuItem(menu_4, SWT.NONE);
		mntmGetHelp.setText("Get help");
		mntmGetHelp.addListener(SWT.Selection, e -> Program.launch(HELP_URL));
		
		MenuItem mntmSendReport = new MenuItem(menu_4, SWT.NONE);
		mntmSendReport.setText("Send Report");
		mntmSendReport.addListener(SWT.Selection, e -> {
			throw new NullPointerException();
		});

		MenuItem mntmRegister = new MenuItem(menu_4, SWT.NONE);
		mntmRegister.setText("Register");
		mntmRegister.addListener(SWT.Selection, e -> new RegisterLicense(shell).open());

		separator(menu_4);

		MenuItem mntmAbout = new MenuItem(menu_4, SWT.NONE);
		mntmAbout.setText("About");
		mntmAbout.addListener(SWT.Selection, e -> dispatchCmd(ABOUT));
	}
	
	private void separator(Menu menu) {
		new MenuItem(menu, SWT.SEPARATOR);
	}

	Composite createListComposite(Composite parent) {
		
		Composite listComp = new Composite(parent, 0);
		listComp.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		GridLayout gl_listComp = new GridLayout(3, false);
		listComp.setLayout(gl_listComp);

		Label lblAnimations = new Label(listComp, SWT.NONE);
		lblAnimations.setText("Recordings");
		
		Label lblScences = new Label(listComp, SWT.NONE);
		lblScences.setText("Scences");

		Label lblKeyframes = new Label(listComp, SWT.NONE);
		lblKeyframes.setText("Keyframes");

		int listWidth = 150;
		int listHeight = 181;
		int colWidth = 220;

		recordingsListViewer = new TableViewer(listComp, SWT.BORDER | SWT.V_SCROLL);
		Table aniList = recordingsListViewer.getTable();
		GridData gd_aniList = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_aniList.heightHint = listHeight;
		gd_aniList.widthHint = listWidth;
		aniList.setLayoutData(gd_aniList);
		aniList.setLinesVisible(true);
		aniList.addKeyListener(new EscUnselect(recordingsListViewer));
		recordingsListViewer.setContentProvider(ArrayContentProvider.getInstance());
		recordingsListViewer.setLabelProvider(new LabelProviderAdapter<Animation>(ani -> ani.getDesc()));
		
		// created edit support for ani / recordings
		TableViewerColumn viewerCol1 = new TableViewerColumn(recordingsListViewer, SWT.LEFT);
		viewerCol1.setEditingSupport(
				new GenericTextCellEditor<Animation>(recordingsListViewer, ani -> ani.getDesc(), (ani, newName) -> {
					if( !ani.getDesc().equals(newName)) {
						if( !vm.recordings.containsKey(newName)) {
							dispatchCmd(RENAME_RECORDING,ani.getDesc(), newName);
							ani.setDesc(newName);
						} else {
							messageUtil.warn("Recording name not unique","Recording names must be unique. Please choose a different name");
						}
					}
				}
			));
		viewerCol1.getColumn().setWidth(colWidth);
		viewerCol1.setLabelProvider(new IconLabelProvider<Animation>(shell, o -> o.getIconAndText() ));
		
		sceneListViewer = new TableViewer(listComp, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
		Table sceneList = sceneListViewer.getTable();
		GridData gd_list = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_list.heightHint = listHeight;
		gd_list.widthHint = listWidth;
		sceneList.setLayoutData(gd_list);
		sceneList.setLinesVisible(true);
		sceneList.addKeyListener(new EscUnselect(sceneListViewer));
		sceneListViewer.setContentProvider(ArrayContentProvider.getInstance());
		sceneListViewer.setLabelProvider(new LabelProviderAdapter<Animation>(o -> o.getDesc()));
		// bound directly
		//sceneListViewer.setInput(vm.scenes.values());
		//sceneListViewer.addSelectionChangedListener(event -> ed.onSceneSelectionChanged(ed.getFirstSelected(event)));

		TableViewerColumn viewerCol2 = new TableViewerColumn(sceneListViewer, SWT.LEFT);
		viewerCol2.setEditingSupport(
				new GenericTextCellEditor<Animation>(sceneListViewer, ani -> ani.getDesc(), (ani, newName) -> {
					if( !ani.getDesc().equals(newName) ) {
						if( !vm.scenes.containsKey(newName)) {
							String oldName = ani.getDesc();
							ani.setDesc(newName);
							dispatchCmd(RENAME_SCENE,oldName, newName);
							//frameSeqViewer.refresh();
						} else {
							messageUtil.warn("Scene name not unique","Scene names must be unique. Please choose a different name");
						}
					}
				}
			));
		viewerCol2.getColumn().setWidth(colWidth);
		viewerCol2.setLabelProvider(new IconLabelProvider<Animation>(shell, ani -> ani.getIconAndText() ));

		keyframeTableViewer = new TableViewer(listComp, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
		Table keyframeList = keyframeTableViewer.getTable();
		GridData gd_keyframeList = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_keyframeList.heightHint = listHeight;
		gd_keyframeList.widthHint = listWidth;
		keyframeList.setLinesVisible(true);
		keyframeList.setLayoutData(gd_keyframeList);
		keyframeList.addKeyListener(new EscUnselect(keyframeTableViewer));

		//keyframeTableViewer.setLabelProvider(new KeyframeLabelProvider(shell));
		keyframeTableViewer.setContentProvider(ArrayContentProvider.getInstance());
		// bound keyframeTableViewer.setInput(ed.project.palMappings);
		// boud keyframeTableViewer.addSelectionChangedListener(event -> ed.onKeyframeChanged(event));

		TableViewerColumn viewerColumn = new TableViewerColumn(keyframeTableViewer, SWT.LEFT);
		viewerColumn.setEditingSupport(new GenericTextCellEditor<PalMapping>(keyframeTableViewer, e -> e.name, (e, v) -> { e.name = v; }));

		viewerColumn.getColumn().setWidth(colWidth);
		viewerColumn.setLabelProvider(new IconLabelProvider<PalMapping>(shell, o -> Pair.of(o.switchMode.name().toLowerCase(), o.name ) ));

		Composite composite_1 = new Composite(listComp, SWT.NONE);
		composite_1.setLayout(new GridLayout(2, false));
		GridData gd_composite_1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_composite_1.widthHint = listWidth;
		composite_1.setLayoutData(gd_composite_1);

		deleteRecording = new Button(composite_1, SWT.NONE);
		deleteRecording.setToolTipText("Deletes selected recording");
		deleteRecording.setText("Del");
		deleteRecording.setEnabled(false);
		deleteRecording.addListener(SWT.Selection, e -> dispatchCmd(DELETE_RECORDING));

		btnSortAni = new Button(composite_1, SWT.NONE);
		btnSortAni.setToolTipText("Sorts recordings by name");
		btnSortAni.setText("Sort");
		btnSortAni.addListener(SWT.Selection, e -> dispatchCmd(SORT_RECORDING));
		
		Composite composite_4 = new Composite(listComp, SWT.NONE);
		composite_4.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		GridLayout gl_composite_4 = new GridLayout(3, false);
		gl_composite_4.horizontalSpacing = 1;
		gl_composite_4.marginWidth = 1;
		gl_composite_4.verticalSpacing = 1;
		composite_4.setLayout(gl_composite_4);
		
		deleteScene = new Button(composite_4, SWT.NONE);
		deleteScene.setToolTipText("Deletes selected scene");
		deleteScene.setEnabled(false);
		deleteScene.setText("Del");
		deleteScene.addListener(SWT.Selection, e -> dispatchCmd(DELETE_SCENE));
		
		Button btnSortScene = new Button(composite_4, SWT.NONE);
		btnSortScene.setToolTipText("Sorts scenes by name");
		btnSortScene.setText("Sort");
		btnSortScene.addListener(SWT.Selection, e -> dispatchCmd(SORT_SCENES) );
		
		btnSetScenePal = new Button(composite_4, SWT.NONE);
		btnSetScenePal.setToolTipText("Applies palette to scene");
		btnSetScenePal.setText("Pal");
		btnSetScenePal.setEnabled(false);
		btnSetScenePal.addListener(SWT.Selection, e -> dispatchCmd(SET_SCENE_PALETTE));

		Composite composite_2 = new Composite(listComp, SWT.NONE);
		GridLayout gl_composite_2 = new GridLayout(3, false);
		gl_composite_2.marginWidth = 1;
		gl_composite_2.horizontalSpacing = 1;
		composite_2.setLayout(gl_composite_2);

		deleteKeyFrame = new Button(composite_2, SWT.NONE);
		deleteKeyFrame.setToolTipText("Deletes selected keyframe");
		deleteKeyFrame.setText("Del");
		deleteKeyFrame.setEnabled(false);
		deleteKeyFrame.addListener(SWT.Selection, e -> dispatchCmd(DELETE_KEYFRAME));

		Button btnSortKeyFrames = new Button(composite_2, SWT.NONE);
		btnSortKeyFrames.setToolTipText("Sorts keyframes by name");
		btnSortKeyFrames.setText("Sort");
		btnSortKeyFrames.addListener(SWT.Selection, e -> dispatchCmd(SORT_KEY_FRAMES));
		
		setKeyFramePal = new Button(composite_2, SWT.NONE);
		setKeyFramePal.setToolTipText("Applies palette to keyframe");
		setKeyFramePal.setText("Pal");
		setKeyFramePal.setEnabled(false);
		setKeyFramePal.addListener(SWT.Selection, e -> dispatchCmd(SET_KEYFRAME_PALETTE));
		
		return listComp;

	}
	


	/**
	 * @wbp.parser.entryPoint
	 * Create contents of the window.
	 */
	public void createContents() {
		
		 // for the sake of window builder
//			shell = new Shell();
//			shell.setSize(1400, 1075);
//			this.vm = new ViewModel();
//			vm.dmd = new DMD(192, 64);
			
		shell.setMaximized(true);
		
		shell.setText("Pin2dmd - Editor");

		createMenu(shell);
		
		recentProjectsMenuManager = new RecentMenuManager("recentProject", 4, menuPopRecentProjects, 
				e -> dispatchCmd(LOAD_PROJECT,(String) e.widget.getData()), config);

		recentPalettesMenuManager = new RecentMenuManager("recentPalettes", 4, mntmRecentPalettes, 
				e -> dispatchCmd(LOAD_PALETTE,(String) e.widget.getData()), config);

		recentAnimationsMenuManager = new RecentMenuManager("recentAnimations", 4, mntmRecentAnimations, 
				e -> dispatchCmd(LOAD_ANIMATION,((String) e.widget.getData()), true, true), config);

		resManager = new LocalResourceManager(JFaceResources.getResources(), shell);
		
		shell.setLayout(new GridLayout(3,false));
		
		createListComposite(shell);

		createTabbedFolder(shell);

		Composite drawPalGroup = new Composite(shell,0);
		drawPalGroup.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		drawPalGroup.setLayout(new GridLayout(1,false));
		createPalettesGroup(drawPalGroup);
		createDrawingGroup(drawPalGroup);

		Composite previewGroup = new Composite(shell,0);
		previewGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
		GridLayout gl_previewGroup = new GridLayout(1,false);
		gl_previewGroup.marginRight = 20;
		previewGroup.setLayout(gl_previewGroup);
		createPreviewComposite(previewGroup);
		createDetailsGroup(previewGroup);
		createStartStopControl(previewGroup);
		
	}
	
	private void createDetailsGroup(Composite parent) {
		
		Group grpDetails = new Group(parent, SWT.NONE);
		grpDetails.setLayoutData(new GridData(SWT.FILL, SWT.TOP, false, false, 1, 1));
		grpDetails.setLayout(new GridLayout(12, false));
		
		/*GridData gd_grpDetails = new GridData(SWT.LEFT, SWT.CENTER, false, true, 1, 1);
		gd_grpDetails.heightHint = 27;
		gd_grpDetails.widthHint = 815;
		grpDetails.setLayoutData(gd_grpDetails);*/
		grpDetails.setText("Details");

		Label lblFrame = new Label(grpDetails, SWT.NONE);
		lblFrame.setText("Frame:");

		lblFrameNo = new Label(grpDetails, SWT.NONE);
		GridData gd_lblFrameNo = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblFrameNo.widthHint = 66;
		gd_lblFrameNo.minimumWidth = 60;
		lblFrameNo.setLayoutData(gd_lblFrameNo);
		lblFrameNo.setText("---");

		Label lblTimecode = new Label(grpDetails, SWT.NONE);
		lblTimecode.setText("Timecode:");

		lblTcval = new Label(grpDetails, SWT.NONE);
		GridData gd_lblTcval = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblTcval.widthHint = 62;
		gd_lblTcval.minimumWidth = 80;
		lblTcval.setLayoutData(gd_lblTcval);
		lblTcval.setText("---");

		Label lblDelay = new Label(grpDetails, SWT.NONE);
		lblDelay.setText("Delay:");

		txtDelayVal = new Text(grpDetails, SWT.NONE);
		GridData gd_lblDelayVal = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblDelayVal.widthHint = 53;
		txtDelayVal.setLayoutData(gd_lblDelayVal);
		txtDelayVal.setText("");
		txtDelayVal.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent event) {
				if( event.keyCode == SWT.CR ) {
					if( vm.selectedScene!=null ) {
						dispatchCmd(UPDATE_DELAY);
						vm.setDirty(true);
					}
				}
			}
		} );
		
		txtDelayVal.addListener(SWT.Verify, e -> e.doit = Pattern.matches("^[0-9]*$", e.text));

		Label lblPlanes = new Label(grpDetails, SWT.NONE);
		lblPlanes.setText("Planes:");

		lblPlanesVal = new Text(grpDetails, SWT.NONE);
		GridData gd_lblPlanesVal = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_lblPlanesVal.widthHint = 30;
		lblPlanesVal.setLayoutData(gd_lblPlanesVal);
		lblPlanesVal.setText("---");
		new Label(grpDetails, SWT.NONE);

		btnLivePreview = new Button(grpDetails, SWT.CHECK);
		btnLivePreview.setToolTipText("controls live preview to real display device");
		btnLivePreview.setText("Live Preview");
		// bound btnLivePreview.addListener(SWT.Selection, e -> ed.onLivePreviewSwitched(btnLivePreview.getSelection()));
				
		Button btnIncPitch = new Button(grpDetails, SWT.NONE);
		btnIncPitch.setText("+");
								
		Button btnDecPitch = new Button(grpDetails, SWT.NONE);
		btnDecPitch.setText("-");
		btnDecPitch.addListener(SWT.Selection, e -> dmdWidget.decPitch());
		btnIncPitch.addListener(SWT.Selection, e -> dmdWidget.incPitch());
	}
	
	Map<String, DrawTool> drawTools = new HashMap<>();
	
	 void dmdRedraw() {
		if( fullScreenWidget != null ) {
			fullScreenWidget.redraw();
		} else {
			dmdWidget.redraw();
			previewDmd.redraw();
		}
	}

	private void createDrawingGroup(Composite parent) {
		
		Group grpDrawing = new Group(parent, SWT.NONE);
		GridData gd_grpDrawing = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_grpDrawing.widthHint = 541;
		grpDrawing.setLayoutData(gd_grpDrawing);
		grpDrawing.setLayout(new GridLayout(7, false));
		
		grpDrawing.setText("Drawing");

		drawTools.put("pencil", new SetPixelTool(paletteTool.getSelectedColor()));
		drawTools.put("fill", new FloodFillTool(paletteTool.getSelectedColor()));
		drawTools.put("rect", new RectTool(paletteTool.getSelectedColor()));
		drawTools.put("line", new LineTool(paletteTool.getSelectedColor()));
		drawTools.put("circle", new CircleTool(paletteTool.getSelectedColor(), false));
		drawTools.put("filledCircle", new CircleTool(paletteTool.getSelectedColor(), true));
		palettePickerTool = new PalettePickerTool(0);
		drawTools.put("picker",palettePickerTool);
		selectTool = new SelectTool(paletteTool.getSelectedColor());
		selectTool.setDMD(vm.dmd);
		
		drawTools.put("select", selectTool );
		// notify draw tool on color changes
		drawTools.values().forEach(d -> paletteTool.addIndexListener(d));
		// let draw tools notify when draw action is finished
		drawTools.values().forEach(d->d.addObserver((dmd,o)->dispatchCmd(UPDATE_HASHES)));
		
		paletteTool.addListener(palette -> {
			if (vm.livePreviewActive) {
				dispatchCmd(UPLOAD_PALETTE,vm.selectedPalette);
			}
		});
				
		drawToolBar = new ToolBar(grpDrawing, SWT.FLAT | SWT.RIGHT);
		GridData gd_drawToolBar = new GridData(SWT.FILL, SWT.FILL, false, false, 2, 1);
		drawToolBar.setLayoutData(gd_drawToolBar);
						
		ToolItem tltmPen = new ToolItem(drawToolBar, SWT.RADIO);
		tltmPen.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/pencil.png")));
		tltmPen.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("pencil")));
								
		ToolItem tltmFill = new ToolItem(drawToolBar, SWT.RADIO);
		tltmFill.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/color-fill.png")));
		tltmFill.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("fill")));
										
		ToolItem tltmRect = new ToolItem(drawToolBar, SWT.RADIO);
		tltmRect.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/rect.png")));
		tltmRect.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("rect")));
												
		ToolItem tltmLine = new ToolItem(drawToolBar, SWT.RADIO);
		tltmLine.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/line.png")));
		tltmLine.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("line")));

		ToolItem tltmCircle = new ToolItem(drawToolBar, SWT.RADIO);
		tltmCircle.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/oval.png")));
		tltmCircle.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("circle")));

		ToolItem tltmFilledCircle = new ToolItem(drawToolBar, SWT.RADIO);
		tltmFilledCircle.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/oval2.png")));
		tltmFilledCircle.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("filledCircle")));

		//		ToolItem tltmColorize = new ToolItem(drawToolBar, SWT.RADIO);
//		tltmColorize.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/colorize.png")));
//		tltmColorize.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("colorize")));
		
		ToolItem tltmMark = new ToolItem(drawToolBar, SWT.RADIO);
		tltmMark.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/select.png")));
		tltmMark.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("select")));

		ToolItem tltmPicker = new ToolItem(drawToolBar, SWT.RADIO);
		tltmPicker.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/color-picker.png")));
		tltmPicker.addListener(SWT.Selection, e -> dmdWidget.setDrawTool(drawTools.get("picker")));
		
		editModeViewer = new ComboViewer(grpDrawing, SWT.READ_ONLY);
		Combo combo_2 = editModeViewer.getCombo();
		GridData gd_combo_2 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1);
		gd_combo_2.widthHint = 150;
		combo_2.setLayoutData(gd_combo_2);
		editModeViewer.setContentProvider(ArrayContentProvider.getInstance());
		editModeViewer.setLabelProvider(new LabelProviderAdapter<EditMode>(o -> o.label));
		
		// bound editModeViewer.setInput(EditMode.values());
		// bound editModeViewer.addSelectionChangedListener(e -> ed.onEditModeChanged(e));
		
		// TODO the list depends on animation type
		// for immutable only fixed ist selectable
		// else replace / mask / follow
		/*1 - Replacement
		2 - AddCol
		3 - AddCol mit Follow Hash

		könnte man wenn Mode = 3 und Mask = checked die Maske vom Frame editieren
		(Auswahl 1-10 wäre da ausgegraut)

		In Modus 1+2 würde ich die Mask-Checkbox, Maskennummer-Dropdown und die Hash-Checkboxen alle auch ausgrauen,
		da das alles editierten Content kein Sinn macht. 
		-> Die wären dann alle nur bei Dumps akti*/

		Label lblMaskNo = new Label(grpDrawing, SWT.NONE);
		lblMaskNo.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblMaskNo.setText("Mask:");

		maskSpinner = new Spinner(grpDrawing, SWT.BORDER);
		maskSpinner.setToolTipText("select the mask to use");
		maskSpinner.setMinimum(0);
		maskSpinner.setMaximum(25);
		maskSpinner.setEnabled(false);
		//maskSpinner.addListener(SWT.Selection, e -> ed.onMaskNumberChanged(maskSpinner.getSelection()));
		
		detectionMask = new Button(grpDrawing, SWT.CHECK);
		detectionMask.setText("D-Mask");
		detectionMask.setEnabled(false);
		detectionMask.setToolTipText("enables drawing the DETECTION MASK for triggering a keyframe");
		
		copyToPrev = new Button(grpDrawing, SWT.NONE);
		copyToPrev.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		copyToPrev.setText("CopyPrev");
		copyToPrev.addListener(SWT.Selection, e->dispatchCmd(COPY_AND_MOVE_TO_PREV_FRAME));
		
		copyToNext = new Button(grpDrawing, SWT.NONE);
		copyToNext.setToolTipText("copy the actual scene / color mask to next frame and move forward");
		copyToNext.setText("CopyNext");
		copyToNext.addListener(SWT.Selection, e->dispatchCmd(COPY_AND_MOVE_TO_NEXT_FRAME));
		
		undo = new Button(grpDrawing, SWT.NONE);
		undo.setText("&Undo");
		undo.addListener(SWT.Selection, e -> dispatchCmd(UNDO));
		
		redo = new Button(grpDrawing, SWT.NONE);
		redo.setText("&Redo");
		redo.addListener(SWT.Selection, e -> dispatchCmd(REDO));
		
		deleteColMask = new Button(grpDrawing, SWT.NONE);
		deleteColMask.setText("Del");
		deleteColMask.setEnabled(false);
		deleteColMask.addListener(SWT.Selection, e -> dispatchCmd(DELETE_COL_MASK));
		
		btnInvert = new Button(grpDrawing, SWT.NONE);
		btnInvert.setText("Inv");
		btnInvert.addListener(SWT.Selection, e->dispatchCmd(INVERT_MASK));
		btnInvert.setEnabled(false);

		layerMask = new Button(grpDrawing, SWT.CHECK);
		layerMask.setText("L-Mask");
		layerMask.setEnabled(false);
		layerMask.setToolTipText("enables drawing the LAYERED MASK for layered coloring");

		
	}

	private void createPalettesGroup(Composite parent) {
		Group grpPalettes = new Group(parent, SWT.NONE);
		GridData gd_grpPalettes = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_grpPalettes.widthHint = 539;
		grpPalettes.setLayoutData(gd_grpPalettes);
		GridLayout gl_grpPalettes = new GridLayout(5, false);
		gl_grpPalettes.verticalSpacing = 2;
		gl_grpPalettes.horizontalSpacing = 2;
		grpPalettes.setLayout(gl_grpPalettes);
		
		grpPalettes.setText("Palettes");

		paletteComboViewer = new ComboViewer(grpPalettes, SWT.NONE);
		paletteCombo = paletteComboViewer.getCombo();
		GridData gd_combo = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_combo.widthHint = 166;
		paletteCombo.setLayoutData(gd_combo);
		paletteComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		paletteComboViewer.setLabelProvider(new LabelProviderAdapter<Palette>(o -> o.index + " - " + o.name));
		// bound paletteComboViewer.setInput(vm.paletteMap.values());
		// bound paletteComboViewer.addSelectionChangedListener(event -> ed.onPaletteChanged(ed.getFirstSelected(event)));

		paletteTypeComboViewer = new ComboViewer(grpPalettes, SWT.READ_ONLY);
		Combo combo_1 = paletteTypeComboViewer.getCombo();
		combo_1.setToolTipText("Type of palette. Default palette is choosen at start and after timed switch is expired");
		GridData gd_combo_1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_combo_1.widthHint = 96;
		combo_1.setLayoutData(gd_combo_1);
		paletteTypeComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		paletteTypeComboViewer.setInput(PaletteType.values());
		// bound vm.setSelectedPaletteType(vm.selectedPalette.type);
		// bound paletteTypeComboViewer.addSelectionChangedListener(e -> ed.onPaletteTypeChanged(e));
		
		btnNewPalette = new Button(grpPalettes, SWT.NONE);
		btnNewPalette.setToolTipText("Creates a new palette by copying the actual colors");
		btnNewPalette.setText("New");
		btnNewPalette.addListener(SWT.Selection, e -> dispatchCmd(NEW_PALETTE));
		
		btnRenamePalette = new Button(grpPalettes, SWT.NONE);
		btnRenamePalette.setToolTipText("Confirms the new palette name");
		btnRenamePalette.setText("Rename");
		btnRenamePalette.addListener(SWT.Selection, e -> dispatchCmd(RENAME_PALETTE, vm.editedPaletteName));
		
		Button btnDeletePalette = new Button(grpPalettes, SWT.NONE);
		btnDeletePalette.setText("Delete");
		btnDeletePalette.addListener(SWT.Selection, e->dispatchCmd(DELETE_PALETTE));

		Composite grpPal = new Composite(grpPalettes, SWT.NONE);
		grpPal.setLayout(new GridLayout(1, false));
		GridData gd_grpPal = new GridData(SWT.LEFT, SWT.TOP, false, false, 3, 1);
		gd_grpPal.widthHint = 333;
		gd_grpPal.heightHint = 22;
		grpPal.setLayoutData(gd_grpPal);
		
		paletteTool = new PaletteTool(shell, grpPal, SWT.FLAT | SWT.RIGHT, vm.selectedPalette);
		
		ToolBar bar = new ToolBar(grpPalettes, SWT.NONE);
		btnPick = new ToolItem(bar, SWT.NONE);
		btnPick.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/color-picker.png")));
		btnPick.addListener(SWT.Selection, e->dispatchCmd("pickPalette"));
		
		ToolItem btnPick2 = new ToolItem(bar, SWT.NONE);
		btnPick2.setImage(resManager.createImage(ImageDescriptor.createFromFile(PinDmdEditor.class, "/icons/color-picker2.png")));
		btnPick2.addListener(SWT.Selection, e->dispatchCmd("extractPalColorsFromFrame"));

		Label lblCtrlclick = new Label(grpPalettes, SWT.NONE);
		lblCtrlclick.setText("Ctrl-Click to edit");
	}

	private void createStartStopControl(Composite parent) {
		
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		
		composite.setLayout(new GridLayout(11, false));

		startStop = new Button(composite, SWT.NONE);
		startStop.setToolTipText("Starts automatic playback");
		startStop.setText("Start");
		startStop.addListener(SWT.Selection, e -> dispatchCmd(START_STOP, vm.animationIsPlaying ));

		btnPrev = new Button(composite, SWT.NONE);
		btnPrev.setToolTipText("Go back one frame");
		btnPrev.setText("<");
		btnPrev.addListener(SWT.Selection, e -> dispatchCmd(PREV_FRAME));

		btnNext = new Button(composite, SWT.NONE);
		btnNext.setToolTipText("Move forward one frame");
		btnNext.setText(">");
		btnNext.addListener(SWT.Selection, e -> dispatchCmd(NEXT_FRAME));

		markStart = new Button(composite, SWT.NONE);
		markStart.setToolTipText("Marks start of scene for cutting");
		markStart.setText("Mark Start");
		markStart.addListener(SWT.Selection, e -> dispatchCmd(MARK_START));

		markEnd = new Button(composite, SWT.NONE);
		markEnd.setText("Mark End");
		markEnd.addListener(SWT.Selection, e ->  dispatchCmd(MARK_END));
		
		cutScene = new Button(composite, SWT.NONE);
		cutScene.setToolTipText("Cuts out a new scene for editing and use a replacement or color mask");
		cutScene.setText("Cut");
		cutScene.addListener(SWT.Selection, e -> dispatchCmd(CUT_SCENE));

		btnAddFrame = new Button(composite, SWT.NONE);
		btnAddFrame.setText("Frame+");
		btnAddFrame.addListener(SWT.Selection, e->dispatchCmd(ADD_FRAME));
		
		btnDelFrame = new Button(composite, SWT.NONE);
		btnDelFrame.setText("Frame-");
		btnDelFrame.addListener(SWT.Selection, e->dispatchCmd(REMOVE_FRAME));
		
		bookmarkComboViewer = new ComboViewer(composite, SWT.NONE);
		bookmarkCombo = bookmarkComboViewer.getCombo();
		GridData gd_combo_3 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_combo_3.widthHint = 106;
		bookmarkCombo.setLayoutData(gd_combo_3);
		bookmarkComboViewer.setContentProvider(ArrayContentProvider.getInstance());
		bookmarkComboViewer.setLabelProvider(new LabelProviderAdapter<Bookmark>(o -> o.name+" - "+o.pos));
			
		btnNewBookmark = new Button(composite, SWT.NONE);
		btnNewBookmark.setText("New");
		btnNewBookmark.addListener(SWT.Selection, e-> dispatchCmd(NEW_BOOKMARK));

		btnDelBookmark = new Button(composite, SWT.NONE);
		btnDelBookmark.setText("Del");
		btnDelBookmark.addListener(SWT.Selection, e->dispatchCmd(DEL_BOOKMARK));
		
	}
	
	public void createHashButtons(Composite parent, int x, int y) {
		for (int i = 0; i < numberOfHashes; i++) {
			btnHash[i] = new Button(parent, SWT.CHECK);
			if (i == 0)
				btnHash[i].setSelection(true);
			btnHash[i].setData(Integer.valueOf(i));
			btnHash[i].setText("Hash" + i);
			// btnHash[i].setFont(new Font(shell.getDisplay(), "sans", 10, 0));
			btnHash[i].setBounds(x, y + i * 16, 331, 18);
			btnHash[i].addListener(SWT.Selection, e -> dispatchCmd(HASH_SELECTED, (Integer) e.widget.getData(), 
					((Button)e.widget).getSelection() ));
		}
	}


	
	private Composite createKeyFrameGroup(Composite parent) {
		grpKeyframe = new Composite(parent, 0);
		GridLayout gl_grpKeyframe = new GridLayout(3, false);
		gl_grpKeyframe.horizontalSpacing = 0;
		gl_grpKeyframe.marginWidth = 0;
		grpKeyframe.setLayout(gl_grpKeyframe);
		GridData gd_grpKeyframe = new GridData(SWT.FILL, SWT.TOP, false, false, 3, 4);
		gd_grpKeyframe.heightHint = 257;
		//gd_grpKeyframe.widthHint = 460;
		grpKeyframe.setLayoutData(gd_grpKeyframe);
//		grpKeyframe.setText("KeyFrames");
//		grpKeyframe.setVisible(!ApplicationProperties.getBoolean(ApplicationProperties.GODMD_ENABLED_PROP_KEY, false));
		
		Composite hashPreviewGrp = new Composite(grpKeyframe, SWT.NONE);
		hashPreviewGrp.setLayout(new GridLayout(2, false));
		hashPreviewGrp.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		Composite composite_hash = new Composite(hashPreviewGrp, SWT.NONE);
		//gd_composite_hash.widthHint = 105;
		GridData gd_composite_hash = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_composite_hash.widthHint = 110;
		composite_hash.setLayoutData(gd_composite_hash);
		createHashButtons(composite_hash, 10, 0);
		
		previewDmd = new DMDWidget(hashPreviewGrp, SWT.DOUBLE_BUFFERED, vm.dmd, false);
		GridData gd_previewDmd = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		gd_previewDmd.widthHint = 203;
		previewDmd.setLayoutData(gd_previewDmd);
		previewDmd.setDrawingEnabled(false);
		previewDmd.setMaskOut(true);

		new Label(grpKeyframe, SWT.NONE);
		new Label(grpKeyframe, SWT.NONE);
		
		btnAddKeyframe = new Button(grpKeyframe, SWT.NONE);
		btnAddKeyframe.setToolTipText("Adds a key frame that switches palette");
		btnAddKeyframe.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, false, false, 1, 1));
		btnAddKeyframe.setText("Palette");
		btnAddKeyframe.setEnabled(false);
		btnAddKeyframe.addListener(SWT.Selection, e -> dispatchCmd(ADD_KEYFRAME,SwitchMode.PALETTE));
		
		Label lblScene = new Label(grpKeyframe, SWT.NONE);
		lblScene.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblScene.setText("Scene:");
		
		frameSeqViewer = new ComboViewer(grpKeyframe, SWT.NONE);
		Combo frameSeqCombo = frameSeqViewer.getCombo();
		frameSeqCombo.setToolTipText("Choose frame sequence to use with key frame");
		GridData gd_frameSeqCombo = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
		gd_frameSeqCombo.widthHint = 100;
		frameSeqCombo.setLayoutData(gd_frameSeqCombo);
		frameSeqViewer.setLabelProvider(new LabelProviderAdapter<Animation>(o -> o.getDesc()));
		frameSeqViewer.setContentProvider(ArrayContentProvider.getInstance());
		
		// bound frameSeqViewer.setInput(ed.frameSeqList);
		// bound frameSeqViewer.addSelectionChangedListener(event -> ed.onFrameSeqChanged(ed.getFirstSelected(event)));
		
		btnAddFrameSeq = new Button(grpKeyframe, SWT.NONE);
		btnAddFrameSeq.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnAddFrameSeq.setToolTipText("Adds a keyframe that triggers playback of a scene");
		// bound btnAddFrameSeq.setText("ColorScene");
		// add switch mode depend on ani scene
		btnAddFrameSeq.addListener(SWT.Selection, e -> dispatchCmd(ADD_KEYFRAME));
		btnAddFrameSeq.setEnabled(false);
		
		Label lblDuration = new Label(grpKeyframe, SWT.NONE);
		lblDuration.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblDuration.setText("Duration:");
		
		txtDuration = new Text(grpKeyframe, SWT.BORDER);
		txtDuration.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		txtDuration.setText("0");
		txtDuration.addListener(SWT.Verify, e -> e.doit = Pattern.matches("^[0-9]+$", e.text));
//		txtDuration.addListener(SWT.Modify, e -> {
//			if (vm.selectedKeyFrame != null) {
//				System.out.println("setting "+txtDuration.getText() +" -> "+vm.selectedKeyFrame.name+" : "+vm.selectedKeyFrame.durationInMillis);
//				vm.selectedKeyFrame.durationInMillis = Integer.parseInt(txtDuration.getText());
//				vm.selectedKeyFrame.durationInFrames = (int) vm.selectedKeyFrame.durationInMillis / 40;
//			}
//		});
		
		return grpKeyframe;
	}
	


	private void createTabbedFolder(Composite parent) {
		
		CTabFolder tabFolder = new CTabFolder(parent, SWT.FLAT);
		tabFolder.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		
		/*GridData gd_tabFolder = new GridData(SWT.LEFT, SWT.CENTER, false, false, 3, 4);
		gd_tabFolder.heightHint = 255;
		gd_tabFolder.widthHint = 504;
		tabFolder.setLayoutData(gd_tabFolder);*/
		
		CTabItem tbtmKeyframe = new CTabItem(tabFolder, SWT.NONE);
		tbtmKeyframe.setText(TabMode.KEYFRAME.label);
		tbtmKeyframe.setControl(createKeyFrameGroup(tabFolder));
		
		fetchDuration = new Button(grpKeyframe, SWT.NONE);
		fetchDuration.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		fetchDuration.setToolTipText("Fetches duration for palette switches by calculating the difference between actual timestamp and keyframe timestamp");
		fetchDuration.setText("Fetch Duration");
		fetchDuration.setEnabled(false);
		fetchDuration.addListener( SWT.Selection, e->dispatchCmd(FETCH_DURATION));

		Label lblEvent = new Label(grpKeyframe, SWT.NONE);
		lblEvent.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblEvent.setText("Event:");
		
		Composite composite_5 = new Composite(grpKeyframe, SWT.NONE);
		GridLayout gl_composite_5 = new GridLayout(3, false);
		gl_composite_5.marginWidth = 0;
		gl_composite_5.marginHeight = 0;
		composite_5.setLayout(gl_composite_5);
		GridData gd_composite_5 = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1);
		gd_composite_5.heightHint = 24;
		gd_composite_5.widthHint = 134;
		composite_5.setLayoutData(gd_composite_5);
		new Label(composite_5, SWT.NONE);
		
		spinnerDeviceId = new Spinner(composite_5, SWT.BORDER);
		spinnerDeviceId.setMaximum(255);
		spinnerDeviceId.setMinimum(0);
		// bound spinnerDeviceId.addModifyListener(e->ed.onEventSpinnerChanged(spinnerDeviceId, 8));
		
		spinnerEventId = new Spinner(composite_5, SWT.BORDER);
		spinnerEventId.setMaximum(255);
		spinnerEventId.setMinimum(0);
		// bound spinnerEventId.addModifyListener(e->ed.onEventSpinnerChanged(spinnerEventId, 0));
		
		btnAddEvent = new Button(grpKeyframe, SWT.NONE);
		btnAddEvent.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnAddEvent.setText("Event");
		btnAddEvent.addListener(SWT.Selection, e->dispatchCmd(ADD_KEYFRAME,SwitchMode.EVENT));
		
		CTabItem tbtmGodmd = new CTabItem(tabFolder, SWT.NONE);
		tbtmGodmd.setText(TabMode.GODMD.label);
		
		goDmdGroup =  new GoDmdGroup(tabFolder);
		tbtmGodmd.setControl( goDmdGroup.getGrpGoDMDCrtls() );

		CTabItem tbtmPropertyText = new CTabItem(tabFolder, SWT.NONE);
		tbtmPropertyText.setText(TabMode.PROP.label);
		
		textProperty = new Text(tabFolder, SWT.BORDER | SWT.V_SCROLL | SWT.MULTI);
		tbtmPropertyText.setControl(textProperty);
		
		tabFolder.setSelection(tbtmKeyframe);
		tabFolder.addListener(SWT.Selection, e->{
			log.debug("tab changed: {}", tabFolder.getSelection().getText());
			tabMode = TabMode.fromLabel(tabFolder.getSelection().getText());
		});
		
	}

	TabMode tabMode;
	enum TabMode {
		KEYFRAME("KeyFrame"), GODMD("goDMD"), PROP("Properties");
		
		public final String label;

		private TabMode(String label) {
			this.label = label;
		}

		public static TabMode fromLabel(String text) {
			for( TabMode i : values()) {
				if( i.label.equals(text)) return i;
			}
			return null;
		}
	}


	AnimationHandler animationHandler;
	
	/**
	 * handles redraw of animations
	 * 
	 * @author steve
	 */
	private class CyclicRedraw implements Runnable {

		@Override
		public void run() {
			// if( !previewCanvas.isDisposed()) previewCanvas.redraw();
			if (dmdWidget != null && !dmdWidget.isDisposed())
				dmdWidget.redraw();
			if (previewDmd != null && !previewDmd.isDisposed())
				previewDmd.redraw();
			if (animationHandler != null && !animationHandler.isStopped()) {
				animationHandler.run();
				display.timerExec(animationHandler.getRefreshDelay(), cyclicRedraw);
			}
		}
	}


	
	// move to view
	@Override
	public void playFullScreen() {
		final Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
		Rectangle b = display.getBounds();
		shell.setBounds(b);
		shell.setFullScreen(true);
		Color black = new Color(display, 0, 0, 0);
		shell.setBackground(black);
		//shell.setLayout(new GridLayout(1, false));
		fullScreenWidget = new DMDWidget(shell, SWT.DOUBLE_BUFFERED, vm.dmd, false);
	    //fullScreenWidget.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, true, true));
		fullScreenWidget.setBackground(black);
	    Rectangle r = shell.getBounds();
	    fullScreenWidget.setBounds(r.x, r.y, r.width, r.height);
	    fullScreenWidget.setPalette(vm.selectedPalette);
	    int w = fullScreenWidget.getPitch()*vm.dmd.getWidth()+fullScreenWidget.getMargin()*2;
	    int h = fullScreenWidget.getPitch()*vm.dmd.getHeight()+fullScreenWidget.getMargin()*2;
	    fullScreenWidget.setBounds((r.width-w)/2,(r.height-h)/2,w,h);
	   
	    display.addFilter(SWT.KeyDown, new Listener() {
			@Override
			public void handleEvent(Event e) {
				if(e.character == 27 ) {
					shell.dispose();
				}
			}
		});
		shell.open();
		
	    // Set up the event loop.
	    while (!shell.isDisposed()  ) {
	      if (!display.readAndDispatch()) {
	        // If no more entries in event queue
	    	  display.sleep();
	      }
	    }
	    fullScreenWidget.dispose();
	    black.dispose();
	    fullScreenWidget = null;
	}


	private void createPreviewComposite(Composite parent) {

		Composite comp = new Composite(parent,0);
		GridData gd_comp = new GridData(SWT.FILL);
		gd_comp.grabExcessHorizontalSpace = true;
		gd_comp.grabExcessVerticalSpace = true;
		gd_comp.verticalAlignment = SWT.FILL;
		gd_comp.horizontalAlignment = SWT.FILL;
		comp.setLayoutData(gd_comp);
		GridLayout gl_comp = new GridLayout(1, false);
		comp.setLayout(gl_comp);
		
		dmdWidget = new DMDWidget(comp, SWT.DOUBLE_BUFFERED, vm.dmd, true);
		//gd_dmdWidget.heightHint = 231;
		//gd_dmdWidget.widthHint = 1600;
		dmdWidget.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		dmdWidget.addListeners(frame -> dispatchCmd(FRAME_CHANGED,frame));
		
		// wire some dependencies to dmdWidget
		paletteTool.addListener(dmdWidget);
		selectTool.setDmdWidget(dmdWidget);

		frame = new Scale(comp, SWT.NONE);
		frame.setMinimum(0);
		frame.setMaximum(1);
		frame.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
	}

	CyclicRedraw cyclicRedraw = new CyclicRedraw();
	private boolean checkDirty;

	public void timerExec(int delay, Runnable r) {
		display.timerExec(delay, r);
	}
	
	ClipboardHandler clipboardHandler;

	@Override
	public void open() {
		Realm.runWithDefault(realm, ()->innerOpen());
	}
	
	Realm realm;
	private ToolItem btnPick;
	private PalettePickerTool palettePickerTool;
	@Override
	public void init(ViewModel vm, BeanFactory beanFactory) {
		display = Display.getDefault();
		realm = SWTObservables.getRealm(display);
		Realm.runWithDefault(realm, () -> innerInit(vm, beanFactory));
	}

	/**
	 * exit hook (also new project): checks on dirty (if not supressed by instance level flag)
	 * and show warning if so.
	 * if not dirty or warning is approved, cmd will be dispatched
	 * @param cmd cmd to dispatch
	 * @return true, if flow should proceed
	 */
	 boolean dirtyCheck(String cmd, String but) {
		boolean proceed = true;
		if( checkDirty && vm.dirty ) {
			int res = messageUtil.warn(0,
					"Warning unsaved changes",
					"Unsaved Changes",
					"There are unsaved changes in project.",
					new String[]{"", "Cancel", but}, 2);
			//System.out.println(res);
			proceed = (res == 2);
		} 
		if( proceed ) dispatchCmd(cmd);
		return proceed;
	}

	private void innerInit(ViewModel vm, BeanFactory beanFactory) {
		
		shell = new Shell();
		EditorViewBinding viewBinding = null;
		
		if( beanFactory != null ) {
			beanFactory.setSingleton("shell", shell);
			config = beanFactory.getBeanByType(Config.class);
			viewBinding = beanFactory.getBeanByType(EditorViewBinding.class);
			viewBinding.setDisplay(display);
		}

		this.vm = vm;
		shell.addListener(SWT.Close, e -> {
			e.doit = vm.dirty && checkDirty;
		});

		if (SWT.getPlatform().equals("cocoa")) {
			CocoaGuiEnhancer enhancer = new CocoaGuiEnhancer("Pin2dmd Editor");
			enhancer.hookApplicationMenu(display, 
					e -> e.doit = dirtyCheck(QUIT, "Quit"),
					new ActionAdapter(() -> dispatchCmd(ABOUT) ),
					new ActionAdapter(() -> dispatchCmd(CONFIGURATION) )
				);
		}

		GlobalExceptionHandler.getInstance().setDisplay(display);
		GlobalExceptionHandler.getInstance().setShell(shell);

		createContents();	

		SplashScreen splashScreen = SplashScreen.getSplashScreen();
		if (splashScreen != null) {
			splashScreen.close();
		}
		if( viewBinding != null ) viewBinding.setEditorView(this);
	}
	
	private void copyLogo(DMD dmd) {
    	BufferedImage master;
		try {
			URL resource = getClass().getResource("/init-dmd-"+dmd.getWidth()+".png");
			master = ImageIO.read(resource);
			Frame f = ImageUtil.convertToFrame(master, dmd.getWidth(), dmd.getHeight());
			dmd.setNumberOfPlanes(15);
			dmd.writeOr(f);
		} catch (Exception e) {
		}
	}


	// hier ist breits alles injected
	private void innerOpen() {

		recentAnimationsMenuManager.loadRecent();
		recentProjectsMenuManager.loadRecent();
		recentPalettesMenuManager.loadRecent();

		// hier muss eine selectedPalette bereits da sein
		vm.setSelectedPalette(Palette.getDefaultPalettes().get(0));
		clipboardHandler = new ClipboardHandler(vm.dmd, dmdWidget, vm.selectedPalette);

		//timerExec(animationHandler.getRefreshDelay(), cyclicRedraw);
		timerExec(1000*300, ()->dispatchCmd(AUTO_SAVE));

		shell.addListener(SWT.Close, e -> {
			e.doit = dirtyCheck(QUIT, "Quit");
		});
		
		copyLogo(vm.dmd);
		
		shell.open();
		shell.layout();

		int retry = 0;
		while (true) {
			try {
				log.info("entering event loop");
				while (!shell.isDisposed()) {
					if (!display.readAndDispatch()) {
						display.sleep();
					}
				}
				dispatchCmd(DELETE_AUTOSAVE_FILES);
				System.exit(0);
			} catch (Exception e) {
				log.error("unexpected error: {}", e);
				GlobalExceptionHandler.getInstance().showError(e);
				if (retry++ > 10) {
					dispatchCmd(DELETE_AUTOSAVE_FILES);
					System.exit(1);
				}
			}
		}
	}

	@Override
	public void createBindings() {
		Realm.runWithDefault(realm, () -> createBindingsInternal());
	}
	
	@Override
	public List<Object> getInjectTargets() {
		return Arrays.asList(paletteTool, palettePickerTool);
	}
	
	// Getter to overide for mocks in tests
	
	public DMDWidget getDmdWidget() {
		return dmdWidget;
	}

	public ClipboardHandler getClipboardHandler() {
		return clipboardHandler;
	}

	public Button[] getBtnHash() {
		return btnHash;
	}

	public Shell getShell() {
		return shell;
	}

	public RecentMenuManager getRecentAnimationsMenuManager() {
		return recentAnimationsMenuManager;
	}
}