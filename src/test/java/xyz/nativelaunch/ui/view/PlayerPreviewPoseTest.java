package xyz.nativelaunch.ui.view;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import xyz.nativelaunch.cosmetic.CosmeticModel;
import xyz.nativelaunch.ui.gfx.Boxes;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPreviewPoseTest {
    private float wristX(PlayerPreview p,CosmeticModel.Attach arm)throws Exception {
        Field boxes=PlayerPreview.class.getDeclaredField("b");boxes.setAccessible(true);
        Boxes b=(Boxes)boxes.get(p);b.begin(0,0,1,0,0);
        Method bone=PlayerPreview.class.getDeclaredMethod("boneTransform",CosmeticModel.Attach.class);bone.setAccessible(true);bone.invoke(p,arm);
        Field matrix=Boxes.class.getDeclaredField("m");matrix.setAccessible(true);float[] m=(float[])matrix.get(b);
        return m[1]*10+m[9];
    }
    @Test void wardrobeArmsOpenSymmetricallyWithoutChangingHitboxStandingPose()throws Exception {
        PlayerPreview wardrobe=new PlayerPreview(),hitbox=new PlayerPreview(true);
        float right=wristX(wardrobe,CosmeticModel.Attach.RIGHT_ARM),left=wristX(wardrobe,CosmeticModel.Attach.LEFT_ARM);
        assertEquals(-right,left,0.001f);assertTrue(left>6.5f,"wardrobe arms too close to body");
        assertTrue(left>wristX(hitbox,CosmeticModel.Attach.LEFT_ARM)+1,"standing debug pose should stay separate");
    }
}
