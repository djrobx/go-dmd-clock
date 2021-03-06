package com.rinke.solutions.pinball.animation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.Test;

import com.rinke.solutions.pinball.DMD;
import com.rinke.solutions.pinball.DmdSize;
import com.rinke.solutions.pinball.PinDmdEditor;
import com.rinke.solutions.pinball.model.Frame;
import com.rinke.solutions.pinball.model.Plane;


public class AnimationTest {

	@Test
	public void testCutScene() throws Exception {
		Animation src = new Animation(AnimationType.MAME,
				"./src/test/resources/drwho-dump.txt.gz", 0, 100, 0, 0, 0);
		Animation cutScene = src.cutScene(10, 20, 4);
		assertThat(cutScene, notNullValue());
		assertThat(cutScene.end - cutScene.start, equalTo(10));

		src.actFrame = 10;
		Frame srcFrame = src.render(new DMD(DmdSize.Size128x32), false);
		cutScene.actFrame = 0;
		Frame destFrame = cutScene.render(new DMD(DmdSize.Size128x32), false);
		assertThat(srcFrame.planes.size(), equalTo(2));
		assertThat(destFrame.planes.size(), equalTo(4));
		int i = 0;
		for (Plane p : srcFrame.planes) {
			assertThat(p.data, equalTo(destFrame.planes.get(i++).data));
		}

	}

}
