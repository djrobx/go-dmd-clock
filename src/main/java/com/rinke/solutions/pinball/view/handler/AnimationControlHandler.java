package com.rinke.solutions.pinball.view.handler;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

import com.rinke.solutions.beans.Autowired;
import com.rinke.solutions.beans.Bean;
import com.rinke.solutions.pinball.AnimationHandler;
import com.rinke.solutions.pinball.animation.Animation;
import com.rinke.solutions.pinball.animation.CompiledAnimation;
import com.rinke.solutions.pinball.view.model.ViewModel;

@Bean
@Slf4j
public class AnimationControlHandler extends AbstractCommandHandler implements ViewBindingHandler {

	@Autowired AnimationHandler animationHandler;
	@Autowired MaskHandler maskHandler;
	
	public AnimationControlHandler(ViewModel vm) {
		super(vm);
	}
	
	// could depend on order, MAYBE the vm does not reflect the latest state !??
	public void onSelectedRecordingChanged(Animation o, Animation n) {
		if( n != null) {
			update(vm.minFrame, vm.selectedFrame, vm.maxFrame, vm.animationIsPlaying);
		}
	}

	public void onSelectedSceneChanged(Animation o, Animation n) {
		if( n != null) {
			update(vm.minFrame, vm.selectedFrame, vm.maxFrame, vm.animationIsPlaying);
		}
	}
	
	public void onSelectedFrameChanged(int o, int n) {
		animationHandler.setPos(n, maskHandler.getCurrentMask());
		//if( vm.detectionMaskActive || vm.layerMaskActive ) maskHandler.onMaskActiveChanged(false,true );
		update(vm.minFrame, n, vm.maxFrame, vm.animationIsPlaying);
	}

	private void update(int minFrame, int actFrame, int maxFrame, boolean isPlaying) {
		if( isPlaying ) {
			vm.setBtnPrevEnabled(false);
			vm.setBtnNextEnabled(false);
		} else {
			vm.setBtnPrevEnabled(minFrame<actFrame);
			vm.setBtnNextEnabled(actFrame<maxFrame);
		}
	}

	public void onAnimationIsPlayingChanged(boolean o, boolean n) {
		update(vm.minFrame, vm.selectedFrame, vm.maxFrame, n);
	}
	
	public void onStartStop(boolean isPlaying) {
		if( isPlaying ) {
			log.info("Stopping animation playback");
			animationHandler.stop();
			vm.setAnimationIsPlaying(false);
		} else {
			log.info("Starting animation playback");
			if( vm.selectedScene != null ){
				vm.selectedScene.commitDMDchanges(vm.dmd, saveGetHash(vm.selectedHashIndex)); 
				vm.setDirty(vm.dirty|vm.selectedScene.isDirty());
			}
			animationHandler.start();
			//vm.setNextTimerExec(animationHandler.getRefreshDelay());
			
			vm.setSelection(null);
			vm.setAnimationIsPlaying(true);
		}
	}

	public void onPrevFrame() {
		if(vm.selectedScene!=null) {
			vm.selectedScene.commitDMDchanges(vm.dmd, saveGetHash(vm.selectedHashIndex)); 
			vm.setDirty(vm.dirty|vm.selectedScene.isDirty());
		}
		maskHandler.commitMaskIfNeeded();
		vm.setSelectedFrame(vm.selectedFrame-vm.frameIncrement);
		
		if(  (vm.selectedEditMode.useLocalMask || vm.selectedEditMode.useGlobalMask ) && vm.selectedScene!=null) {
			selectHash(vm.selectedScene);
		}
		vm.setSelection(null);
		//if( vm.selectedScene != null ) System.out.println(vm.selectedScene.actFrame);
	}
	
	byte[] saveGetHash(int idx) {
		return idx>=0 && idx<vm.hashes.size() ? vm.hashes.get(idx) : null;
	}
	
	public void onNextFrame() {
		if( vm.selectedScene != null){
			vm.selectedScene.commitDMDchanges(vm.dmd,saveGetHash(vm.selectedHashIndex)); 
			vm.setDirty(vm.dirty|vm.selectedScene.isDirty());
		}
		maskHandler.commitMaskIfNeeded();
		vm.setSelectedFrame(vm.selectedFrame+vm.frameIncrement);

		if( (vm.selectedEditMode.useLocalMask || vm.selectedEditMode.useGlobalMask ) && vm.selectedScene!=null) {
			selectHash(vm.selectedScene);
		}
		vm.setSelection(null);
		//if( vm.selectedScene != null ) System.out.println(vm.selectedScene.actFrame);
	}
	
	private void selectHash(CompiledAnimation ani) {
		byte[] crc32 = ani.frames.get(ani.actFrame).crc32;
		for( int i =0; i < vm.hashes.size(); i++ ) {
			if( Arrays.equals(vm.hashes.get(i), crc32) ) {
				vm.setSelectedHashIndex(i);
			}
		}
	}

	public void onCopyAndMoveToNextFrame() {
		onNextFrame();
		CompiledAnimation ani = vm.selectedScene;
		if( !ani.hasEnded() ) {
			ani.frames.get(ani.actFrame-1).copyToWithMask(vm.dmd.getFrame(), vm.dmd.getDrawMask());
			vm.setDmdDirty(true);
		}
	}
	
	public void onCopyAndMoveToPrevFrame() {
		onPrevFrame();
		CompiledAnimation ani = vm.selectedScene;
		if( ani.getActFrame() >= ani.getStart() ) {
			ani.frames.get(ani.actFrame+1).copyToWithMask(vm.dmd.getFrame(), vm.dmd.getDrawMask());
			vm.setDmdDirty(true);
		}
	}

	public void onUpdateDelay() {
		CompiledAnimation ani = vm.selectedScene;
		if( ani != null && vm.selectedFrame < ani.frames.size() ) {
			log.debug("Setting delay of frame {} to {}", vm.selectedFrame, vm.delay);
			ani.frames.get(vm.selectedFrame).delay = vm.delay;
		}
	}

	public void setAnimationHandler(AnimationHandler animationHandler) {
		this.animationHandler = animationHandler;
	}
	
}
